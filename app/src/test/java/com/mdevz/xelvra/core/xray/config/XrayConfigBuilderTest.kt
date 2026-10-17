package com.mdevz.xelvra.core.xray.config

import com.mdevz.xelvra.data.model.Protocol
import com.mdevz.xelvra.data.model.ProtocolOptions
import com.mdevz.xelvra.data.model.Security
import com.mdevz.xelvra.data.model.ServerProfile
import com.mdevz.xelvra.data.model.Transport
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayConfigBuilderTest {

    @Test
    fun shadowsocksOutboundUsesServers() {
        val profile = ServerProfile(
            id = "dummy-shadowsocks",
            remarks = "Shadowsocks test",
            protocol = Protocol.SHADOWSOCKS,
            address = "ss.example.com",
            port = 8388,
            protocolOptions = ProtocolOptions.Shadowsocks(
                method = "aes-256-gcm",
                password = "dummy-password"
            ),
            transport = Transport.Tcp,
            security = Security.None
        )

        val config = JSONObject(
            XrayConfigBuilder.build(profile = profile)
        )

        val outbound = config
            .getJSONArray("outbounds")
            .getJSONObject(0)

        assertEquals(
            "shadowsocks",
            outbound.getString("protocol")
        )

        val settings = outbound.getJSONObject("settings")
        val servers = settings.getJSONArray("servers")

        assertEquals(1, servers.length())

        val server = servers.getJSONObject(0)

        assertEquals(
            "ss.example.com",
            server.getString("address")
        )
        assertEquals(8388, server.getInt("port"))
        assertEquals(
            "aes-256-gcm",
            server.getString("method")
        )
        assertEquals(
            "dummy-password",
            server.getString("password")
        )

        assertFalse(settings.has("method"))
        assertFalse(settings.has("address"))
    }



    @Test
    fun vmessOutboundUsesVnextAndUsers() {
        val uuid = "123e4567-e89b-42d3-a456-426614174001"

        val profile = ServerProfile(
            id = "dummy-vmess",
            remarks = "VMess test",
            protocol = Protocol.VMESS,
            address = "vmess.example.com",
            port = 8443,
            protocolOptions = ProtocolOptions.VMess(
                uuid = uuid,
                alterId = 0,
                encryption = "auto"
            ),
            transport = Transport.Tcp,
            security = Security.None
        )

        val config = JSONObject(
            XrayConfigBuilder.build(profile = profile)
        )

        val outbound = config
            .getJSONArray("outbounds")
            .getJSONObject(0)

        assertEquals("vmess", outbound.getString("protocol"))

        val settings = outbound.getJSONObject("settings")
        val vnext = settings.getJSONArray("vnext")

        assertEquals(1, vnext.length())

        val server = vnext.getJSONObject(0)
        assertEquals(
            "vmess.example.com",
            server.getString("address")
        )
        assertEquals(8443, server.getInt("port"))

        val users = server.getJSONArray("users")
        assertEquals(1, users.length())

        val user = users.getJSONObject(0)
        assertEquals(uuid, user.getString("id"))
        assertEquals(0, user.getInt("alterId"))
        assertEquals("auto", user.getString("security"))

        assertFalse(settings.has("id"))
        assertFalse(settings.has("address"))
    }



    @Test
    fun vlessOutboundUsesVnextAndUsers() {
        val uuid = "123e4567-e89b-42d3-a456-426614174000"

        val profile = ServerProfile(
            id = "dummy-vless",
            remarks = "VLESS test",
            protocol = Protocol.VLESS,
            address = "example.com",
            port = 443,
            protocolOptions = ProtocolOptions.VLess(
                uuid = uuid,
                flow = "xtls-rprx-vision"
            ),
            transport = Transport.Tcp,
            security = Security.None
        )

        val config = JSONObject(
            XrayConfigBuilder.build(profile = profile)
        )

        val outbound = config
            .getJSONArray("outbounds")
            .getJSONObject(0)

        assertEquals("vless", outbound.getString("protocol"))

        val settings = outbound.getJSONObject("settings")
        val vnext = settings.getJSONArray("vnext")

        assertEquals(1, vnext.length())

        val server = vnext.getJSONObject(0)
        assertEquals("example.com", server.getString("address"))
        assertEquals(443, server.getInt("port"))

        val users = server.getJSONArray("users")
        assertEquals(1, users.length())

        val user = users.getJSONObject(0)
        assertEquals(uuid, user.getString("id"))
        assertEquals("none", user.getString("encryption"))
        assertEquals(
            "xtls-rprx-vision",
            user.getString("flow")
        )

        assertFalse(settings.has("id"))
        assertFalse(settings.has("address"))
    }



    @Test
    fun trojanWebSocketTlsDisablesMuxAndUsesHostAsSni() {
        val profile = ServerProfile(
            id = "dummy-trojan",
            remarks = "Trojan test",
            protocol = Protocol.TROJAN,
            address = "198.51.100.10",
            port = 443,
            protocolOptions = ProtocolOptions.Trojan(
                password = "dummy-password"
            ),
            transport = Transport.WebSocket(
                host = "example.com",
                path = "/trojan"
            ),
            security = Security.Tls(
                serverName = "",
                fingerprint = "chrome",
                alpn = "http/1.1,h2"
            )
        )

        val config = JSONObject(
            XrayConfigBuilder.build(
                profile = profile,
                muxEnabled = true,
                fragmentEnabled = false
            )
        )

        val outbound = config
            .getJSONArray("outbounds")
            .getJSONObject(0)

        assertEquals("proxy", outbound.getString("tag"))
        assertEquals("trojan", outbound.getString("protocol"))

        val settings = outbound.getJSONObject("settings")
        val servers = settings.getJSONArray("servers")

        assertEquals(1, servers.length())

        val server = servers.getJSONObject(0)
        assertEquals("198.51.100.10", server.getString("address"))
        assertEquals(443, server.getInt("port"))
        assertEquals("dummy-password", server.getString("password"))

        val mux = outbound.getJSONObject("mux")
        assertFalse(mux.getBoolean("enabled"))
        assertEquals(-1, mux.getInt("concurrency"))

        val stream = outbound.getJSONObject("streamSettings")
        assertEquals("ws", stream.getString("network"))
        assertEquals("tls", stream.getString("security"))

        val ws = stream.getJSONObject("wsSettings")
        assertEquals("example.com", ws.getString("host"))
        assertEquals("/trojan", ws.getString("path"))

        val tls = stream.getJSONObject("tlsSettings")
        assertEquals("example.com", tls.getString("serverName"))
        assertEquals("chrome", tls.getString("fingerprint"))

        val alpn = tls.getJSONArray("alpn")
        assertEquals(2, alpn.length())
        assertEquals("http/1.1", alpn.getString(0))
        assertEquals("h2", alpn.getString(1))

        assertTrue(config.has("inbounds"))
        assertTrue(config.has("routing"))
    }
}
