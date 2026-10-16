package com.mdevz.xelvra.ui.home

import android.content.Intent
import android.provider.Settings
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
import android.net.VpnService
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mdevz.xelvra.data.local.SettingsStore
import com.mdevz.xelvra.R
import com.mdevz.xelvra.core.diagnostics.ConnectionDiagnostics
import com.mdevz.xelvra.core.diagnostics.DiagnosticVpnStatus
import com.mdevz.xelvra.core.diagnostics.NetworkDiagnostics
import com.mdevz.xelvra.core.diagnostics.NetworkDiagnosticStatus
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import com.mdevz.xelvra.core.traffic.TrafficMonitor
import com.mdevz.xelvra.core.xray.XrayRuntimeController
import com.mdevz.xelvra.core.xray.DelayMonitor
import com.mdevz.xelvra.core.xray.DelayQualityState
import com.mdevz.xelvra.core.vpn.VpnRuntimeManager
import com.mdevz.xelvra.core.vpn.VpnRuntimeState
import com.mdevz.xelvra.core.vpn.XelvraVpnService
import com.mdevz.xelvra.data.local.ActiveProfileStore
import com.mdevz.xelvra.data.local.ProfileLocalStorage
import com.mdevz.xelvra.data.model.Protocol
import com.mdevz.xelvra.data.model.ServerProfile
import com.mdevz.xelvra.data.repository.ProfileRepository
import com.google.android.material.color.MaterialColors
import com.mdevz.xelvra.network.latency.EndpointKey
import com.mdevz.xelvra.network.latency.EndpointLatencyManager
import com.mdevz.xelvra.network.latency.EndpointLatencyState
import com.mdevz.xelvra.ui.home.widget.TrafficChartView
import java.util.Locale

