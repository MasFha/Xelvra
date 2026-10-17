package com.mdevz.xelvra.core.xray.config

import com.mdevz.xelvra.data.local.RoutingMode

import com.mdevz.xelvra.data.model.Protocol
import com.mdevz.xelvra.data.model.ProtocolOptions
import com.mdevz.xelvra.data.model.Security
import com.mdevz.xelvra.data.model.ServerProfile
import com.mdevz.xelvra.data.model.Transport
import org.json.JSONArray
import org.json.JSONObject

object XrayConfigBuilder {

    enum class InboundMode {
        SOCKS,
        TUN
    }

    fun build(
        profile: ServerProfile,
        inboundMode: InboundMode = InboundMode.SOCKS,
        mtu: Int = 1500,
        remoteDns: String = DEFAULT_REMOTE_DNS,
        directDns: String = DEFAULT_DIRECT_DNS,
        muxEnabled: Boolean = false,
        muxConcurrency: Int = 8,
        muxXudpConcurrency: Int = 8,
        muxUdp443Mode: String = "reject",
        fragmentEnabled: Boolean = false,
        fragmentPackets: String = "tlshello",
        fragmentLength: String = "100-200",
        fragmentInterval: String = "10-20",
        fragmentMaxSplit: String = "10",
        sniffingEnabled: Boolean = true,
        sniffingRouteOnly: Boolean = false,
        sniffingHttpEnabled: Boolean = true,
        sniffingTlsEnabled: Boolean = true,
        sniffingQuicEnabled: Boolean = true,
        fakeDnsEnabled: Boolean = false,
        domainStrategy: String = "AsIs",
        routingMode: RoutingMode = RoutingMode.GLOBAL,
        smartRoutingRules: JSONArray = JSONArray()
    ): String {
        validateModel(profile)

        require(mtu in 576..9000) {
            "MTU must be between 576 and 9000"
        }


        require(muxConcurrency in -1..1024) {
            "Mux concurrency must be between -1 and 1024"
        }

        require(muxXudpConcurrency in -1..1024) {
            "Mux XUDP concurrency must be between -1 and 1024"
        }

        require(
            muxUdp443Mode in
                setOf(
                    "reject",
                    "allow",
                    "skip"
                )
        ) {
            "Unsupported Mux UDP 443 mode"
        }

        require(remoteDns.isNotBlank()) {
            "Remote DNS must not be blank"
        }

        require(directDns.isNotBlank()) {
            "Direct DNS must not be blank"
        }

        require(
            domainStrategy in VALID_DOMAIN_STRATEGIES
        ) {
            "Unsupported domain strategy: $domainStrategy"
        }

        val root = JSONObject()

        root.put(
            "log",
            JSONObject()
                .put("loglevel", "debug")
        )

        val sniffing =
            buildSniffing(
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
                    fakeDnsEnabled
            )

        val inbound = when (inboundMode) {
            InboundMode.SOCKS ->
                buildSocksInbound()
                    .put(
                        "sniffing",
                        sniffing
                    )

            InboundMode.TUN ->
                buildTunInbound(mtu)
                    .put(
                        "sniffing",
                        sniffing
                    )
        }

        root.put(
            "inbounds",
            JSONArray().put(inbound)
        )

        val dnsServers =
            JSONArray()

        if (fakeDnsEnabled) {
            dnsServers.put(
                JSONObject()
                    .put(
                        "address",
                        "fakedns"
                    )
            )
        }

        dnsServers.put(
            remoteDns.trim()
        )

        val directDomains =
            when (routingMode) {
                RoutingMode.BYPASS_PRIVATE ->
                    listOf("geosite:private")

                RoutingMode.GLOBAL,
                RoutingMode.BLOCK_ADS ->
                    emptyList()
            }

        if (directDomains.isNotEmpty()) {
            val resolvedDirectDns =
                if (
                    directDns.equals(
                        "System",
                        ignoreCase = true
                    )
                ) {
                    DEFAULT_DIRECT_DNS
                } else {
                    directDns.trim()
                }

            dnsServers.put(
                JSONObject()
                    .put(
                        "address",
                        resolvedDirectDns
                    )
                    .put(
                        "domains",
                        JSONArray().apply {
                            directDomains.forEach(
                                this::put
                            )
                        }
                    )
                    .put(
                        "skipFallback",
                        true
                    )
                    .put(
                        "tag",
                        DIRECT_DNS_TAG
                    )
            )
        }

        root.put(
            "dns",
            JSONObject()
                .put(
                    "tag",
                    DNS_MODULE_TAG
                )
                .put(
                    "servers",
                    dnsServers
                )
        )

        if (fakeDnsEnabled) {
            root.put(
                "fakeDns",
                JSONArray().put(
                    JSONObject()
                        .put(
                            "ipPool",
                            FAKE_DNS_IPV4_POOL
                        )
                        .put(
                            "poolSize",
                            FAKE_DNS_POOL_SIZE
                        )
                )
            )
        }

        root.put(
            "outbounds",
            JSONArray()
                .put(
                    buildProxyOutbound(
                        profile = profile,
                        muxEnabled = muxEnabled,
                        muxConcurrency = muxConcurrency,
                        muxXudpConcurrency =
                            muxXudpConcurrency,
                        muxUdp443Mode =
                            muxUdp443Mode,
                        fragmentEnabled =
                            fragmentEnabled
                    )
                )
                .put(buildDirectOutbound())
                .apply {
                    if (fragmentEnabled) {
                        put(
                            buildFragmentOutbound(
                                packets = fragmentPackets,
                                length = fragmentLength,
                                interval = fragmentInterval,
                                maxSplit = fragmentMaxSplit
                            )
                        )
                    }
                }
                .put(buildBlockOutbound())
                .put(buildDnsOutbound())
        )

        val routingRules =
            JSONArray()

        if (inboundMode == InboundMode.TUN) {
            routingRules.put(
                JSONObject()
                    .put("type", "field")
                    .put(
                        "inboundTag",
                        JSONArray().put("tun")
                    )
                    .put("port", "53")
                    .put(
                        "outboundTag",
                        DNS_OUT_TAG
                    )
            )
        }

        if (directDomains.isNotEmpty()) {
            routingRules.put(
                JSONObject()
                    .put("type", "field")
                    .put(
                        "inboundTag",
                        JSONArray().put(
                            DIRECT_DNS_TAG
                        )
                    )
                    .put(
                        "outboundTag",
                        "direct"
                    )
            )
        }

        routingRules.put(
            JSONObject()
                .put("type", "field")
                .put(
                    "inboundTag",
                    JSONArray().put(
                        DNS_MODULE_TAG
                    )
                )
                .put(
                    "outboundTag",
                    "proxy"
                )
        )

        // Saved Smart Routing rules are applied after internal DNS rules.
        require(smartRoutingRules.length() <= 100) {
            "Too many Smart Routing rules"
        }

        for (index in 0 until smartRoutingRules.length()) {
            val rule = smartRoutingRules.getJSONObject(index)
            require(rule.optString("type") == "field") {
                "Invalid Smart Routing rule type"
            }
            require(
                rule.optString("outboundTag") in
                    setOf("proxy", "direct", "block")
            ) {
                "Invalid Smart Routing outbound"
            }
            require(
                rule.has("domain") xor rule.has("ip")
            ) {
                "Smart Routing rule must have domain or ip"
            }
            routingRules.put(JSONObject(rule.toString()))
        }

        when (routingMode) {
            RoutingMode.GLOBAL -> Unit

            RoutingMode.BYPASS_PRIVATE -> {
                routingRules.put(
                    JSONObject()
                        .put("type", "field")
                        .put(
                            "domain",
                            JSONArray()
                                .put("geosite:private")
                        )
                        .put(
                            "outboundTag",
                            "direct"
                        )
                )

                routingRules.put(
                    JSONObject()
                        .put("type", "field")
                        .put(
                            "ip",
                            JSONArray()
                                .put("geoip:private")
                        )
                        .put(
                            "outboundTag",
                            "direct"
                        )
                )
            }

            RoutingMode.BLOCK_ADS -> {
                routingRules.put(
                    JSONObject()
                        .put("type", "field")
                        .put(
                            "domain",
                            JSONArray()
                                .put(
                                    "geosite:category-ads-all"
                                )
                        )
                        .put(
                            "outboundTag",
                            "block"
                        )
                )
            }
        }

        root.put(
            "routing",
            JSONObject()
                .put(
                    "domainStrategy",
                    domainStrategy
                )
                .put("rules", routingRules)
        )

        return root.toString()
    }

