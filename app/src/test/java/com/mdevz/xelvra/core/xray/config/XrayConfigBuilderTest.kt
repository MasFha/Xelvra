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
        assertEquals("dummy-password", settings.getString("password"))

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