class HomeFragment : Fragment(
    R.layout.fragment_home
) {

    private val diagnosticsExecutor: ExecutorService =
        Executors.newSingleThreadExecutor()

    private val diagnosticsHandler =
        Handler(Looper.getMainLooper())

    private var diagnosticsTask: Future<*>? = null

    private var diagnosticsDialog:
        androidx.appcompat.app.AlertDialog? = null

    private var diagnosticsGeneration = 0L

    private fun cancelDiagnostics() {
        diagnosticsGeneration++
        diagnosticsTask?.cancel(true)
        diagnosticsTask = null
        diagnosticsHandler.removeCallbacksAndMessages(null)
    }



    private lateinit var repository: ProfileRepository
    private lateinit var activeProfileStore: ActiveProfileStore

    private var continueVpnAfterNotificationPermission =
        false

    private val trafficHandler =
        Handler(Looper.getMainLooper())

    private val trafficUiUpdater =
        object : Runnable {
            override fun run() {
                renderTraffic()
                renderRuntimeDetails()
                renderActiveProfileLatency()
                renderXrayQuality()
                renderVpnHealthDashboard()

                trafficHandler.postDelayed(
                    this,
                    1000L
                )
            }
        }

    private val latencyListener: () -> Unit = {
        renderActiveProfileLatency()
    }

    private val vpnStateListener:
        (VpnRuntimeState) -> Unit = {
            renderActiveProfile()
            renderVpnState()
            renderRuntimeDetails()
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            notificationPreferences()
                .edit()
                .putBoolean(
                    PREF_NOTIFICATION_PERMISSION_REQUESTED,
                    true
                )
                .apply()

            renderNotificationPermissionCard()

            if (granted) {
                val shouldContinue =
                    continueVpnAfterNotificationPermission

                continueVpnAfterNotificationPermission =
                    false

                if (shouldContinue) {
                    ensureNotificationPermissionThenVpn()
                }
            } else {
                continueVpnAfterNotificationPermission =
                    false

                VpnRuntimeManager.updateState(
                    VpnRuntimeState.Disconnected
                )

                renderVpnState()
                renderNotificationPermissionCard()
            }
        }

    private val vpnPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                startVpnService()
            } else {
                VpnRuntimeManager.updateState(
                    VpnRuntimeState.Disconnected
                )

                Toast.makeText(
                    requireContext(),
                    R.string.vpn_permission_denied,
                    Toast.LENGTH_SHORT
                ).show()

                renderVpnState()
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        val appContext =
            requireContext().applicationContext

        repository = ProfileRepository(
            ProfileLocalStorage(appContext)
        )

        activeProfileStore =
            ActiveProfileStore(appContext)

        view.findViewById<MaterialButton>(
            R.id.start_button
        ).setOnClickListener {
            handleVpnButton()
        }

        view.findViewById<View>(
            R.id.notification_permission_allow
        ).setOnClickListener {
            requestNotificationPermissionFromReminder()
        }

        view.findViewById<View>(
            R.id.home_diagnostics_button
        ).setOnClickListener {
            showConnectionDiagnosticsDialog()
        }

        renderNotificationPermissionCard()
        renderVpnState()
    }



    private fun showConnectionDiagnosticsDialog() {
        val snapshot = ConnectionDiagnostics.snapshot()

        val vpnStatus = when (snapshot.vpnStatus) {
            DiagnosticVpnStatus.DISCONNECTED -> R.string.diagnostics_disconnected
            DiagnosticVpnStatus.PREPARING -> R.string.diagnostics_preparing
            DiagnosticVpnStatus.CONNECTING -> R.string.diagnostics_connecting
            DiagnosticVpnStatus.CONNECTED -> R.string.diagnostics_connected
            DiagnosticVpnStatus.WAITING_FOR_NETWORK -> R.string.diagnostics_waiting_network
            DiagnosticVpnStatus.RECONNECTING -> R.string.diagnostics_reconnecting
            DiagnosticVpnStatus.DISCONNECTING -> R.string.diagnostics_disconnecting
            DiagnosticVpnStatus.FAILED -> R.string.diagnostics_failed
        }

        val xrayStatus = if (snapshot.xrayRunning) {
            R.string.diagnostics_running
        } else {
            R.string.diagnostics_stopped
        }

        val initialMessage = buildString {
            append(getString(R.string.diagnostics_vpn))
            append(": ")
            append(getString(vpnStatus))
            append("\n\n")
            append(getString(R.string.diagnostics_xray))
            append(": ")
            append(getString(xrayStatus))
            append("\n\n")
            append(getString(R.string.diagnostics_dns))
            append(": ")
            append(getString(R.string.diagnostics_not_tested))
            append("\n")
            append(getString(R.string.diagnostics_internet))
            append(": ")
            append(getString(R.string.diagnostics_not_tested))
            append("\n")
            append(getString(R.string.diagnostics_latency))
            append(": ")
            append(getString(R.string.diagnostics_not_tested))
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.diagnostics_title)
            .setMessage(initialMessage)
            .setNegativeButton(R.string.diagnostics_close, null)
            .setPositiveButton(R.string.diagnostics_run, null)
            .create()

        dialog.setOnShowListener {
            val runButton = dialog.getButton(
                android.content.DialogInterface.BUTTON_POSITIVE
            )

            runButton.setOnClickListener {
                runButton.isEnabled = false

                val messageView = dialog.findViewById<TextView>(
                    android.R.id.message
                )

                messageView?.text = getString(
                    R.string.diagnostics_testing
                )

                cancelDiagnostics()
                val generation = diagnosticsGeneration

                diagnosticsTask = diagnosticsExecutor.submit {
                    val report = runCatching {
                        NetworkDiagnostics.runTests()
                    }.getOrNull()

                    val dns = when (report?.dns?.status) {
                        NetworkDiagnosticStatus.SUCCESS ->
                            R.string.diagnostics_success
                        NetworkDiagnosticStatus.TIMEOUT ->
                            R.string.diagnostics_timeout
                        else ->
                            R.string.diagnostics_failed
                    }

                    val internet = when (report?.internet?.status) {
                        NetworkDiagnosticStatus.SUCCESS ->
                            R.string.diagnostics_success
                        NetworkDiagnosticStatus.TIMEOUT ->
                            R.string.diagnostics_timeout
                        else ->
                            R.string.diagnostics_failed
                    }

                    val latency = report?.internet?.latencyMs

                    diagnosticsHandler.post {
                        if (
                            !dialog.isShowing ||
                            !isAdded ||
                            !lifecycle.currentState.isAtLeast(
                                androidx.lifecycle.Lifecycle.State.STARTED
                            ) ||
                            diagnosticsDialog !== dialog ||
                            generation != diagnosticsGeneration
                        ) {
                            return@post
                        }

                        val updated = buildString {
                            append(getString(R.string.diagnostics_vpn))
                            append(": ")
                            append(getString(vpnStatus))
                            append("\n\n")
                            append(getString(R.string.diagnostics_xray))
                            append(": ")
                            append(getString(xrayStatus))
                            append("\n\n")
                            append(getString(R.string.diagnostics_dns))
                            append(": ")
                            append(getString(dns))
                            append("\n")
                            append(getString(R.string.diagnostics_internet))
                            append(": ")
                            append(getString(internet))
                            append("\n")
                            append(getString(R.string.diagnostics_latency))
                            append(": ")
                            append(
                                if (latency != null) {
                                    "${latency} ms"
                                } else {
                                    getString(R.string.diagnostics_not_tested)
                                }
                            )
                        }

                        messageView?.text = updated
                        runButton.isEnabled = true
                    }

                }
            }
        }

        dialog.setOnDismissListener {
            if (diagnosticsDialog === dialog) {
                cancelDiagnostics()
                diagnosticsDialog = null
            }
        }

        diagnosticsDialog = dialog
        dialog.show()
    }

    override fun onStart() {
        super.onStart()

        VpnRuntimeManager.addListener(
            vpnStateListener
        )

        EndpointLatencyManager.addListener(
            latencyListener
        )

        trafficHandler.removeCallbacks(
            trafficUiUpdater
        )

        trafficHandler.post(
            trafficUiUpdater
        )
    }

    override fun onDestroy() {
        cancelDiagnostics()
        diagnosticsExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onStop() {
        cancelDiagnostics()
        diagnosticsDialog?.dismiss()
        diagnosticsDialog = null

        trafficHandler.removeCallbacks(
            trafficUiUpdater
        )

        EndpointLatencyManager.removeListener(
            latencyListener
        )

        VpnRuntimeManager.removeListener(
            vpnStateListener
        )

        super.onStop()
    }

    override fun onResume() {
        super.onResume()

        if (
            ::repository.isInitialized &&
            ::activeProfileStore.isInitialized
        ) {
            renderActiveProfile()
            renderNotificationPermissionCard()
            renderVpnState()
        }
    }

    private fun handleVpnButton() {
        when (VpnRuntimeManager.getState()) {
            is VpnRuntimeState.Connected,
            is VpnRuntimeState.Connecting,
            is VpnRuntimeState.Preparing,
            is VpnRuntimeState.WaitingForNetwork,
            is VpnRuntimeState.Reconnecting -> {
                stopVpnService()
            }

            is VpnRuntimeState.Disconnecting -> {
                // Ignore repeated taps while stopping.
            }

            is VpnRuntimeState.Disconnected,
            is VpnRuntimeState.Failed -> {
                prepareVpn()
            }
        }
    }

    private fun prepareVpn() {
        val activeId =
            activeProfileStore.getActiveProfileId()

        if (activeId == null) {
            Toast.makeText(
                requireContext(),
                getString(R.string.select_active_profile_first),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (repository.findProfile(activeId) == null) {
            activeProfileStore.clearActiveProfile()

            renderActiveProfile()

            Toast.makeText(
                requireContext(),
                getString(R.string.active_profile_not_found),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        VpnRuntimeManager.updateState(
            VpnRuntimeState.Preparing
        )
        renderVpnState()

        val settingsStore =
            SettingsStore(requireContext())

        if (
            settingsStore
                .hasAcceptedVpnDisclosure()
        ) {
            ensureNotificationPermissionThenVpn()
        } else {
            showVpnDisclosure(
                settingsStore
            )
        }
    }

    private fun showVpnDisclosure(
        settingsStore: SettingsStore
    ) {
        val content =
            layoutInflater.inflate(
                R.layout.dialog_vpn_disclosure,
                null,
                false
            )

        val dialog =
            MaterialAlertDialogBuilder(
                requireContext()
            )
                .setTitle(
                    R.string.vpn_disclosure_title
                )
                .setView(content)
                .setNegativeButton(
                    R.string.vpn_disclosure_cancel
                ) { _, _ ->
                    VpnRuntimeManager.updateState(
                        VpnRuntimeState.Disconnected
                    )
                    renderVpnState()
                }
                .setPositiveButton(
                    R.string.vpn_disclosure_continue
                ) { _, _ ->
                    settingsStore
                        .setVpnDisclosureAccepted(
                            true
                        )

                    requestVpnPermission()
                }
                .setOnCancelListener {
                    VpnRuntimeManager.updateState(
                        VpnRuntimeState.Disconnected
                    )
                    renderVpnState()
                }
                .create()

        dialog.show()
    }

    private fun notificationPreferences() =
        requireContext().getSharedPreferences(
            NOTIFICATION_PERMISSION_PREFS,
            android.content.Context.MODE_PRIVATE
        )

    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT <
            Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureNotificationPermissionThenVpn() {
        if (!isAdded) {
            VpnRuntimeManager.updateState(
                VpnRuntimeState.Disconnected
            )
            return
        }

        if (hasNotificationPermission()) {
            requestVpnPermission()
            return
        }

        val preferences =
            notificationPreferences()

        val disclosureSeen =
            preferences.getBoolean(
                PREF_NOTIFICATION_DISCLOSURE_SEEN,
                false
            )

        if (disclosureSeen) {
            showNotificationPermissionReminder(
                continueVpn = true
            )
        } else {
            showNotificationPermissionDisclosure()
        }
    }

    private fun showNotificationPermissionDisclosure() {
        val content =
            layoutInflater.inflate(
                R.layout.dialog_notification_permission,
                null,
                false
            )

        MaterialAlertDialogBuilder(
            requireContext()
        )
            .setTitle(
                R.string.notification_permission_title
            )
            .setView(content)
            .setNegativeButton(
                R.string.notification_permission_later
            ) { _, _ ->
                notificationPreferences()
                    .edit()
                    .putBoolean(
                        PREF_NOTIFICATION_DISCLOSURE_SEEN,
                        true
                    )
                    .apply()

                continueVpnAfterNotificationPermission =
                    false

                VpnRuntimeManager.updateState(
                    VpnRuntimeState.Disconnected
                )

                renderNotificationPermissionCard()
                renderVpnState()
            }
            .setPositiveButton(
                R.string.notification_permission_continue
            ) { _, _ ->
                notificationPreferences()
                    .edit()
                    .putBoolean(
                        PREF_NOTIFICATION_DISCLOSURE_SEEN,
                        true
                    )
                    .apply()

                launchNotificationPermission(
                    continueVpn = true
                )
            }
            .setOnCancelListener {
                notificationPreferences()
                    .edit()
                    .putBoolean(
                        PREF_NOTIFICATION_DISCLOSURE_SEEN,
                        true
                    )
                    .apply()

                continueVpnAfterNotificationPermission =
                    false

                VpnRuntimeManager.updateState(
                    VpnRuntimeState.Disconnected
                )

                renderNotificationPermissionCard()
                renderVpnState()
            }
            .show()
    }

    private fun showNotificationPermissionReminder(
        continueVpn: Boolean
    ) {
        val requested =
            notificationPreferences()
                .getBoolean(
                    PREF_NOTIFICATION_PERMISSION_REQUESTED,
                    false
                )

        val canRequestAgain =
            !requested ||
                shouldShowRequestPermissionRationale(
                    Manifest.permission.POST_NOTIFICATIONS
                )

        if (!canRequestAgain) {
            showNotificationSettingsDialog()
            return
        }

        MaterialAlertDialogBuilder(
            requireContext()
        )
            .setTitle(
                R.string.notification_reminder_dialog_title
            )
            .setMessage(
                R.string.notification_reminder_dialog_message
            )
            .setNegativeButton(
                android.R.string.cancel
            ) { _, _ ->
                if (continueVpn) {
                    VpnRuntimeManager.updateState(
                        VpnRuntimeState.Disconnected
                    )
                    renderVpnState()
                }
            }
            .setPositiveButton(
                R.string.notification_permission_allow
            ) { _, _ ->
                launchNotificationPermission(
                    continueVpn
                )
            }
            .show()
    }

    private fun launchNotificationPermission(
        continueVpn: Boolean
    ) {
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.TIRAMISU
        ) {
            if (continueVpn) {
                requestVpnPermission()
            }
            return
        }

        continueVpnAfterNotificationPermission =
            continueVpn

        notificationPermissionLauncher.launch(
            Manifest.permission.POST_NOTIFICATIONS
        )
    }

    private fun requestNotificationPermissionFromReminder() {
        if (hasNotificationPermission()) {
            renderNotificationPermissionCard()
            return
        }

        showNotificationPermissionReminder(
            continueVpn = false
        )
    }

    private fun showNotificationSettingsDialog() {
        continueVpnAfterNotificationPermission =
            false

        VpnRuntimeManager.updateState(
            VpnRuntimeState.Disconnected
        )

        renderVpnState()

        MaterialAlertDialogBuilder(
            requireContext()
        )
            .setTitle(
                R.string.notification_settings_title
            )
            .setMessage(
                R.string.notification_settings_message
            )
            .setNegativeButton(
                android.R.string.cancel,
                null
            )
            .setPositiveButton(
                R.string.notification_open_settings
            ) { _, _ ->
                val intent =
                    Intent(
                        Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    ).apply {
                        putExtra(
                            Settings.EXTRA_APP_PACKAGE,
                            requireContext().packageName
                        )
                    }

                startActivity(intent)
            }
            .show()
    }

    private fun renderNotificationPermissionCard() {
        val root =
            view ?: return

        val card =
            root.findViewById<View>(
                R.id.notification_permission_card
            )

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.TIRAMISU
        ) {
            card.visibility =
                View.GONE
            return
        }

        val disclosureSeen =
            notificationPreferences()
                .getBoolean(
                    PREF_NOTIFICATION_DISCLOSURE_SEEN,
                    false
                )

        card.visibility =
            if (
                disclosureSeen &&
                !hasNotificationPermission()
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun requestVpnPermission() {
        if (!isAdded) {
            VpnRuntimeManager.updateState(
                VpnRuntimeState.Disconnected
            )
            return
        }

        val permissionIntent =
            VpnService.prepare(
                requireContext()
            )

        if (permissionIntent == null) {
            startVpnService()
        } else {
            vpnPermissionLauncher.launch(
                permissionIntent
            )
        }
    }

    private fun startVpnService() {
        val context =
            requireContext().applicationContext

        val intent =
            Intent(
                context,
                XelvraVpnService::class.java
            ).apply {
                action =
                    XelvraVpnService.ACTION_START
            }

        try {
            ContextCompat.startForegroundService(
                context,
                intent
            )
        } catch (error: Throwable) {
            VpnRuntimeManager.updateState(
                VpnRuntimeState.Failed(
                    "VPN startup failed"
                )
            )

            Toast.makeText(
                requireContext(),
                R.string.vpn_start_failed,
                Toast.LENGTH_SHORT
            ).show()
        }

        renderVpnState()
    }

    private fun stopVpnService() {
        val context =
            requireContext().applicationContext

        val intent =
            Intent(
                context,
                XelvraVpnService::class.java
            ).apply {
                action =
                    XelvraVpnService.ACTION_STOP
            }

        context.startService(intent)

        renderVpnState()
    }

    private fun renderVpnState() {
        val root = view ?: return

        val button =
            root.findViewById<MaterialButton>(
                R.id.start_button
            )

        when (
            VpnRuntimeManager.getState()
        ) {
            is VpnRuntimeState.Disconnected,
            is VpnRuntimeState.Failed -> {
                button.setText(R.string.start)
                button.setIconResource(
                    R.drawable.ic_play
                )
                button.isEnabled = true
            }

            is VpnRuntimeState.Preparing -> {
                button.setText(R.string.stop)
                button.setIconResource(
                    R.drawable.ic_stop
                )
                button.isEnabled = true
            }

            is VpnRuntimeState.Connecting,
            is VpnRuntimeState.Reconnecting -> {
                button.setText(R.string.stop)
                button.setIconResource(
                    R.drawable.ic_stop
                )
                button.isEnabled = true
            }

            is VpnRuntimeState.Connected -> {
                button.setText(R.string.stop)
                button.setIconResource(
                    R.drawable.ic_stop
                )
                button.isEnabled = true
            }

            is VpnRuntimeState.WaitingForNetwork,
            is VpnRuntimeState.Reconnecting -> {
                button.setText(R.string.stop)
                button.setIconResource(
                    R.drawable.ic_stop
                )
                button.isEnabled = true
            }

            is VpnRuntimeState.Disconnecting -> {
                button.setText(R.string.stopping)
                button.setIconResource(
                    R.drawable.ic_stop
                )
                button.isEnabled = false
            }
        }
    }

    private fun renderRuntimeDetails() {
        val root = view ?: return

        val runtime =
            root.findViewById<View>(
                R.id.active_profile_runtime
            )

        val activeId =
            if (::activeProfileStore.isInitialized) {
                activeProfileStore
                    .getActiveProfileId()
            } else {
                null
            }

        runtime.visibility =
            if (activeId == null) {
                View.GONE
            } else {
                View.VISIBLE
            }

        if (activeId == null) {
            return
        }

        val status =
            root.findViewById<TextView>(
                R.id.active_profile_status
            )

        val duration =
            root.findViewById<TextView>(
                R.id.active_profile_duration
            )

        status.visibility = View.VISIBLE

        val state =
            VpnRuntimeManager.getState()

        when (state) {
            is VpnRuntimeState.Disconnected -> {
                status.setText(R.string.status_disconnected)
                duration.text = "00:00:00"
            }

            is VpnRuntimeState.Preparing -> {
                status.setText(R.string.status_preparing)
                duration.text = "00:00:00"
            }

            is VpnRuntimeState.Connecting,
            is VpnRuntimeState.Reconnecting -> {
                status.setText(R.string.status_connecting)
                duration.text = "00:00:00"
            }

            is VpnRuntimeState.Connected -> {
                status.setText(R.string.status_connected)

                duration.text =
                    formatDuration(
                        (
                            System.currentTimeMillis() -
                                state.startedAt
                        ).coerceAtLeast(0L)
                    )
            }

            is VpnRuntimeState.WaitingForNetwork -> {
                status.setText(R.string.status_waiting)
                duration.text = "00:00:00"
            }

            is VpnRuntimeState.Disconnecting -> {
                status.setText(R.string.status_stopping)
            }

            is VpnRuntimeState.Failed -> {
                status.setText(R.string.status_failed)
                duration.text = "00:00:00"
            }
        }

        renderStatusPill(
            status = status,
            state = state
        )
    }

    private fun renderStatusPill(
        status: TextView,
        state: VpnRuntimeState
    ) {
        val backgroundColor: Int
        val textColor: Int

        when (state) {
            is VpnRuntimeState.Connected -> {
                backgroundColor =
                    MaterialColors.getColor(
                        status,
                        com.google.android.material.R.attr
                            .colorPrimaryContainer
                    )

                textColor =
                    MaterialColors.getColor(
                        status,
                        com.google.android.material.R.attr
                            .colorOnPrimaryContainer
                    )
            }

            is VpnRuntimeState.Preparing,
            is VpnRuntimeState.Connecting,
            is VpnRuntimeState.WaitingForNetwork,
            is VpnRuntimeState.Reconnecting,
            is VpnRuntimeState.Disconnecting -> {
                backgroundColor =
                    MaterialColors.getColor(
                        status,
                        com.google.android.material.R.attr
                            .colorSecondaryContainer
                    )

                textColor =
                    MaterialColors.getColor(
                        status,
                        com.google.android.material.R.attr
                            .colorOnSecondaryContainer
                    )
            }

            is VpnRuntimeState.Failed -> {
                backgroundColor =
                    MaterialColors.getColor(
                        status,
                        com.google.android.material.R.attr
                            .colorErrorContainer
                    )

                textColor =
                    MaterialColors.getColor(
                        status,
                        com.google.android.material.R.attr
                            .colorOnErrorContainer
                    )
            }

            is VpnRuntimeState.Disconnected -> {
                backgroundColor =
                    MaterialColors.getColor(
                        status,
                        com.google.android.material.R.attr
                            .colorSurfaceContainerHighest
                    )

                textColor =
                    MaterialColors.getColor(
                        status,
                        com.google.android.material.R.attr
                            .colorOnSurfaceVariant
                    )
            }
        }

        status.background =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius =
                    100f * resources
                        .displayMetrics
                        .density

                setColor(backgroundColor)
            }

        status.setTextColor(textColor)
    }

    private fun formatDuration(
        milliseconds: Long
    ): String {
        val totalSeconds =
            milliseconds / 1000L

        val hours =
            totalSeconds / 3600L

        val minutes =
            (totalSeconds % 3600L) / 60L

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

    private fun renderTraffic() {
        val root = view ?: return

        val snapshot =
            TrafficMonitor.getSnapshot()

        root.findViewById<TextView>(
            R.id.traffic_upload_speed
        ).text =
            formatRate(
                snapshot.uploadBytesPerSecond
            )

        root.findViewById<TextView>(
            R.id.traffic_download_speed
        ).text =
            formatRate(
                snapshot.downloadBytesPerSecond
            )

        root.findViewById<TextView>(
            R.id.traffic_session_upload
        ).text =
            formatBytes(
                snapshot.sessionUploadBytes
            )

        root.findViewById<TextView>(
            R.id.traffic_session_download
        ).text =
            formatBytes(
                snapshot.sessionDownloadBytes
            )

        root.findViewById<TrafficChartView>(
            R.id.traffic_chart
        ).setTraffic(
            uploadHistory =
                snapshot.uploadHistory,
            downloadHistory =
                snapshot.downloadHistory
        )
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
                        (1024.0 * 1024.0 * 1024.0)
                )

            value >= 1024L * 1024L ->
                String.format(
                    Locale.US,
                    "%.2f MB",
                    value.toDouble() /
                        (1024.0 * 1024.0)
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

    private fun displayedProfileId(): String? {
        return VpnRuntimeManager
            .getSessionProfileId()
            ?: activeProfileStore
                .getActiveProfileId()
    }

    private fun renderActiveProfile() {
        val root = view ?: return

        val name = root.findViewById<TextView>(
            R.id.active_profile_name
        )

        val detail = root.findViewById<TextView>(
            R.id.active_profile_detail
        )

        val displayedId =
            displayedProfileId()

        if (displayedId == null) {
            renderEmpty(
                name = name,
                detail = detail
            )
            return
        }

        val profile =
            repository.findProfile(
                displayedId
            )

        if (profile == null) {
            if (
                VpnRuntimeManager
                    .getSessionProfileId() == null &&
                activeProfileStore
                    .getActiveProfileId() == displayedId
            ) {
                activeProfileStore
                    .clearActiveProfile()
            }

            renderEmpty(
                name = name,
                detail = detail
            )
            return
        }

        renderProfile(
            profile = profile,
            name = name,
            detail = detail
        )

        renderRuntimeDetails()
    }

    private fun renderProfile(
        profile: ServerProfile,
        name: TextView,
        detail: TextView
    ) {
        name.text = profile.remarks

        detail.text =
            protocolLabel(
                profile.protocol
            )

        val avatar =
            view?.findViewById<TextView>(
                R.id.active_profile_avatar
            )

        avatar?.apply {
            visibility = View.VISIBLE

            text =
                profile.remarks
                    .trim()
                    .firstOrNull()
                    ?.uppercaseChar()
                    ?.toString()
                    ?: "?"

            setTypeface(
                typeface,
                Typeface.BOLD
            )

            setTextColor(
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr
                        .colorOnPrimary
                )
            )

            val avatarColor =
                MaterialColors.getColor(
                    this,
                    androidx.appcompat.R.attr
                        .colorPrimary
                )

            background =
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(avatarColor)
                }
        }

        renderActiveProfileLatency()
    }

    private fun renderEmpty(
        name: TextView,
        detail: TextView
    ) {
        view?.findViewById<TextView>(
            R.id.active_profile_avatar
        )?.visibility = View.GONE

        name.setText(
            R.string.no_active_profile
        )

        detail.setText(
            R.string.add_profile_hint
        )
    }

    private fun renderVpnHealthDashboard() {
        val root = view ?: return
        val vpnState = VpnRuntimeManager.getState()
        val connected = vpnState is VpnRuntimeState.Connected

        val networkValue = root.findViewById<TextView>(
            R.id.home_health_network_value
        )
        val networkIcon = root.findViewById<ImageView>(
            R.id.home_health_network_icon
        )
        val coreValue = root.findViewById<TextView>(
            R.id.home_health_core_value
        )
        val vpnValue = root.findViewById<TextView>(
            R.id.home_health_vpn_value
        )

        val transport = if (connected) {
            XelvraVpnService.getPhysicalNetworkSnapshot()
        } else {
            "UNAVAILABLE"
        }

        when (transport) {
            "WIFI" -> {
                networkValue.setText(R.string.home_health_wifi)
                networkIcon.setImageResource(R.drawable.ic_health_wifi)
            }
            "CELLULAR" -> {
                networkValue.setText(R.string.home_health_cellular)
                networkIcon.setImageResource(R.drawable.ic_health_cellular)
            }
            "OTHER" -> {
                networkValue.setText(R.string.home_health_other)
                networkIcon.setImageResource(R.drawable.ic_health_network)
            }
            "UNKNOWN" -> {
                networkValue.setText(R.string.home_health_detecting)
                networkIcon.setImageResource(R.drawable.ic_health_network)
            }
            else -> {
                networkValue.setText(R.string.home_quality_unavailable)
                networkIcon.setImageResource(R.drawable.ic_health_network)
            }
        }

        coreValue.setText(
            if (connected && XrayRuntimeController.isRunning()) {
                R.string.home_health_running
            } else {
                R.string.home_health_inactive
            }
        )

        val vpnLabel = when (vpnState) {
            is VpnRuntimeState.Connected ->
                R.string.status_connected
            VpnRuntimeState.Preparing ->
                R.string.status_preparing
            VpnRuntimeState.Connecting ->
                R.string.status_connecting
            VpnRuntimeState.WaitingForNetwork ->
                R.string.status_waiting
            VpnRuntimeState.Reconnecting ->
                R.string.home_health_reconnecting
            VpnRuntimeState.Disconnecting ->
                R.string.status_stopping
            is VpnRuntimeState.Failed ->
                R.string.status_failed
            VpnRuntimeState.Disconnected ->
                R.string.status_disconnected
        }

        vpnValue.setText(vpnLabel)
    }

    private fun renderXrayQuality() {
        val root = view ?: return

        val value = root.findViewById<TextView>(
            R.id.home_quality_value
        )

        val status = root.findViewById<TextView>(
            R.id.home_quality_status
        )

        val neutral = MaterialColors.getColor(
            value,
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )

        val state = VpnRuntimeManager.getState()

        if (state !is VpnRuntimeState.Connected) {
            value.text = "--"
            status.setText(R.string.home_quality_unavailable)
            value.setTextColor(neutral)
            return
        }

        when (val quality = DelayMonitor.getQualityState()) {
            is DelayQualityState.NotAvailable -> {
                value.text = "--"
                status.setText(R.string.home_quality_unavailable)
                value.setTextColor(neutral)
            }

            is DelayQualityState.Measuring -> {
                value.text = "--"
                status.setText(R.string.home_quality_measuring)
                value.setTextColor(neutral)
            }

            is DelayQualityState.Timeout -> {
                value.text = "--"
                status.setText(R.string.home_quality_timeout)
                value.setTextColor(neutral)
            }

            is DelayQualityState.Failed -> {
                value.text = "--"
                status.setText(R.string.home_quality_failed)
                value.setTextColor(neutral)
            }

            is DelayQualityState.Success -> {
                val age = System.currentTimeMillis() -
                    quality.measuredAt

                if (age < 0L || age > 120_000L) {
                    value.text = "--"
                    status.setText(R.string.home_quality_unavailable)
                    value.setTextColor(neutral)
                    return
                }

                val delay = quality.milliseconds
                value.text = "${delay} ms"

                val color = when {
                    delay < 100L -> {
                        status.setText(R.string.home_quality_excellent)
                        android.graphics.Color.rgb(46, 160, 67)
                    }

                    delay < 200L -> {
                        status.setText(R.string.home_quality_good)
                        android.graphics.Color.rgb(190, 140, 0)
                    }

                    delay < 400L -> {
                        status.setText(R.string.home_quality_fair)
                        android.graphics.Color.rgb(220, 115, 25)
                    }

                    else -> {
                        status.setText(R.string.home_quality_poor)
                        android.graphics.Color.rgb(210, 55, 55)
                    }
                }

                value.setTextColor(color)
            }
        }
    }

    private fun renderActiveProfileLatency() {
        if (
            !::repository.isInitialized ||
            !::activeProfileStore.isInitialized
        ) {
            return
        }

        val root = view ?: return

        val latencyView =
            root.findViewById<TextView>(
                R.id.active_profile_ping
            )

        val displayedId =
            displayedProfileId()
                ?: run {
                    latencyView.text = "--"
                    return
                }

        val profile =
            repository.findProfile(
                displayedId
            )
                ?: run {
                    latencyView.text = "--"
                    return
                }

        val state =
            EndpointLatencyManager.getState(
                EndpointKey.from(
                    address = profile.address,
                    port = profile.port
                )
            )

        latencyView.text =
            when (state) {
                EndpointLatencyState.NotTested ->
                    getString(R.string.latency_not_tested)

                EndpointLatencyState.Testing ->
                    getString(
                        R.string.latency_testing
                    )

                is EndpointLatencyState.Success ->
                    "${state.milliseconds} ms"

                is EndpointLatencyState.Timeout ->
                    getString(
                        R.string.latency_timeout
                    )

                is EndpointLatencyState.Failed ->
                    getString(
                        R.string.latency_failed
                    )
            }
    }

    private fun protocolLabel(
        protocol: Protocol
    ): String {
        return when (protocol) {
            Protocol.VMESS -> "VMess"
            Protocol.VLESS -> "Vless"
            Protocol.TROJAN -> "Trojan"
            Protocol.SHADOWSOCKS -> "Shadowsocks"
        }
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_PREFS =
            "xelvra_notification_permission"

        private const val PREF_NOTIFICATION_DISCLOSURE_SEEN =
            "notification_disclosure_seen"

        private const val PREF_NOTIFICATION_PERMISSION_REQUESTED =
            "notification_permission_requested"
    }

}