    private fun buildSniffing(
        sniffingEnabled: Boolean,
        sniffingRouteOnly: Boolean,
        sniffingHttpEnabled: Boolean,
        sniffingTlsEnabled: Boolean,
        sniffingQuicEnabled: Boolean,
        fakeDnsEnabled: Boolean
    ): JSONObject {
        val destOverride =
            JSONArray()

        if (sniffingEnabled) {
            if (sniffingHttpEnabled) {
                destOverride.put("http")
            }

            if (sniffingTlsEnabled) {
                destOverride.put("tls")
            }

            if (sniffingQuicEnabled) {
                destOverride.put("quic")
            }
        }

        if (fakeDnsEnabled) {
            destOverride.put("fakedns")
        }

        return JSONObject()
            .put(
                "enabled",
                sniffingEnabled ||
                    fakeDnsEnabled
            )
            .put(
                "destOverride",
                destOverride
            )
            .put(
                "routeOnly",
                sniffingEnabled &&
                    sniffingRouteOnly
            )
    }

    private fun buildTunInbound(
        mtu: Int
    ): JSONObject {
        return JSONObject()
            .put("tag", "tun")
            .put("port", 0)
            .put("protocol", "tun")
            .put(
                "settings",
                JSONObject()
                    .put("name", "xelvra0")
                    .put("mtu", mtu)
            )
    }

