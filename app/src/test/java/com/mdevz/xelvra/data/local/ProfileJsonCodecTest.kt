package com.mdevz.xelvra.data.local

import com.mdevz.xelvra.data.model.Protocol
import com.mdevz.xelvra.data.model.ProtocolOptions
import com.mdevz.xelvra.data.model.Security
import com.mdevz.xelvra.data.model.ServerProfile
import com.mdevz.xelvra.data.model.Transport
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileJsonCodecTest {

    @Test
    fun allProfileSettingsSurviveJsonRoundTrip() {
        val uuid = "123e4567-e89b-42d3-a456-426614174000"

        val protocols = listOf(
            Protocol.VMESS to ProtocolOptions.VMess(
                uuid = uuid,
                alterId = 0,
                encryption = "auto"
            ),
            Protocol.VLESS to ProtocolOptions.VLess(
                uuid = uuid,
                flow = "xtls-rprx-vision"
            ),
            Protocol.TROJAN to ProtocolOptions.Trojan(
                password = "trojan-password"
            ),
            Protocol.SHADOWSOCKS to ProtocolOptions.Shadowsocks(
                method = "aes-128-gcm",
                password = "ss-password"
            )
        )

        val transports = listOf(
            Transport.Tcp,
            Transport.WebSocket("ws.example.com", "/ws"),
            Transport.Grpc("grpc-service"),
            Transport.Http("legacy.example.com", "/legacy"),
            Transport.HttpUpgrade("upgrade.example.com", "/upgrade"),
            Transport.XHttp("xhttp.example.com", "/xhttp", "auto")
        )

        val securities = listOf(
            Security.None,
            Security.Tls(
                serverName = "tls.example.com",
                fingerprint = "chrome",
                alpn = "h2,http/1.1"
            ),
            Security.Reality(
                serverName = "reality.example.com",
                fingerprint = "chrome",
                publicKey = "public-key",
                shortId = "abcd1234",
                spiderX = "/spider"
            )
        )

        val profiles = buildList {
            for ((protocol, options) in protocols) {
                for (transport in transports) {
                    for (security in securities) {
                        add(
                            ServerProfile(
                                id = "profile-$size",
                                remarks = "Round-trip profile $size",
                                protocol = protocol,
                                address = "example.com",
                                port = 443,
                                protocolOptions = options,
                                transport = transport,
                                security = security
                            )
                        )
                    }
                }
            }
        }

        val encoded = ProfileJsonCodec.encodeProfiles(profiles)
        val decoded = ProfileJsonCodec.decodeProfiles(encoded)

        assertEquals(profiles, decoded)
    }
}
