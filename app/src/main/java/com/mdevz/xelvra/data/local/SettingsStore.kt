package com.mdevz.xelvra.data.local

import android.content.Context

enum class AppTheme {
    SYSTEM,
    LIGHT,
    DARK
}

enum class RoutingMode(
    val displayName: String,
    val requiresGeoAssets: Boolean
) {
    GLOBAL(
        "Global",
        false
    ),
    BYPASS_PRIVATE(
        "Bypass Private",
        true
    ),
    BLOCK_ADS(
        "Block Ads",
        true
    )
}

enum class DomainStrategy(
    val xrayValue: String
) {
    AS_IS("AsIs"),
    IP_IF_NON_MATCH("IPIfNonMatch"),
    IP_ON_DEMAND("IPOnDemand")
}

enum class PerAppVpnMode {
    ALL_APPS,
    SELECTED_APPS,
    BYPASS_SELECTED
}

enum class BypassLanMode {
    FOLLOW_CONFIG,
    BYPASS,
    NOT_BYPASS
}

class SettingsStore(
    context: Context
) {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    fun getPerAppVpnMode(): PerAppVpnMode {
        val raw = preferences.getString(
            KEY_PER_APP_VPN_MODE,
            PerAppVpnMode.ALL_APPS.name
        )

        return runCatching {
            PerAppVpnMode.valueOf(
                raw ?: PerAppVpnMode.ALL_APPS.name
            )
        }.getOrDefault(
            PerAppVpnMode.ALL_APPS
        )
    }

    fun setPerAppVpnMode(
        mode: PerAppVpnMode
    ) {
        preferences.edit()
            .putString(
                KEY_PER_APP_VPN_MODE,
                mode.name
            )
            .apply()
    }

    fun getPerAppVpnPackages(): Set<String> {
        return preferences.getStringSet(
            KEY_PER_APP_VPN_PACKAGES,
            emptySet()
        )?.toSet() ?: emptySet()
    }

    fun setPerAppVpnPackages(
        packages: Set<String>
    ) {
        preferences.edit()
            .putStringSet(
                KEY_PER_APP_VPN_PACKAGES,
                packages.toSet()
            )
            .apply()
    }

    fun getTheme(): AppTheme {
        val raw = preferences.getString(
            KEY_THEME,
            AppTheme.SYSTEM.name
        )

        return runCatching {
            AppTheme.valueOf(
                raw ?: AppTheme.SYSTEM.name
            )
        }.getOrDefault(
            AppTheme.SYSTEM
        )
    }

    fun setTheme(theme: AppTheme) {
        preferences.edit()
            .putString(
                KEY_THEME,
                theme.name
            )
            .apply()
    }

    fun getBypassLanMode(): BypassLanMode {
        val stored =
            preferences.getString(
                KEY_BYPASS_LAN_MODE,
                null
            )

        if (stored != null) {
            return runCatching {
                BypassLanMode.valueOf(stored)
            }.getOrDefault(
                BypassLanMode.BYPASS
            )
        }

        val migrated =
            if (preferences.contains(KEY_BYPASS_LAN)) {
                if (
                    preferences.getBoolean(
                        KEY_BYPASS_LAN,
                        false
                    )
                ) {
                    BypassLanMode.BYPASS
                } else {
                    BypassLanMode.NOT_BYPASS
                }
            } else {
                BypassLanMode.BYPASS
            }

        preferences.edit()
            .putString(
                KEY_BYPASS_LAN_MODE,
                migrated.name
            )
            .remove(KEY_BYPASS_LAN)
            .apply()

        return migrated
    }

    fun setBypassLanMode(
        mode: BypassLanMode
    ) {
        preferences.edit()
            .putString(
                KEY_BYPASS_LAN_MODE,
                mode.name
            )
            .remove(KEY_BYPASS_LAN)
            .apply()
    }

    fun resolveBypassLan(
        routingMode: RoutingMode =
            getRoutingMode()
    ): Boolean {
        return when (getBypassLanMode()) {
            BypassLanMode.FOLLOW_CONFIG ->
                routingMode ==
                    RoutingMode.BYPASS_PRIVATE

            BypassLanMode.BYPASS ->
                true

            BypassLanMode.NOT_BYPASS ->
                false
        }
    }

    fun isIpv6Enabled(): Boolean {
        return preferences.getBoolean(
            KEY_IPV6,
            false
        )
    }

    fun setIpv6Enabled(
        enabled: Boolean
    ) {
        preferences.edit()
            .putBoolean(
                KEY_IPV6,
                enabled
            )
            .apply()
    }

    fun getRoutingMode(): RoutingMode {
        val raw =
            preferences.getString(
                KEY_ROUTING_MODE,
                RoutingMode.GLOBAL.name
            )

        return runCatching {
            RoutingMode.valueOf(
                raw ?: RoutingMode.GLOBAL.name
            )
        }.getOrDefault(
            RoutingMode.GLOBAL
        )
    }

    fun setRoutingMode(
        mode: RoutingMode
    ) {
        preferences.edit()
            .putString(
                KEY_ROUTING_MODE,
                mode.name
            )
            .apply()
    }

    fun getDomainStrategy(): DomainStrategy {
        val raw =
            preferences.getString(
                KEY_DOMAIN_STRATEGY,
                DomainStrategy.AS_IS.name
            )

        return runCatching {
            DomainStrategy.valueOf(
                raw ?: DomainStrategy.AS_IS.name
            )
        }.getOrDefault(
            DomainStrategy.AS_IS
        )
    }

    fun setDomainStrategy(
        strategy: DomainStrategy
    ) {
        preferences.edit()
            .putString(
                KEY_DOMAIN_STRATEGY,
                strategy.name
            )
            .apply()
    }

    fun isTestUrlEnabled(): Boolean {
        return preferences.getBoolean(
            KEY_TEST_URL_ENABLED,
            true
        )
    }

    fun setTestUrlEnabled(
        enabled: Boolean
    ) {
        preferences.edit()
            .putBoolean(
                KEY_TEST_URL_ENABLED,
                enabled
            )
            .apply()
    }

    fun getTestUrl(): String {
        return preferences.getString(
            KEY_TEST_URL,
            DEFAULT_TEST_URL
        ) ?: DEFAULT_TEST_URL
    }

    fun setTestUrl(
        value: String
    ) {
        preferences.edit()
            .putString(
                KEY_TEST_URL,
                value.trim()
            )
            .apply()
    }

    fun getTestUrlIntervalSeconds(): Int {
        return preferences.getInt(
            KEY_TEST_URL_INTERVAL_SECONDS,
            DEFAULT_TEST_URL_INTERVAL_SECONDS
        ).coerceIn(
            MIN_TEST_URL_INTERVAL_SECONDS,
            MAX_TEST_URL_INTERVAL_SECONDS
        )
    }

    fun setTestUrlIntervalSeconds(
        seconds: Int
    ) {
        require(
            seconds in
                MIN_TEST_URL_INTERVAL_SECONDS..
                    MAX_TEST_URL_INTERVAL_SECONDS
        ) {
            "Test URL interval must be between " +
                "$MIN_TEST_URL_INTERVAL_SECONDS and " +
                "$MAX_TEST_URL_INTERVAL_SECONDS seconds"
        }

        preferences.edit()
            .putInt(
                KEY_TEST_URL_INTERVAL_SECONDS,
                seconds
            )
            .apply()
    }

    fun getConnectionTimeoutSeconds(): Int {
        return preferences.getInt(
            KEY_CONNECTION_TIMEOUT_SECONDS,
            DEFAULT_CONNECTION_TIMEOUT_SECONDS
        ).coerceIn(
            MIN_CONNECTION_TIMEOUT_SECONDS,
            MAX_CONNECTION_TIMEOUT_SECONDS
        )
    }

    fun setConnectionTimeoutSeconds(
        seconds: Int
    ) {
        require(
            seconds in
                MIN_CONNECTION_TIMEOUT_SECONDS..
                    MAX_CONNECTION_TIMEOUT_SECONDS
        ) {
            "Timeout must be between " +
                "$MIN_CONNECTION_TIMEOUT_SECONDS and " +
                "$MAX_CONNECTION_TIMEOUT_SECONDS seconds"
        }

        preferences.edit()
            .putInt(
                KEY_CONNECTION_TIMEOUT_SECONDS,
                seconds
            )
            .apply()
    }

    fun isAutoReconnectEnabled(): Boolean {
        return preferences.getBoolean(
            KEY_AUTO_RECONNECT,
            true
        )
    }

    fun setAutoReconnectEnabled(
        enabled: Boolean
    ) {
        preferences.edit()
            .putBoolean(
                KEY_AUTO_RECONNECT,
                enabled
            )
            .apply()
    }

    fun isAutoConnectEnabled(): Boolean {
        return preferences.getBoolean(
            KEY_AUTO_CONNECT,
            false
        )
    }

    fun setAutoConnectEnabled(
        enabled: Boolean
    ) {
        preferences.edit()
            .putBoolean(
                KEY_AUTO_CONNECT,
                enabled
            )
            .apply()
    }

    fun isFragmentEnabled(): Boolean {
        return preferences.getBoolean(
            KEY_FRAGMENT,
            false
        )
    }

    fun setFragmentEnabled(
        enabled: Boolean
    ) {
        preferences.edit()
            .putBoolean(
                KEY_FRAGMENT,
                enabled
            )
            .apply()
    }

    fun getFragmentPackets(): String {
        val value =
            preferences.getString(
                KEY_FRAGMENT_PACKETS,
                DEFAULT_FRAGMENT_PACKETS
            ) ?: DEFAULT_FRAGMENT_PACKETS

        return value.takeIf {
            it in FRAGMENT_PACKET_PRESETS
        } ?: DEFAULT_FRAGMENT_PACKETS
    }

    fun setFragmentPackets(
        value: String
    ) {
        val normalized = value.trim()

        require(
            normalized in FRAGMENT_PACKET_PRESETS
        ) {
            "Unsupported Fragment packets value"
        }

        preferences.edit()
            .putString(
                KEY_FRAGMENT_PACKETS,
                normalized
            )
            .apply()
    }

    fun getFragmentMaxSplit(): String {
        return preferences.getString(
            KEY_FRAGMENT_MAX_SPLIT,
            DEFAULT_FRAGMENT_MAX_SPLIT
        ) ?: DEFAULT_FRAGMENT_MAX_SPLIT
    }

    fun setFragmentMaxSplit(
        value: String
    ) {
        val normalized = value.trim()

        require(
            isValidFragmentRangeValue(
                normalized
            )
        ) {
            "Fragment max split must be a positive number or ascending range"
        }

        preferences.edit()
            .putString(
                KEY_FRAGMENT_MAX_SPLIT,
                normalized
            )
            .apply()
    }

    fun getFragmentLength(): String {
        return preferences.getString(
            KEY_FRAGMENT_LENGTH,
            DEFAULT_FRAGMENT_LENGTH
        ) ?: DEFAULT_FRAGMENT_LENGTH
    }

    fun setFragmentLength(
        value: String
    ) {
        preferences.edit()
            .putString(
                KEY_FRAGMENT_LENGTH,
                value.trim()
            )
            .apply()
    }

    fun getFragmentInterval(): String {
        return preferences.getString(
            KEY_FRAGMENT_INTERVAL,
            DEFAULT_FRAGMENT_INTERVAL
        ) ?: DEFAULT_FRAGMENT_INTERVAL
    }

    fun setFragmentInterval(
        value: String
    ) {
        preferences.edit()
            .putString(
                KEY_FRAGMENT_INTERVAL,
                value.trim()
            )
            .apply()
    }

    fun isMuxEnabled(): Boolean {
        return preferences.getBoolean(
            KEY_MUX,
            false
        )
    }

    fun setMuxEnabled(
        enabled: Boolean
    ) {
        preferences.edit()
            .putBoolean(
                KEY_MUX,
                enabled
            )
            .apply()
    }

    fun getMuxConcurrency(): Int {
        return preferences.getInt(
            KEY_MUX_CONCURRENCY,
            DEFAULT_MUX_CONCURRENCY
        ).coerceIn(
            MIN_MUX_CONCURRENCY,
            MAX_MUX_CONCURRENCY
        )
    }

    fun setMuxConcurrency(
        value: Int
    ) {
        require(
            value in
                MIN_MUX_CONCURRENCY..
                    MAX_MUX_CONCURRENCY
        ) {
            "Mux concurrency must be between " +
                "$MIN_MUX_CONCURRENCY and " +
                "$MAX_MUX_CONCURRENCY"
        }

        preferences.edit()
            .putInt(
                KEY_MUX_CONCURRENCY,
                value
            )
            .apply()
    }

    fun getMuxXudpConcurrency(): Int {
        return preferences.getInt(
            KEY_MUX_XUDP_CONCURRENCY,
            DEFAULT_MUX_XUDP_CONCURRENCY
        ).coerceIn(
            MIN_MUX_CONCURRENCY,
            MAX_MUX_CONCURRENCY
        )
    }

    fun setMuxXudpConcurrency(
        value: Int
    ) {
        require(
            value in
                MIN_MUX_CONCURRENCY..
                    MAX_MUX_CONCURRENCY
        ) {
            "Mux XUDP concurrency must be between " +
                "$MIN_MUX_CONCURRENCY and " +
                "$MAX_MUX_CONCURRENCY"
        }

        preferences.edit()
            .putInt(
                KEY_MUX_XUDP_CONCURRENCY,
                value
            )
            .apply()
    }

    fun getMuxUdp443Mode(): String {
        val value =
            preferences.getString(
                KEY_MUX_UDP_443_MODE,
                DEFAULT_MUX_UDP_443_MODE
            ) ?: DEFAULT_MUX_UDP_443_MODE

        return value.takeIf {
            it in MUX_UDP_443_MODES
        } ?: DEFAULT_MUX_UDP_443_MODE
    }

    fun setMuxUdp443Mode(
        value: String
    ) {
        val normalized =
            value.trim().lowercase()

        require(
            normalized in MUX_UDP_443_MODES
        ) {
            "Unsupported Mux UDP 443 mode"
        }

        preferences.edit()
            .putString(
                KEY_MUX_UDP_443_MODE,
                normalized
            )
            .apply()
    }

    fun isSniffingEnabled(): Boolean {
        return preferences.getBoolean(
            KEY_SNIFFING,
            true
        )
    }

    fun setSniffingEnabled(
        enabled: Boolean
    ) {
        preferences.edit()
            .putBoolean(
                KEY_SNIFFING,
                enabled
            )
            .apply()
    }

    fun isSniffingRouteOnly(): Boolean {
        return preferences.getBoolean(
            KEY_SNIFFING_ROUTE_ONLY,
            false
        )
    }

    fun setSniffingRouteOnly(
        enabled: Boolean
    ) {
        preferences.edit()
            .putBoolean(
                KEY_SNIFFING_ROUTE_ONLY,
                enabled
            )
            .apply()
    }

    fun isSniffingHttpEnabled(): Boolean {
        return preferences.getBoolean(
            KEY_SNIFFING_HTTP,
            true
        )
    }

    fun setSniffingHttpEnabled(
        enabled: Boolean
    ) {
        preferences.edit()
            .putBoolean(
                KEY_SNIFFING_HTTP,
                enabled
            )
            .apply()
    }

    fun isSniffingTlsEnabled(): Boolean {
        return preferences.getBoolean(
            KEY_SNIFFING_TLS,
            true
        )
    }

    fun setSniffingTlsEnabled(
        enabled: Boolean
    ) {
        preferences.edit()
            .putBoolean(
                KEY_SNIFFING_TLS,
                enabled
            )
            .apply()
    }

    fun isSniffingQuicEnabled(): Boolean {
        return preferences.getBoolean(
            KEY_SNIFFING_QUIC,
            true
        )
    }

    fun setSniffingQuicEnabled(
        enabled: Boolean
    ) {
        preferences.edit()
            .putBoolean(
                KEY_SNIFFING_QUIC,
                enabled
            )
            .apply()
    }

    fun isFakeDnsEnabled(): Boolean {
        return preferences.getBoolean(
            KEY_FAKE_DNS,
            false
        )
    }

    fun setFakeDnsEnabled(
        enabled: Boolean
    ) {
        preferences.edit()
            .putBoolean(
                KEY_FAKE_DNS,
                enabled
            )
            .apply()
    }

    fun getDirectDns(): String {
        return preferences.getString(
            KEY_DIRECT_DNS,
            DEFAULT_DIRECT_DNS
        )
            ?.trim()
            ?.takeIf {
                it.isNotEmpty()
            }
            ?: DEFAULT_DIRECT_DNS
    }

    fun setDirectDns(
        value: String
    ) {
        val normalized =
            value.trim()

        require(
            normalized.isNotEmpty()
        ) {
            "Direct DNS must not be empty"
        }

        preferences.edit()
            .putString(
                KEY_DIRECT_DNS,
                normalized
            )
            .apply()
    }

    fun isRemoteDnsEnabled(): Boolean {
        return preferences.getBoolean(
            KEY_REMOTE_DNS_ENABLED,
            true
        )
    }

    fun setRemoteDnsEnabled(
        enabled: Boolean
    ) {
        preferences.edit()
            .putBoolean(
                KEY_REMOTE_DNS_ENABLED,
                enabled
            )
            .apply()
    }

    fun getRemoteDns(): String {
        return preferences.getString(
            KEY_REMOTE_DNS,
            DEFAULT_REMOTE_DNS
        )
            ?.trim()
            ?.takeIf {
                it.isNotEmpty()
            }
            ?: DEFAULT_REMOTE_DNS
    }

    fun setRemoteDns(
        value: String
    ) {
        val normalized =
            value.trim()

        require(
            normalized.isNotEmpty()
        ) {
            "Remote DNS must not be empty"
        }

        preferences.edit()
            .putString(
                KEY_REMOTE_DNS,
                normalized
            )
            .apply()
    }

    fun hasAcceptedVpnDisclosure(): Boolean {
        return preferences.getBoolean(
            KEY_VPN_DISCLOSURE_ACCEPTED,
            false
        )
    }

    fun setVpnDisclosureAccepted(
        accepted: Boolean
    ) {
        preferences.edit()
            .putBoolean(
                KEY_VPN_DISCLOSURE_ACCEPTED,
                accepted
            )
            .apply()
    }

    fun getVpnInterfaceAddressIndex(): Int {
        return preferences.getInt(
            KEY_VPN_INTERFACE_ADDRESS_INDEX,
            DEFAULT_VPN_INTERFACE_ADDRESS_INDEX
        ).coerceIn(
            0,
            VPN_INTERFACE_ADDRESSES.lastIndex
        )
    }

    fun setVpnInterfaceAddressIndex(
        index: Int
    ) {
        require(index in VPN_INTERFACE_ADDRESSES.indices) {
            "Invalid VPN interface address index"
        }

        preferences.edit()
            .putInt(
                KEY_VPN_INTERFACE_ADDRESS_INDEX,
                index
            )
            .apply()
    }

    fun getVpnInterfaceAddress(): String {
        return VPN_INTERFACE_ADDRESSES[
            getVpnInterfaceAddressIndex()
        ]
    }

    fun getMtu(): Int {
        return preferences.getInt(
            KEY_MTU,
            DEFAULT_MTU
        ).coerceIn(
            MIN_MTU,
            MAX_MTU
        )
    }

    fun setMtu(mtu: Int) {
        require(mtu in MIN_MTU..MAX_MTU) {
            "MTU must be between $MIN_MTU and $MAX_MTU"
        }

        preferences.edit()
            .putInt(
                KEY_MTU,
                mtu
            )
            .apply()
    }

    companion object {
        private const val PREFS_NAME =
            "xelvra_settings"

        private const val KEY_PER_APP_VPN_MODE =
            "per_app_vpn_mode"

        private const val KEY_PER_APP_VPN_PACKAGES =
            "per_app_vpn_packages"

        private const val KEY_THEME =
            "theme"

        private const val KEY_VPN_DISCLOSURE_ACCEPTED =
            "vpn_disclosure_accepted"

        private const val KEY_BYPASS_LAN =
            "bypass_lan"

        private const val KEY_BYPASS_LAN_MODE =
            "bypass_lan_mode"

        private const val KEY_IPV6 =
            "ipv6_enabled"

        private const val KEY_MTU =
            "mtu"

        private const val KEY_VPN_INTERFACE_ADDRESS_INDEX =
            "vpn_interface_address_index"

        const val DEFAULT_VPN_INTERFACE_ADDRESS_INDEX =
            0

        val VPN_INTERFACE_ADDRESS_LABELS =
            listOf(
                "10.10.14.x",
                "10.1.0.x",
                "10.0.0.x",
                "172.31.0.x",
                "172.20.0.x",
                "172.16.0.x",
                "192.168.100.x"
            )

        val VPN_INTERFACE_ADDRESSES =
            listOf(
                "10.10.14.1",
                "10.1.0.1",
                "10.0.0.1",
                "172.31.0.1",
                "172.20.0.1",
                "172.16.0.1",
                "192.168.100.1"
            )

        private const val KEY_REMOTE_DNS_ENABLED =
            "remote_dns_enabled"

        private const val KEY_REMOTE_DNS =
            "remote_dns"

        private const val KEY_DIRECT_DNS =
            "direct_dns"

        private const val KEY_FAKE_DNS =
            "fake_dns"

        private const val KEY_TEST_URL_ENABLED =
            "test_url_enabled"

        private const val KEY_TEST_URL =
            "test_url"

        const val DEFAULT_TEST_URL =
            "https://www.google.com/generate_204"

        private const val KEY_TEST_URL_INTERVAL_SECONDS =
            "test_url_interval_seconds"

        const val DEFAULT_TEST_URL_INTERVAL_SECONDS =
            30

        const val MIN_TEST_URL_INTERVAL_SECONDS =
            5

        const val MAX_TEST_URL_INTERVAL_SECONDS =
            3600

        private const val KEY_CONNECTION_TIMEOUT_SECONDS =
            "connection_timeout_seconds"

        const val DEFAULT_CONNECTION_TIMEOUT_SECONDS =
            10

        const val MIN_CONNECTION_TIMEOUT_SECONDS =
            1

        const val MAX_CONNECTION_TIMEOUT_SECONDS =
            60

        private const val KEY_AUTO_CONNECT =
            "auto_connect_enabled"

        private const val KEY_AUTO_RECONNECT =
            "auto_reconnect_enabled"

        private const val KEY_FRAGMENT =
            "fragment_enabled"

        private const val KEY_FRAGMENT_PACKETS =
            "fragment_packets"

        private const val KEY_FRAGMENT_LENGTH =
            "fragment_length"

        private const val KEY_FRAGMENT_INTERVAL =
            "fragment_interval"

        private const val KEY_FRAGMENT_MAX_SPLIT =
            "fragment_max_split"

        const val DEFAULT_FRAGMENT_PACKETS =
            "tlshello"

        const val DEFAULT_FRAGMENT_LENGTH =
            "100-200"

        const val DEFAULT_FRAGMENT_INTERVAL =
            "10-20"


        const val DEFAULT_FRAGMENT_MAX_SPLIT =
            "10"

        val FRAGMENT_PACKET_PRESETS =
            listOf(
                "tlshello",
                "1-1",
                "1-2",
                "1-3",
                "1-5"
            )

        private fun isValidFragmentRangeValue(
            value: String
        ): Boolean {
            val parts =
                value.trim().split("-")

            if (parts.size !in 1..2) {
                return false
            }

            val numbers =
                parts.map {
                    it.trim().toIntOrNull()
                        ?: return false
                }

            if (numbers.any { it <= 0 }) {
                return false
            }

            return numbers.size == 1 ||
                numbers[0] <= numbers[1]
        }

        private const val KEY_MUX =
            "mux_enabled"


        private const val KEY_MUX_CONCURRENCY =
            "mux_concurrency"

        private const val KEY_MUX_XUDP_CONCURRENCY =
            "mux_xudp_concurrency"

        private const val KEY_MUX_UDP_443_MODE =
            "mux_udp_443_mode"

        const val DEFAULT_MUX_CONCURRENCY =
            8

        const val DEFAULT_MUX_XUDP_CONCURRENCY =
            8

        const val MIN_MUX_CONCURRENCY =
            -1

        const val MAX_MUX_CONCURRENCY =
            1024

        const val DEFAULT_MUX_UDP_443_MODE =
            "reject"

        val MUX_UDP_443_MODES =
            listOf(
                "reject",
                "allow",
                "skip"
            )

        private const val KEY_SNIFFING =
            "sniffing_enabled"

        private const val KEY_SNIFFING_ROUTE_ONLY =
            "sniffing_route_only"

        private const val KEY_SNIFFING_HTTP =
            "sniffing_http"

        private const val KEY_SNIFFING_TLS =
            "sniffing_tls"

        private const val KEY_SNIFFING_QUIC =
            "sniffing_quic"

        private const val KEY_DOMAIN_STRATEGY =
            "domain_strategy"

        private const val KEY_ROUTING_MODE =
            "routing_mode"

        const val DEFAULT_REMOTE_DNS =
            "https://cloudflare-dns.com/dns-query"

        const val DEFAULT_DIRECT_DNS =
            "System"

        const val DEFAULT_MTU =
            1500

        const val MIN_MTU =
            576

        const val MAX_MTU =
            9000
    }
}