    private fun buildSocksInbound(): JSONObject {
        return JSONObject()
            .put("tag", "socks")
            .put("listen", "127.0.0.1")
            .put("port", 10808)
            .put("protocol", "socks")
            .put(
                "settings",
                JSONObject()
                    .put("auth", "noauth")
                    .put("udp", true)
            )
    }

    private fun buildProxyOutbound(
        profile: ServerProfile,
        muxEnabled: Boolean,
        muxConcurrency: Int,
        muxXudpConcurrency: Int,
        muxUdp443Mode: String,
        fragmentEnabled: Boolean
    ): JSONObject {
        val effectiveMuxEnabled =
            muxEnabled &&
                profile.protocol != Protocol.TROJAN &&
                profile.protocol != Protocol.SHADOWSOCKS &&
                profile.transport !is Transport.XHttp

        val effectiveMuxConcurrency =
            if (
                effectiveMuxEnabled &&
                profile.protocol == Protocol.VLESS &&
                (profile.protocolOptions as? ProtocolOptions.VLess)
                    ?.flow?.isNotBlank() == true
            ) {
                -1
            } else if (effectiveMuxEnabled) {
                muxConcurrency
            } else {
                -1
            }

        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", protocolName(profile.protocol))
            .put("settings", buildProtocolSettings(profile))
            .put(
                "streamSettings",
                buildStreamSettings(
                    profile,
                    fragmentEnabled
                )
            )
            .put(
                "mux",
                JSONObject()
                    .put(
                        "enabled",
                        effectiveMuxEnabled
                    )
                    .put(
                        "concurrency",
                        effectiveMuxConcurrency
                    )
                    .put(
                        "xudpConcurrency",
                        muxXudpConcurrency
                    )
                    .put(
                        "xudpProxyUDP443",
                        muxUdp443Mode
                    )
            )
    }

    private fun buildProtocolSettings(
        profile: ServerProfile
    ): JSONObject {
        return when (val options = profile.protocolOptions) {
            is ProtocolOptions.VMess -> {
                require(profile.protocol == Protocol.VMESS) {
                    "VMess options do not match protocol"
                }

                val user = JSONObject()
                    .put("id", options.uuid)
                    .put("alterId", options.alterId)
                    .put("security", options.encryption)

                JSONObject().put(
                    "vnext",
                    JSONArray().put(
                        JSONObject()
                            .put("address", profile.address)
                            .put("port", profile.port)
                            .put("users", JSONArray().put(user))
                    )
                )
            }

            is ProtocolOptions.VLess -> {
                require(profile.protocol == Protocol.VLESS) {
                    "VLESS options do not match protocol"
                }

                val user = JSONObject()
                    .put("id", options.uuid)
                    .put("encryption", "none")

                if (options.flow.isNotBlank()) {
                    user.put("flow", options.flow)
                }

                JSONObject().put(
                    "vnext",
                    JSONArray().put(
                        JSONObject()
                            .put("address", profile.address)
                            .put("port", profile.port)
                            .put("users", JSONArray().put(user))
                    )
                )
            }

            is ProtocolOptions.Trojan -> {
                require(profile.protocol == Protocol.TROJAN) {
                    "Trojan options do not match protocol"
                }

                JSONObject().put(
                    "servers",
                    JSONArray().put(
                        JSONObject()
                            .put("address", profile.address)
                            .put("port", profile.port)
                            .put("password", options.password)
                    )
                )
            }

            is ProtocolOptions.Shadowsocks -> {
                require(profile.protocol == Protocol.SHADOWSOCKS) {
                    "Shadowsocks options do not match protocol"
                }

                JSONObject().put(
                    "servers",
                    JSONArray().put(
                        JSONObject()
                            .put("address", profile.address)
                            .put("port", profile.port)
                            .put("method", options.method)
                            .put("password", options.password)
                    )
                )
            }
        }
    }

