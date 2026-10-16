package com.mdevz.xelvra.core.vpn

import com.mdevz.xelvra.core.xray.config.SmartRoutingRuleCodec


import com.mdevz.xelvra.core.xray.XrayAssetManager
import com.mdevz.xelvra.core.log.AppLog
import com.mdevz.xelvra.core.traffic.TrafficMonitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.ParcelFileDescriptor
import com.mdevz.xelvra.data.model.Protocol
import java.util.Locale
import android.os.Looper
import android.os.Handler
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.mdevz.xelvra.MainActivity
import com.mdevz.xelvra.R
import com.mdevz.xelvra.core.xray.XrayRuntimeController
import com.mdevz.xelvra.core.xray.DelayMonitor
import com.mdevz.xelvra.core.xray.config.XrayConfigBuilder
import com.mdevz.xelvra.data.local.ActiveProfileStore
import com.mdevz.xelvra.data.local.ProfileLocalStorage
import com.mdevz.xelvra.data.local.PerAppVpnMode
import com.mdevz.xelvra.data.local.SettingsStore
import com.mdevz.xelvra.data.local.BypassLanMode
import com.mdevz.xelvra.data.repository.ProfileRepository

class XelvraVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    private val lifecycleLock =
        Any()

    private val resourceLock =
        Any()

    private var cleanupScheduled = false

    private var startupFinished =
        java.util.concurrent.CountDownLatch(0)

    private var worker: Thread? = null
    private var stopWorker: Thread? = null

    @Volatile
    private var recoveryWorker: Thread? = null

    @Volatile
    private var activeXrayConfig: String? = null

    private var recoveryAttempts = 0

    private var xrayHealthySinceElapsed = 0L

    @Volatile
    private var stopping = false

    private val notificationHandler =
        Handler(Looper.getMainLooper())

    private var notificationProfileName =
        ""

    private var notificationProtocol =
        ""

    private var notificationConnectedAt =
        0L

    @Volatile
    private var networkCallbackRegistered =
        false

    @Volatile
    private var networkAvailable =
        false

    private val physicalNetworkLock = Any()

    private val physicalNetworks =
        mutableMapOf<Network, String>()

    @Volatile
    private var physicalNetworkCallbackRegistered = false

    private val physicalNetworkCallback =
        object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {
                val added =
                    synchronized(physicalNetworkLock) {
                        if (
                            !physicalNetworkCallbackRegistered ||
                            stopping
                        ) {
                            false
                        } else if (
                            physicalNetworks.containsKey(network)
                        ) {
                            false
                        } else {
                            physicalNetworks[network] = "UNKNOWN"
                            publishPhysicalNetworkSnapshotLocked()
                            true
                        }
                    }

                if (added) {
                    AppLog.vpn("Physical network available")
                }
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                val transport = when {
                    capabilities.hasTransport(
                        NetworkCapabilities.TRANSPORT_WIFI
                    ) -> "WIFI"

                    capabilities.hasTransport(
                        NetworkCapabilities.TRANSPORT_CELLULAR
                    ) -> "CELLULAR"

                    else -> "OTHER"
                }

                val changed =
                    synchronized(physicalNetworkLock) {
                        if (
                            !physicalNetworkCallbackRegistered ||
                            stopping
                        ) {
                            false
                        } else {
                            val previous = physicalNetworks[network]
                            physicalNetworks[network] = transport
                            publishPhysicalNetworkSnapshotLocked()
                            previous != transport
                        }
                    }

                if (changed) {
                    AppLog.vpn(
                        "Physical network transport: $transport"
                    )
                }
            }

            override fun onLost(network: Network) {
                val removed =
                    synchronized(physicalNetworkLock) {
                        if (
                            !physicalNetworkCallbackRegistered ||
                            stopping
                        ) {
                            false
                        } else {
                            physicalNetworks.remove(network).also {
                                publishPhysicalNetworkSnapshotLocked()
                            } != null
                        }
                    }

                if (removed) {
                    AppLog.vpn("Physical network lost")
                }
            }
        }

    private fun publishPhysicalNetworkSnapshotLocked() {
        physicalNetworkSnapshot = when {
            !physicalNetworkCallbackRegistered || stopping ->
                "UNAVAILABLE"
            physicalNetworks.values.any { it == "WIFI" } ->
                "WIFI"
            physicalNetworks.values.any { it == "CELLULAR" } ->
                "CELLULAR"
            physicalNetworks.values.any { it == "OTHER" } ->
                "OTHER"
            physicalNetworks.isNotEmpty() ->
                "UNKNOWN"
            else ->
                "UNAVAILABLE"
        }
    }

    private val networkIdentityLock = Any()

    private var lastObservedDefaultNetwork: Network? = null

    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(
                network: Network
            ) {
                observeDefaultNetwork(network)
                refreshNetworkState()
            }

            override fun onLost(
                network: Network
            ) {
                synchronized(networkIdentityLock) {
                    if (lastObservedDefaultNetwork == network) {
                        lastObservedDefaultNetwork = null
                    }
                }
                refreshNetworkState()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                observeDefaultNetwork(network)
                refreshNetworkState()
            }
        }

    private val xrayHealthChecker =
        object : Runnable {
            override fun run() {
                if (stopping) {
                    return
                }

                when (
                    VpnRuntimeManager.getState()
                ) {
                    is VpnRuntimeState.Connected,
                    VpnRuntimeState.WaitingForNetwork -> {
                        val healthy =
                            synchronized(resourceLock) {
                                vpnInterface != null &&
                                    XrayRuntimeController.isRunning()
                            }

                        if (!healthy) {
                            synchronized(lifecycleLock) {
                                xrayHealthySinceElapsed = 0L
                            }
                            handleUnexpectedXrayStop()
                            return
                        }

                        val now =
                            android.os.SystemClock.elapsedRealtime()

                        synchronized(lifecycleLock) {
                            val activeState =
                                when (VpnRuntimeManager.getState()) {
                                    is VpnRuntimeState.Connected,
                                    VpnRuntimeState.WaitingForNetwork -> true
                                    else -> false
                                }

                            if (
                                !stopping &&
                                activeState &&
                                recoveryWorker?.isAlive != true
                            ) {
                                if (xrayHealthySinceElapsed == 0L) {
                                    xrayHealthySinceElapsed = now
                                } else if (
                                    recoveryAttempts > 0 &&
                                    now - xrayHealthySinceElapsed >=
                                        XRAY_RECOVERY_STABLE_MS
                                ) {
                                    recoveryAttempts = 0
                                    AppLog.xray(
                                        "Xray stable; recovery budget reset"
                                    )
                                }
                            }
                        }

                        if (!stopping) {
                            notificationHandler.postDelayed(
                                this,
                                XRAY_HEALTH_CHECK_INTERVAL_MS
                            )
                        }
                    }

                    else -> Unit
                }
            }
        }

    private val notificationUpdater =
        object : Runnable {
            override fun run() {
                when (
                    VpnRuntimeManager.getState()
                ) {
                    is VpnRuntimeState.Connected,
                    VpnRuntimeState.WaitingForNetwork -> {
                        updateForegroundNotification()

                        notificationHandler.postDelayed(
                            this,
                            1000L
                        )
                    }

                    else -> Unit
                }
            }
        }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        registerNetworkCallback()
        registerPhysicalNetworkCallback()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {
            ACTION_STOP -> stopVpn()

            ACTION_START -> {
                startForegroundNow()

                synchronized(lifecycleLock) {
                    if (
                        stopping ||
                        stopWorker?.isAlive == true
                    ) {
                        AppLog.vpn(
                            "Start ignored while VPN is stopping"
                        )
                    } else if (
                        worker?.isAlive == true ||
                        when (
                            VpnRuntimeManager.getState()
                        ) {
                            VpnRuntimeState.Connecting,
                            VpnRuntimeState.Reconnecting,
                            is VpnRuntimeState.Connected,
                            VpnRuntimeState.WaitingForNetwork ->
                                true

                            else ->
                                false
                        }
                    ) {
                        AppLog.vpn(
                            "Duplicate VPN start ignored"
                        )
                    } else {
                        stopping = false
                        recoveryAttempts = 0
                        xrayHealthySinceElapsed = 0L

                        val currentStartupLatch =
                            java.util.concurrent.CountDownLatch(1)

                        startupFinished = currentStartupLatch

                        worker =
                            Thread(
                                {
                                    startVpn(currentStartupLatch)
                                },
                                "XelvraVpnStart"
                            ).apply {
                                start()
                            }
                    }
                }
            }

            else -> {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startVpn(
        currentStartupLatch: java.util.concurrent.CountDownLatch
    ) {
        synchronized(lifecycleLock) {
            if (stopping) {
                currentStartupLatch.countDown()
                return
            }

            VpnRuntimeManager.updateState(
                VpnRuntimeState.Connecting
            )
        }

        try {
            AppLog.vpn("Starting VPN")

            val activeProfileId =
                ActiveProfileStore(this)
                    .getActiveProfileId()
                    ?: error(
                        "No active profile selected"
                    )

            val repository = ProfileRepository(
                ProfileLocalStorage(this)
            )

            val profile =
                repository.findProfile(
                    activeProfileId
                )
                    ?: error(
                        "Active profile was not found"
                    )

            if (stopping) {
                return
            }

            VpnRuntimeManager.setSessionProfileId(
                profile.id
            )

            notificationProfileName =
                profile.remarks
                    .trim()
                    .ifBlank {
                        getString(R.string.app_name)
                    }

            notificationProtocol =
                protocolLabel(
                    profile.protocol
                )

            updateForegroundNotification()

            AppLog.vpn(
                "Active protocol: ${profile.protocol}"
            )

            val settingsStore =
                SettingsStore(this)

            val mtu =
                settingsStore.getMtu()

            val vpnInterfaceAddress =
                settingsStore.getVpnInterfaceAddress()

            val ipv6Enabled =
                settingsStore.isIpv6Enabled()

            val bypassLanMode =
                settingsStore.getBypassLanMode()

            val remoteDnsEnabled =
                settingsStore.isRemoteDnsEnabled()

            val remoteDns =
                if (remoteDnsEnabled) {
                    settingsStore.getRemoteDns()
                } else {
                    SettingsStore.DEFAULT_REMOTE_DNS
                }

            val directDns =
                settingsStore.getDirectDns()

            val domainStrategy =
                settingsStore.getDomainStrategy()
            val routingMode =
                settingsStore.getRoutingMode()

            val effectiveBypassLan =
                settingsStore.resolveBypassLan(
                    routingMode
                )

            val muxEnabled =
                settingsStore.isMuxEnabled()


            val muxConcurrency =
                settingsStore.getMuxConcurrency()

            val muxXudpConcurrency =
                settingsStore.getMuxXudpConcurrency()

            val muxUdp443Mode =
                settingsStore.getMuxUdp443Mode()

            val fragmentEnabled =
                settingsStore.isFragmentEnabled()

            val fragmentPackets =
                settingsStore.getFragmentPackets()

            val fragmentLength =
                settingsStore.getFragmentLength()

            val fragmentInterval =
                settingsStore.getFragmentInterval()

            val fragmentMaxSplit =
                settingsStore.getFragmentMaxSplit()

            val sniffingEnabled =
                settingsStore.isSniffingEnabled()

            val sniffingRouteOnly =
                settingsStore.isSniffingRouteOnly()

            val sniffingHttpEnabled =
                settingsStore.isSniffingHttpEnabled()

            val sniffingTlsEnabled =
                settingsStore.isSniffingTlsEnabled()

            val sniffingQuicEnabled =
                settingsStore.isSniffingQuicEnabled()

            val fakeDnsEnabled =
                settingsStore.isFakeDnsEnabled()

            val perAppVpnMode =
                settingsStore.getPerAppVpnMode()

            val perAppVpnPackages =
                settingsStore
                    .getPerAppVpnPackages()

            val testUrlEnabled =
                settingsStore.isTestUrlEnabled()

            val testUrl =
                settingsStore.getTestUrl()

            val connectionTimeoutSeconds =
                settingsStore
                    .getConnectionTimeoutSeconds()

            val testUrlIntervalSeconds =
                settingsStore
                    .getTestUrlIntervalSeconds()

            val autoConnectEnabled =
                settingsStore.isAutoConnectEnabled()

            val geositeAsset =
                XrayAssetManager.getAssetInfo(
                    this,
                    "geosite.dat"
                )

            val geoipAsset =
                XrayAssetManager.getAssetInfo(
                    this,
                    "geoip.dat"
                )

            AppLog.vpn("MTU: $mtu")

            AppLog.vpn(
                "VPN Interface Address: $vpnInterfaceAddress"
            )
            if (ipv6Enabled) {
                AppLog.vpn(
                    "IPv6: enabled"
                )
            }

            if (remoteDnsEnabled) {
                AppLog.vpn(
                    "Remote DNS: enabled"
                )
            }

            AppLog.vpn(
                "Direct DNS configured"
            )

            AppLog.vpn(
                "Domain Strategy: " +
                    domainStrategy.xrayValue
            )

            AppLog.vpn(
                "Routing Mode: ${routingMode.displayName}"
            )
            if (muxEnabled) {
                AppLog.vpn(
                    "Mux: enabled"
                )
                AppLog.vpn(
                    "Mux concurrency: $muxConcurrency"
                )
                AppLog.vpn(
                    "Mux XUDP concurrency: $muxXudpConcurrency"
                )
                AppLog.vpn(
                    "Mux UDP 443: $muxUdp443Mode"
                )
            }

            if (fragmentEnabled) {
                AppLog.vpn(
                    "Fragment: enabled"
                )
                AppLog.vpn(
                    "Fragment packets: $fragmentPackets"
                )
                AppLog.vpn(
                    "Fragment length: $fragmentLength"
                )
                AppLog.vpn(
                    "Fragment interval: $fragmentInterval"
                )

                AppLog.vpn(
                    "Fragment max split: $fragmentMaxSplit"
                )
            }

            if (sniffingEnabled) {
                AppLog.vpn(
                    "Sniffing: enabled"
                )

                AppLog.vpn(
                    "Sniffing routeOnly: " +
                        sniffingRouteOnly
                )

                val sniffingOverrides =
                    buildList {
                        if (sniffingHttpEnabled) {
                            add("http")
                        }

                        if (sniffingTlsEnabled) {
                            add("tls")
                        }

                        if (sniffingQuicEnabled) {
                            add("quic")
                        }
                    }

                AppLog.vpn(
                    "Sniffing overrides: " +
                        if (sniffingOverrides.isEmpty()) {
                            "none"
                        } else {
                            sniffingOverrides.joinToString(",")
                        }
                )
            }

            if (fakeDnsEnabled) {
                AppLog.vpn(
                    "FakeDNS: enabled"
                )
            }

            AppLog.vpn(
                "Bypass LAN: " +
                    when (bypassLanMode) {
                        BypassLanMode.FOLLOW_CONFIG ->
                            "follow config"

                        BypassLanMode.BYPASS ->
                            "bypass"

                        BypassLanMode.NOT_BYPASS ->
                            "not bypass"
                    } +
                    " (effective: " +
                    if (effectiveBypassLan) {
                        "bypass)"
                    } else {
                        "not bypass)"
                    }
            )

            AppLog.vpn(
                "Per-App VPN: " +
                    when (perAppVpnMode) {
                        PerAppVpnMode.ALL_APPS ->
                            "All Apps"

                        PerAppVpnMode.SELECTED_APPS ->
                            "Selected Apps Only " +
                                "(${perAppVpnPackages.size})"

                        PerAppVpnMode.BYPASS_SELECTED ->
                            "Bypass Selected Apps " +
                                "(${perAppVpnPackages.size})"
                    }
            )

            if (testUrlEnabled) {
                AppLog.vpn(
                    "Test URL interval: " +
                        "$testUrlIntervalSeconds seconds"
                )

                AppLog.vpn(
                    "Delay timeout: " +
                        "$connectionTimeoutSeconds seconds"
                )
            }

            if (autoConnectEnabled) {
                AppLog.vpn(
                    "Auto Connect: enabled"
                )
            }

            AppLog.vpn(
                "Asset Files: " +
                    "geosite.dat=${geositeAsset.source}, " +
                    "geoip.dat=${geoipAsset.source}"
            )

            // Apply all saved Smart Routing actions on VPN start.
            // Rule priority is handled by XrayConfigBuilder.
            val smartRoutingRules =
                SmartRoutingRuleCodec.readAsXrayRules(this)

            android.util.Log.i(
                "XelvraSmartRouting",
                "Rules loaded: ${smartRoutingRules.length()}"
            )

            val config =
                XrayConfigBuilder.build(
                    profile = profile,
                    inboundMode =
                        XrayConfigBuilder
                            .InboundMode.TUN,
                    mtu = mtu,
                    remoteDns = remoteDns,
                    directDns = directDns,
                    muxEnabled =
                        muxEnabled,
                    muxConcurrency =
                        muxConcurrency,
                    muxXudpConcurrency =
                        muxXudpConcurrency,
                    muxUdp443Mode =
                        muxUdp443Mode,
                    fragmentEnabled =
                        fragmentEnabled,
                    fragmentPackets =
                        fragmentPackets,
                    fragmentLength =
                        fragmentLength,
                    fragmentInterval =
                        fragmentInterval,
                    fragmentMaxSplit =
                        fragmentMaxSplit,
                    sniffingEnabled =
                        sniffingEnabled,
                    sniffingRouteOnly =
                        sniffingRouteOnly,
                    sniffingHttpEnabled =
                        sniffingHttpEnabled,
                    sniffingTlsEnabled =
                        sniffingTlsEnabled,
                    sniffingQuicEnabled =
                        sniffingQuicEnabled,
                    fakeDnsEnabled =
                        fakeDnsEnabled,
                    domainStrategy =
                        domainStrategy.xrayValue,
                    routingMode = routingMode,
                    smartRoutingRules = smartRoutingRules
                )

            XrayRuntimeController.initialize(this)

            activeXrayConfig = config

            val vpnBuilder =
                Builder()
                    .setSession("Xelvra")
                    .setMtu(mtu)
                    .addAddress(
                        vpnInterfaceAddress,
                        VPN_PREFIX_LENGTH
                    )
                    .apply {
                        if (effectiveBypassLan) {
                            addBypassLanIpv4Routes(this)
                        } else {
                            addRoute(
                                "0.0.0.0",
                                0
                            )
                        }
                    }
                    .addDnsServer(
                        DNS_SERVER
                    )

            if (ipv6Enabled) {
                vpnBuilder
                    .addAddress(
                        VPN_IPV6_ADDRESS,
                        VPN_IPV6_PREFIX_LENGTH
                    )

                if (effectiveBypassLan) {
                    addBypassLanIpv6Routes(
                        vpnBuilder
                    )
                } else {
                    vpnBuilder.addRoute(
                        "::",
                        0
                    )
                }
            }

            applyPerAppVpnPolicy(
                builder = vpnBuilder,
                mode = perAppVpnMode,
                selectedPackages =
                    perAppVpnPackages
            )

            val established =
                vpnBuilder
                    .establish()
                    ?: error(
                        "Android failed to establish VPN interface"
                    )

            synchronized(resourceLock) {
                if (stopping) {
                    established.close()
                    return
                }

                vpnInterface = established

                AppLog.vpn(
                    "TUN established fd=${established.fd}"
                )

                if (stopping) {
                    cleanupRuntime()
                    return
                }

                XrayRuntimeController.start(
                    config = config,
                    tunFd = established.fd
                )

                if (stopping) {
                    cleanupRuntime()
                    return
                }

                TrafficMonitor.start()
            }

            val connectedAt =
                System.currentTimeMillis()

            notificationConnectedAt =
                connectedAt

            if (stopping) {
                cleanupRuntime()
                return
            }

            val usableNetwork = hasUsableNetwork()

            synchronized(lifecycleLock) {
                if (stopping) {
                    return
                }

                networkAvailable = usableNetwork

                if (usableNetwork) {
                    VpnRuntimeManager.updateState(
                        VpnRuntimeState.Connected(
                            startedAt = connectedAt
                        )
                    )
                    AppLog.vpn("VPN connected")
                } else {
                    VpnRuntimeManager.updateState(
                        VpnRuntimeState.WaitingForNetwork
                    )
                    AppLog.vpn("VPN waiting for network")
                }
            }

            synchronized(lifecycleLock) {
                if (stopping) {
                    return
                }

                if (
                    testUrlEnabled &&
                    networkAvailable
                ) {
                    DelayMonitor.start(
                        url = testUrl,
                        intervalSeconds =
                            testUrlIntervalSeconds,
                        timeoutSeconds =
                            connectionTimeoutSeconds
                    )
                }

                notificationHandler.removeCallbacks(
                    notificationUpdater
                )

                updateForegroundNotification()

                notificationHandler.postDelayed(
                    notificationUpdater,
                    1000L
                )

                notificationHandler.removeCallbacks(
                    xrayHealthChecker
                )

                notificationHandler.postDelayed(
                    xrayHealthChecker,
                    XRAY_HEALTH_CHECK_INTERVAL_MS
                )
            }

        } catch (error: Throwable) {
            AppLog.vpn(
                message =
                    "VPN startup failed (" +
                        error.javaClass.simpleName +
                        ")",
                level = AppLog.Level.ERROR
            )

            val startupOwnsCleanup =
                synchronized(lifecycleLock) {
                    if (stopping || cleanupScheduled) {
                        false
                    } else {
                        stopping = true
                        cleanupScheduled = true
                        true
                    }
                }

            if (!startupOwnsCleanup) {
                return
            }

            cleanupRuntime()

            VpnRuntimeManager.clearSessionProfileId()

            VpnRuntimeManager.updateState(
                VpnRuntimeState.Failed(
                    "VPN startup failed"
                )
            )

            stopForegroundCompat()
            stopSelf()
        } finally {
            currentStartupLatch.countDown()
        }
    }

    private fun stopVpn() {
        synchronized(lifecycleLock) {
            if (
                stopping ||
                stopWorker?.isAlive == true
            ) {
                return
            }

            stopping = true
            recoveryWorker?.interrupt()

            unregisterNetworkCallback()
            unregisterPhysicalNetworkCallback()

            notificationHandler.removeCallbacks(
                notificationUpdater
            )

            notificationHandler.removeCallbacks(
                xrayHealthChecker
            )

            VpnRuntimeManager.updateState(
                VpnRuntimeState.Disconnecting
            )

            updateForegroundNotification()

            cleanupScheduled = true

            val latchToWait = startupFinished

            stopWorker =
                Thread(
                    {
                        awaitStartupCompletion(latchToWait)
                        cleanupRuntime()

                        VpnRuntimeManager
                            .clearSessionProfileId()

                        VpnRuntimeManager.updateState(
                            VpnRuntimeState.Disconnected
                        )

                        stopForegroundCompat()

                        synchronized(
                            lifecycleLock
                        ) {
                            worker = null
                        }

                        stopSelf()
                    },
                    "XelvraVpnStop"
                ).apply {
                    start()
                }
        }
    }

    private fun awaitStartupCompletion(
        latch: java.util.concurrent.CountDownLatch
    ) {
        var interrupted = false

        while (true) {
            try {
                latch.await()
                break
            } catch (error: InterruptedException) {
                interrupted = true
                AppLog.vpn(
                    "Cleanup wait interrupted; continuing to wait"
                )
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt()
        }
    }

    private fun cleanupRuntime() {
        synchronized(resourceLock) {
            cleanupRuntimeLocked()
        }
    }

    private fun cleanupRuntimeLocked() {
        DelayMonitor.stop()
        TrafficMonitor.stop()
        activeXrayConfig = null

        try {
            XrayRuntimeController.stop()
        } catch (error: Throwable) {
            AppLog.xray(
                message =
                    "Failed to stop Xray core (" +
                        error.javaClass.simpleName +
                        ")",
                level = AppLog.Level.WARNING
            )
        }

        try {
            vpnInterface?.close()
        } catch (error: Throwable) {
            AppLog.vpn(
                message =
                    "Failed to close TUN (" +
                        error.javaClass.simpleName +
                        ")",
                level = AppLog.Level.WARNING
            )
        } finally {
            vpnInterface = null
        }
    }

    private fun handleUnexpectedXrayStop() {
        synchronized(lifecycleLock) {
            if (
                stopping ||
                recoveryWorker?.isAlive == true ||
                stopWorker?.isAlive == true
            ) {
                return
            }

            when (VpnRuntimeManager.getState()) {
                is VpnRuntimeState.Connected,
                VpnRuntimeState.WaitingForNetwork -> Unit
                else -> return
            }

            if (!SettingsStore(this).isAutoReconnectEnabled()) {
                handleUnexpectedXrayStopFinal()
                return
            }

            if (recoveryAttempts >= 3) {
                handleUnexpectedXrayStopFinal()
                return
            }

            xrayHealthySinceElapsed = 0L

            VpnRuntimeManager.updateState(
                VpnRuntimeState.Reconnecting
            )

            DelayMonitor.stop()

            updateForegroundNotification()

            notificationHandler.removeCallbacks(
                xrayHealthChecker
            )

            notificationHandler.removeCallbacks(
                notificationUpdater
            )

            recoveryWorker = Thread(
                {
                    recoverXray()
                },
                "XelvraXrayRecovery"
            ).apply {
                start()
            }
        }
    }

    private fun recoverXray() {
        try {
            recoverXrayInternal()
        } finally {
            synchronized(lifecycleLock) {
                recoveryWorker = null

                if (
                    !stopping &&
                    (
                        VpnRuntimeManager.getState()
                            is VpnRuntimeState.Connected ||
                        VpnRuntimeManager.getState() ==
                            VpnRuntimeState.WaitingForNetwork
                    )
                ) {
                    notificationHandler.removeCallbacks(
                        xrayHealthChecker
                    )

                    notificationHandler.postDelayed(
                        xrayHealthChecker,
                        XRAY_HEALTH_CHECK_INTERVAL_MS
                    )
                }
            }
        }
    }

    private fun recoverXrayInternal() {
        val delays = longArrayOf(2000L, 4000L, 8000L)

        for ((index, delayMs) in delays.withIndex()) {
            val budgetExhaustedBeforeDelay =
                synchronized(lifecycleLock) {
                    if (stopping) return
                    recoveryAttempts >= 3
                }

            if (budgetExhaustedBeforeDelay) break

            try {
                Thread.sleep(delayMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }

            val budgetExhaustedBeforeAttempt =
                synchronized(lifecycleLock) {
                    if (stopping) return

                    if (recoveryAttempts >= 3) {
                        true
                    } else {
                        recoveryAttempts++
                        false
                    }
                }

            if (budgetExhaustedBeforeAttempt) break

            var recovered = false

            try {
                synchronized(resourceLock) {
                    if (!stopping) {
                        val tun = vpnInterface
                        val config = activeXrayConfig

                        if (tun != null && config != null) {
                            XrayRuntimeController.stop()

                            if (!stopping) {
                                XrayRuntimeController.start(
                                    config = config,
                                    tunFd = tun.fd
                                )

                                recovered =
                                    XrayRuntimeController.isRunning()
                            }
                        }
                    }
                }
            } catch (error: Throwable) {
                AppLog.xray(
                    message =
                        "Recovery attempt ${index + 1} failed (" +
                            error.javaClass.simpleName + ")",
                    level = AppLog.Level.WARNING
                )
            }

            if (stopping) return

            if (recovered) {
                try {
                    synchronized(lifecycleLock) {
                        if (stopping) return

                        TrafficMonitor.start()

                    val available = hasUsableNetwork()
                    networkAvailable = available

                    if (available) {
                        val settingsStore =
                            SettingsStore(this@XelvraVpnService)

                        if (settingsStore.isTestUrlEnabled()) {
                            DelayMonitor.start(
                                url = settingsStore.getTestUrl(),
                                intervalSeconds =
                                    settingsStore.getTestUrlIntervalSeconds(),
                                timeoutSeconds =
                                    settingsStore.getConnectionTimeoutSeconds()
                            )
                        }
                    }

                    val startedAt =
                        System.currentTimeMillis()

                    notificationConnectedAt = startedAt

                    VpnRuntimeManager.updateState(
                        if (available) {
                            VpnRuntimeState.Connected(startedAt)
                        } else {
                            VpnRuntimeState.WaitingForNetwork
                        }
                    )

                    updateForegroundNotification()

                    notificationHandler.postDelayed(
                        notificationUpdater,
                        1000L
                    )
                }

                    AppLog.xray("Xray recovery successful")
                    return
                } catch (error: Throwable) {
                    AppLog.xray(
                        message =
                            "Recovery initialization failed (" +
                                error.javaClass.simpleName + ")",
                        level = AppLog.Level.ERROR
                    )

                    if (!stopping) {
                        handleUnexpectedXrayStopFinal()
                    }
                    return
                }
            }
        }

        if (stopping) return

        handleUnexpectedXrayStopFinal()
    }

    private fun handleUnexpectedXrayStopFinal() {
        synchronized(lifecycleLock) {
            if (
                stopping ||
                stopWorker?.isAlive == true
            ) {
                return
            }

            when (
                VpnRuntimeManager.getState()
            ) {
                is VpnRuntimeState.Connected,
                VpnRuntimeState.WaitingForNetwork,
                VpnRuntimeState.Reconnecting -> {
                    stopping = true
                }

                else -> return
            }

            AppLog.xray(
                message =
                    "Xray core stopped unexpectedly",
                level = AppLog.Level.ERROR
            )

            unregisterNetworkCallback()
            unregisterPhysicalNetworkCallback()

            notificationHandler.removeCallbacks(
                notificationUpdater
            )

            notificationHandler.removeCallbacks(
                xrayHealthChecker
            )

            cleanupScheduled = true

            val latchToWait = startupFinished

            stopWorker =
                Thread(
                    {
                        awaitStartupCompletion(latchToWait)
                        cleanupRuntime()

                        VpnRuntimeManager
                            .clearSessionProfileId()

                        VpnRuntimeManager.updateState(
                            VpnRuntimeState.Failed(
                                "VPN runtime stopped unexpectedly"
                            )
                        )

                        stopForegroundCompat()

                        synchronized(
                            lifecycleLock
                        ) {
                            worker = null
                        }

                        stopSelf()
                    },
                    "XelvraXrayFailureCleanup"
                ).apply {
                    start()
                }
        }
    }

    private fun registerPhysicalNetworkCallback() {
        synchronized(physicalNetworkLock) {
            if (physicalNetworkCallbackRegistered || stopping) {
                return
            }

            val manager =
                getSystemService(ConnectivityManager::class.java)

            val request =
                NetworkRequest.Builder()
                    .addCapability(
                        NetworkCapabilities.NET_CAPABILITY_INTERNET
                    )
                    .addCapability(
                        NetworkCapabilities.NET_CAPABILITY_NOT_VPN
                    )
                    .build()

            try {
                manager.registerNetworkCallback(
                    request,
                    physicalNetworkCallback
                )

                physicalNetworks.clear()
                physicalNetworkCallbackRegistered = true
                publishPhysicalNetworkSnapshotLocked()

                AppLog.vpn(
                    "Physical network monitor registered"
                )
            } catch (error: Throwable) {
                physicalNetworkCallbackRegistered = false
                physicalNetworks.clear()
                publishPhysicalNetworkSnapshotLocked()

                AppLog.vpn(
                    message =
                        "Physical network monitor registration failed (" +
                            error.javaClass.simpleName + ")",
                    level = AppLog.Level.WARNING
                )
            }
        }
    }

    private fun unregisterPhysicalNetworkCallback() {
        synchronized(physicalNetworkLock) {
            if (!physicalNetworkCallbackRegistered) {
                return
            }

            physicalNetworkCallbackRegistered = false
            physicalNetworks.clear()
            publishPhysicalNetworkSnapshotLocked()

            val manager =
                getSystemService(ConnectivityManager::class.java)

            try {
                manager.unregisterNetworkCallback(
                    physicalNetworkCallback
                )

                AppLog.vpn(
                    "Physical network monitor unregistered"
                )
            } catch (error: Throwable) {
                AppLog.vpn(
                    message =
                        "Physical network monitor cleanup failed (" +
                            error.javaClass.simpleName + ")",
                    level = AppLog.Level.WARNING
                )
            }
        }
    }

    private fun registerNetworkCallback() {
        if (networkCallbackRegistered) {
            return
        }

        val manager =
            getSystemService(
                ConnectivityManager::class.java
            )

        try {
            manager.registerDefaultNetworkCallback(
                networkCallback
            )

            networkCallbackRegistered = true
            synchronized(networkIdentityLock) {
                lastObservedDefaultNetwork = null
            }
            networkAvailable =
                hasUsableNetwork()

            AppLog.vpn(
                "Network monitor registered"
            )
        } catch (error: Throwable) {
            networkCallbackRegistered = false
            networkAvailable = hasUsableNetwork()

            AppLog.vpn(
                message =
                    "Failed to register network monitor (" +
                        error.javaClass.simpleName +
                        ")",
                level = AppLog.Level.WARNING
            )
        }
    }

    private fun unregisterNetworkCallback() {
        if (!networkCallbackRegistered) {
            return
        }

        networkCallbackRegistered = false

        synchronized(networkIdentityLock) {
            lastObservedDefaultNetwork = null
        }

        val manager =
            getSystemService(
                ConnectivityManager::class.java
            )

        try {
            manager.unregisterNetworkCallback(
                networkCallback
            )

            AppLog.vpn(
                "Network monitor unregistered"
            )
        } catch (
            error: IllegalArgumentException
        ) {
            AppLog.vpn(
                "Network monitor was already unregistered"
            )
        } catch (error: Throwable) {
            AppLog.vpn(
                message =
                    "Failed to unregister network monitor (" +
                        error.javaClass.simpleName +
                        ")",
                level = AppLog.Level.WARNING
            )
        }
    }

    private fun observeDefaultNetwork(network: Network) {
        if (stopping || !networkCallbackRegistered) {
            return
        }

        val previous: Network?

        synchronized(networkIdentityLock) {
            previous = lastObservedDefaultNetwork

            if (previous == network) {
                return
            }

            lastObservedDefaultNetwork = network
        }

        if (previous == null) {
            AppLog.vpn("Default network identity observed")
        } else {
            AppLog.vpn("Default network identity changed")
        }
    }

    private fun hasUsableNetwork(): Boolean {
        return try {
            val manager =
                getSystemService(
                    ConnectivityManager::class.java
                )

            val network =
                manager.activeNetwork
                    ?: return false

            val capabilities =
                manager.getNetworkCapabilities(
                    network
                )
                    ?: return false

            capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            )
        } catch (error: SecurityException) {
            AppLog.vpn(
                message =
                    "Network state permission unavailable (" +
                        error.javaClass.simpleName +
                        ")",
                level = AppLog.Level.WARNING
            )

            true
        } catch (error: Throwable) {
            AppLog.vpn(
                message =
                    "Failed to inspect network state (" +
                        error.javaClass.simpleName +
                        ")",
                level = AppLog.Level.WARNING
            )

            true
        }
    }

    private fun refreshNetworkState() {
        notificationHandler.post {
            if (
                stopping ||
                !networkCallbackRegistered
            ) {
                return@post
            }

            val available =
                hasUsableNetwork()

            if (available == networkAvailable) {
                return@post
            }

            networkAvailable =
                available

            when (
                val state =
                    VpnRuntimeManager.getState()
            ) {
                is VpnRuntimeState.Connected -> {
                    if (!available) {
                        AppLog.vpn(
                            message =
                                "Underlying network unavailable",
                            level = AppLog.Level.WARNING
                        )

                        VpnRuntimeManager.updateState(
                            VpnRuntimeState.WaitingForNetwork
                        )

                        DelayMonitor.stop()

                        updateForegroundNotification()
                    }
                }

                VpnRuntimeState.WaitingForNetwork -> {
                    if (
                        available &&
                        synchronized(resourceLock) {
                            vpnInterface != null &&
                                XrayRuntimeController.isRunning()
                        }
                    ) {
                        val startedAt =
                            notificationConnectedAt
                                .takeIf {
                                    it > 0L
                                }
                                ?: System.currentTimeMillis()

                        AppLog.vpn(
                            "Underlying network restored"
                        )

                        VpnRuntimeManager.updateState(
                            VpnRuntimeState.Connected(
                                startedAt =
                                    startedAt
                            )
                        )

                        val settingsStore =
                            SettingsStore(this@XelvraVpnService)

                        if (
                            settingsStore.isTestUrlEnabled()
                        ) {
                            DelayMonitor.start(
                                url =
                                    settingsStore.getTestUrl(),
                                intervalSeconds =
                                    settingsStore
                                        .getTestUrlIntervalSeconds(),
                                timeoutSeconds =
                                    settingsStore
                                        .getConnectionTimeoutSeconds()
                            )
                        }

                        updateForegroundNotification()
                    }
                }

                else -> Unit
            }
        }
    }

    override fun onRevoke() {
        super.onRevoke()
        stopVpn()
    }

    override fun onDestroy() {
        synchronized(lifecycleLock) {
            stopping = true
            recoveryWorker?.interrupt()
        }

        unregisterNetworkCallback()
        unregisterPhysicalNetworkCallback()

        notificationHandler.removeCallbacks(
            notificationUpdater
        )

        notificationHandler.removeCallbacks(
            xrayHealthChecker
        )

        val destroyCleanupLatch =
            synchronized(lifecycleLock) {
                if (cleanupScheduled) {
                    null
                } else {
                    cleanupScheduled = true
                    startupFinished
                }
            }

        if (destroyCleanupLatch != null) {
            Thread(
                {
                    awaitStartupCompletion(destroyCleanupLatch)
                    cleanupRuntime()

                    VpnRuntimeManager.clearSessionProfileId()

                    if (
                        VpnRuntimeManager.getState()
                            !is VpnRuntimeState.Failed
                    ) {
                        VpnRuntimeManager.updateState(
                            VpnRuntimeState.Disconnected
                        )
                    }
                },
                "XelvraVpnDestroyCleanup"
            ).start()
        }

        super.onDestroy()
    }

    private fun applyPerAppVpnPolicy(
        builder: Builder,
        mode: PerAppVpnMode,
        selectedPackages: Set<String>
    ) {
        when (mode) {
            PerAppVpnMode.ALL_APPS -> {
                builder.addDisallowedApplication(
                    packageName
                )
            }

            PerAppVpnMode.SELECTED_APPS -> {
                var applied = 0

                selectedPackages
                    .asSequence()
                    .filter {
                        it != packageName
                    }
                    .sorted()
                    .forEach { targetPackage ->

                        try {
                            builder
                                .addAllowedApplication(
                                    targetPackage
                                )

                            applied += 1
                        } catch (
                            error:
                                PackageManager.NameNotFoundException
                        ) {
                            AppLog.vpn(
                                "Per-App VPN ignored a missing package"
                            )
                        }
                    }

                AppLog.vpn(
                    "Per-App VPN allowed packages applied: " +
                        applied
                )
            }

            PerAppVpnMode.BYPASS_SELECTED -> {
                builder.addDisallowedApplication(
                    packageName
                )

                var applied = 0

                selectedPackages
                    .asSequence()
                    .filter {
                        it != packageName
                    }
                    .sorted()
                    .forEach { targetPackage ->

                        try {
                            builder
                                .addDisallowedApplication(
                                    targetPackage
                                )

                            applied += 1
                        } catch (
                            error:
                                PackageManager.NameNotFoundException
                        ) {
                            AppLog.vpn(
                                "Per-App VPN ignored a missing package"
                            )
                        }
                    }

                AppLog.vpn(
                    "Per-App VPN bypass packages applied: " +
                        applied
                )
            }
        }
    }

    private fun addBypassLanIpv4Routes(
        builder: Builder
    ) {
        BYPASS_LAN_IPV4_ROUTES.forEach {
            (address, prefix) ->

            builder.addRoute(
                address,
                prefix
            )
        }
    }

    private fun addBypassLanIpv6Routes(
        builder: Builder
    ) {
        builder.addRoute(
            "2000::",
            3
        )
    }

    private fun startForegroundNow() {
        val notification =
            buildVpnNotification()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            android.content.pm.ServiceInfo
                .FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    private fun updateForegroundNotification() {
        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.notify(
            NOTIFICATION_ID,
            buildVpnNotification()
        )
    }

    private fun buildVpnNotification():
        android.app.Notification {

        val launchIntent =
            Intent(
                this,
                MainActivity::class.java
            ).apply {
                flags =
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

        val contentIntent =
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or
                    PendingIntent.FLAG_UPDATE_CURRENT
            )

        val stopIntent =
            Intent(
                this,
                XelvraVpnService::class.java
            ).apply {
                action =
                    ACTION_STOP
            }

        val stopPendingIntent =
            PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE or
                    PendingIntent.FLAG_UPDATE_CURRENT
            )

        val state =
            VpnRuntimeManager.getState()

        val status =
            when (state) {
                VpnRuntimeState.Preparing ->
                    getString(
                        R.string.vpn_notification_preparing
                    )

                VpnRuntimeState.Connecting,
                VpnRuntimeState.Reconnecting ->
                    getString(
                        R.string.vpn_notification_connecting
                    )

                is VpnRuntimeState.Connected ->
                    getString(
                        R.string.vpn_notification_connected
                    )

                VpnRuntimeState.WaitingForNetwork ->
                    getString(
                        R.string.vpn_notification_waiting
                    )

                VpnRuntimeState.Disconnecting ->
                    getString(
                        R.string.vpn_notification_stopping
                    )

                VpnRuntimeState.Disconnected,
                is VpnRuntimeState.Failed ->
                    getString(
                        R.string.status_disconnected
                    )
            }

        val profileLine =
            if (
                notificationProfileName.isNotBlank() &&
                notificationProtocol.isNotBlank()
            ) {
                getString(
                    R.string.vpn_notification_profile_format,
                    notificationProfileName,
                    notificationProtocol
                )
            } else {
                status
            }

        val builder =
            NotificationCompat.Builder(
                this,
                NOTIFICATION_CHANNEL_ID
            )
                .setSmallIcon(
                    R.drawable.ic_launcher
                )
                .setContentTitle(
                    getString(R.string.app_name) +
                        " • " +
                        status
                )
                .setContentText(
                    profileLine
                )
                .setContentIntent(
                    contentIntent
                )
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(
                    NotificationCompat.CATEGORY_SERVICE
                )
                .setVisibility(
                    NotificationCompat.VISIBILITY_PRIVATE
                )
                .setPriority(
                    NotificationCompat.PRIORITY_LOW
                )
                .addAction(
                    R.drawable.ic_stop,
                    getString(
                        R.string.vpn_notification_stop
                    ),
                    stopPendingIntent
                )

        if (state is VpnRuntimeState.Connected) {
            val snapshot =
                TrafficMonitor.getSnapshot()

            val speedLine =
                getString(
                    R.string.vpn_notification_speed_format,
                    formatRate(
                        snapshot.uploadBytesPerSecond
                    ),
                    formatRate(
                        snapshot.downloadBytesPerSecond
                    )
                )

            val startedAt =
                if (notificationConnectedAt > 0L) {
                    notificationConnectedAt
                } else {
                    state.startedAt
                }

            val durationLine =
                getString(
                    R.string.vpn_notification_session_format,
                    formatDuration(
                        (
                            System.currentTimeMillis() -
                                startedAt
                        ).coerceAtLeast(0L)
                    )
                )

            val detail =
                getString(
                    R.string.vpn_notification_detail_format,
                    profileLine,
                    speedLine,
                    durationLine
                )

            builder
                .setContentText(
                    "$profileLine • $speedLine"
                )
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(detail)
                )
        } else {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(profileLine)
            )
        }

        return builder.build()
    }

    private fun protocolLabel(
        protocol: Protocol
    ): String {
        return when (protocol) {
            Protocol.VMESS ->
                "VMess"

            Protocol.VLESS ->
                "VLESS"

            Protocol.TROJAN ->
                "Trojan"

            Protocol.SHADOWSOCKS ->
                "Shadowsocks"
        }
    }

    private fun formatRate(
        bytesPerSecond: Long
    ): String {
        return "${formatBytes(bytesPerSecond)}/s"
    }

    private fun formatBytes(
        bytes: Long
    ): String {
        val value =
            bytes.coerceAtLeast(0L)

        return when {
            value >= 1024L * 1024L * 1024L ->
                String.format(
                    Locale.US,
                    "%.2f GB",
                    value.toDouble() /
                        (
                            1024.0 *
                                1024.0 *
                                1024.0
                        )
                )

            value >= 1024L * 1024L ->
                String.format(
                    Locale.US,
                    "%.2f MB",
                    value.toDouble() /
                        (
                            1024.0 *
                                1024.0
                        )
                )

            value >= 1024L ->
                String.format(
                    Locale.US,
                    "%.1f KB",
                    value.toDouble() /
                        1024.0
                )

            else ->
                "$value B"
        }
    }

    private fun formatDuration(
        milliseconds: Long
    ): String {
        val totalSeconds =
            milliseconds / 1000L

        val hours =
            totalSeconds / 3600L

        val minutes =
            (totalSeconds % 3600L) /
                60L

        val seconds =
            totalSeconds % 60L

        return String.format(
            Locale.US,
            "%02d:%02d:%02d",
            hours,
            minutes,
            seconds
        )
    }

    private fun stopForegroundCompat() {
        ServiceCompat.stopForeground(
            this,
            ServiceCompat.STOP_FOREGROUND_REMOVE
        )
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT <
            android.os.Build.VERSION_CODES.O
        ) {
            return
        }

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        val channel =
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(
                    R.string.vpn_notification_channel_name
                ),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description =
                    getString(
                        R.string
                            .vpn_notification_channel_description
                    )

                setSound(
                    null,
                    null
                )

                enableVibration(false)
                setShowBadge(false)
            }

        manager.createNotificationChannel(
            channel
        )
    }

    companion object {

        @Volatile
        private var physicalNetworkSnapshot = "UNAVAILABLE"

        fun getPhysicalNetworkSnapshot(): String =
            physicalNetworkSnapshot


        private const val XRAY_HEALTH_CHECK_INTERVAL_MS =
            1000L

        private const val XRAY_RECOVERY_STABLE_MS =
            60_000L

        private const val NOTIFICATION_CHANNEL_ID =
            "xelvra_vpn"

        private const val NOTIFICATION_ID =
            1001

        private const val VPN_ADDRESS =
            "10.10.0.2"

        private const val VPN_PREFIX_LENGTH =
            32

        private const val VPN_IPV6_ADDRESS =
            "fd00:10:10::2"

        private const val VPN_IPV6_PREFIX_LENGTH =
            128

        private val BYPASS_LAN_IPV4_ROUTES =
            arrayOf(
                "1.0.0.0" to 8,
                "2.0.0.0" to 7,
                "4.0.0.0" to 6,
                "8.0.0.0" to 7,
                "11.0.0.0" to 8,
                "12.0.0.0" to 6,
                "16.0.0.0" to 4,
                "32.0.0.0" to 3,
                "64.0.0.0" to 3,
                "96.0.0.0" to 4,
                "112.0.0.0" to 5,
                "120.0.0.0" to 6,
                "124.0.0.0" to 7,
                "126.0.0.0" to 8,
                "128.0.0.0" to 3,
                "160.0.0.0" to 5,
                "168.0.0.0" to 8,
                "169.0.0.0" to 9,
                "169.128.0.0" to 10,
                "169.192.0.0" to 11,
                "169.224.0.0" to 12,
                "169.240.0.0" to 13,
                "169.248.0.0" to 14,
                "169.252.0.0" to 15,
                "169.255.0.0" to 16,
                "170.0.0.0" to 7,
                "172.0.0.0" to 12,
                "172.32.0.0" to 11,
                "172.64.0.0" to 10,
                "172.128.0.0" to 9,
                "173.0.0.0" to 8,
                "174.0.0.0" to 7,
                "176.0.0.0" to 4,
                "192.0.0.0" to 9,
                "192.128.0.0" to 11,
                "192.160.0.0" to 13,
                "192.169.0.0" to 16,
                "192.170.0.0" to 15,
                "192.172.0.0" to 14,
                "192.176.0.0" to 12,
                "192.192.0.0" to 10,
                "193.0.0.0" to 8,
                "194.0.0.0" to 7,
                "196.0.0.0" to 6,
                "200.0.0.0" to 5,
                "208.0.0.0" to 4,
                "224.0.0.0" to 3
            )

        private const val DNS_SERVER =
            "1.1.1.1"

        const val ACTION_START =
            "com.mdevz.xelvra.action.START_VPN"

        const val ACTION_STOP =
            "com.mdevz.xelvra.action.STOP_VPN"
    }
}