    private fun buildStreamSettings(
        profile: ServerProfile,
        fragmentEnabled: Boolean
    ): JSONObject {
        val stream = JSONObject()

        when (val transport = profile.transport) {
            Transport.Tcp -> {
                stream.put("network", "tcp")
            }

            is Transport.WebSocket -> {
                stream.put("network", "ws")
                stream.put(
                    "wsSettings",
                    JSONObject().apply {
                        if (transport.host.isNotBlank()) {
                            put("host", transport.host)
                        }

                        if (transport.path.isNotBlank()) {
                            put("path", transport.path)
                        }
                    }
                )
            }

            is Transport.Grpc -> {
                stream.put("network", "grpc")
                stream.put(
                    "grpcSettings",
                    JSONObject().apply {
                        if (transport.serviceName.isNotBlank()) {
                            put(
                                "serviceName",
                                transport.serviceName
                            )
                        }
                    }
                )
            }

            is Transport.Http -> {
                throw IllegalArgumentException(
                    "HTTP transport has been removed by the bundled " +
                        "Xray core. Migrate this profile to XHTTP."
                )
            }

            is Transport.HttpUpgrade -> {
                stream.put("network", "httpupgrade")
                stream.put(
                    "httpupgradeSettings",
                    JSONObject().apply {
                        if (transport.host.isNotBlank()) {
                            put("host", transport.host)
                        }

                        if (transport.path.isNotBlank()) {
                            put("path", transport.path)
                        }
                    }
                )
            }

            is Transport.XHttp -> {
                stream.put("network", "xhttp")
                stream.put(
                    "xhttpSettings",
                    JSONObject().apply {
                        if (transport.host.isNotBlank()) {
                            put("host", transport.host)
                        }

                        if (transport.path.isNotBlank()) {
                            put("path", transport.path)
                        }

                        if (transport.mode.isNotBlank()) {
                            put("mode", transport.mode)
                        }
                    }
                )
            }
        }

        when (val security = profile.security) {
            Security.None -> {
                stream.put("security", "none")
            }

            is Security.Tls -> {
                stream.put("security", "tls")
                stream.put(
                    "tlsSettings",
                    buildTlsSettings(security, profile)
                )
            }

            is Security.Reality -> {
                require(
                    profile.transport is Transport.Tcp ||
                        profile.transport is Transport.Grpc ||
                        profile.transport is Transport.XHttp
                ) {
                    "REALITY is only supported with " +
                        "TCP, gRPC, or XHTTP"
                }

                stream.put("security", "reality")
                stream.put(
                    "realitySettings",
                    buildRealitySettings(security)
                )
            }
        }

        if (fragmentEnabled) {
            stream.put(
                "sockopt",
                JSONObject().put(
                    "dialerProxy",
                    FRAGMENT_OUT_TAG
                )
            )
        }

        return stream
    }

    private fun buildTlsSettings(
        security: Security.Tls,
        profile: ServerProfile
    ): JSONObject {
        val settings = JSONObject()

        val transportHost = when (val transport = profile.transport) {
            is Transport.WebSocket -> transport.host
            is Transport.HttpUpgrade -> transport.host
            is Transport.XHttp -> transport.host
            else -> ""
        }.trim()

        val effectiveServerName = when {
            security.serverName.isNotBlank() ->
                security.serverName
            isValidSniDomain(transportHost) ->
                transportHost
            isValidSniDomain(profile.address) ->
                profile.address
            else -> ""
        }

        if (effectiveServerName.isNotBlank()) {
            settings.put("serverName", effectiveServerName)
        }

        if (security.fingerprint.isNotBlank()) {
            settings.put("fingerprint", security.fingerprint)
        }

        val alpn = splitAlpn(security.alpn)

        if (alpn.length() > 0) {
            settings.put("alpn", alpn)
        }

        return settings
    }

    private fun buildRealitySettings(
        security: Security.Reality
    ): JSONObject {
        val settings = JSONObject()

        if (security.serverName.isNotBlank()) {
            settings.put("serverName", security.serverName)
        }

        if (security.fingerprint.isNotBlank()) {
            settings.put("fingerprint", security.fingerprint)
        }

        if (security.publicKey.isNotBlank()) {
            settings.put("publicKey", security.publicKey)
        }

        if (security.shortId.isNotBlank()) {
            settings.put("shortId", security.shortId)
        }

        if (security.spiderX.isNotBlank()) {
            settings.put("spiderX", security.spiderX)
        }

        return settings
    }

    private fun buildFragmentOutbound(
        packets: String,
        length: String,
        interval: String,
        maxSplit: String
    ): JSONObject {
        require(packets.isNotBlank()) {
            "Fragment packets must not be blank"
        }

        require(length.isNotBlank()) {
            "Fragment length must not be blank"
        }

        require(interval.isNotBlank()) {
            "Fragment interval must not be blank"
        }

        require(maxSplit.isNotBlank()) {
            "Fragment max split must not be blank"
        }

        return JSONObject()
            .put("tag", FRAGMENT_OUT_TAG)
            .put("protocol", "freedom")
            .put(
                "settings",
                JSONObject().put(
                    "fragment",
                    JSONObject()
                        .put(
                            "packets",
                            packets.trim()
                        )
                        .put(
                            "length",
                            length.trim()
                        )
                        .put(
                            "interval",
                            interval.trim()
                        )
                        .put(
                            "maxSplit",
                            maxSplit.trim()
                        )
                )
            )
    }

    private fun buildDirectOutbound(): JSONObject {
        return JSONObject()
            .put("tag", "direct")
            .put("protocol", "freedom")
            .put("settings", JSONObject())
    }

    private fun buildDnsOutbound(): JSONObject {
        return JSONObject()
            .put("tag", DNS_OUT_TAG)
            .put("protocol", "dns")
    }

    private fun buildBlockOutbound(): JSONObject {
        return JSONObject()
            .put("tag", "block")
            .put("protocol", "blackhole")
            .put(
                "settings",
                JSONObject().put(
                    "response",
                    JSONObject().put("type", "none")
                )
            )
    }

    private fun protocolName(protocol: Protocol): String {
        return when (protocol) {
            Protocol.VMESS -> "vmess"
            Protocol.VLESS -> "vless"
            Protocol.TROJAN -> "trojan"
            Protocol.SHADOWSOCKS -> "shadowsocks"
        }
    }

    private fun isValidSniDomain(value: String): Boolean {
        val domain = value.trim().removeSuffix(".")
        if (domain.length !in 1..253) return false
        if (domain.contains(':') || domain.contains('/')) return false
        if (domain.matches(Regex("[0-9.]+"))) return false

        return domain.split('.').all { label ->
            label.length in 1..63 &&
                label.first().isLetterOrDigit() &&
                label.last().isLetterOrDigit() &&
                label.all {
                    it.isLetterOrDigit() || it == '-'
                }
        }
    }

    private fun splitAlpn(value: String): JSONArray {
        val result = JSONArray()

        value
            .split(',', '\n')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .forEach(result::put)

        return result
    }

    private fun validateModel(profile: ServerProfile) {
        require(profile.address.isNotBlank()) {
            "Server address is blank"
        }

        require(profile.port in 1..65535) {
            "Invalid server port"
        }
    }
    private const val DNS_MODULE_TAG =
        "dns-module"

    private const val DNS_OUT_TAG =
        "dns-out"

    private val VALID_DOMAIN_STRATEGIES =
        setOf(
            "AsIs",
            "IPIfNonMatch",
            "IPOnDemand"
        )

    private const val DEFAULT_REMOTE_DNS =
        "https://cloudflare-dns.com/dns-query"

    private const val DEFAULT_DIRECT_DNS =
        "223.5.5.5"

    private const val DIRECT_DNS_TAG =
        "direct-dns"

    private const val FRAGMENT_OUT_TAG =
        "fragment"

    private const val FAKE_DNS_IPV4_POOL =
        "198.18.0.0/15"

    private const val FAKE_DNS_POOL_SIZE =
        65535

}
