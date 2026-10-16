package com.mdevz.xelvra.data.local

import com.mdevz.xelvra.data.model.Protocol
import com.mdevz.xelvra.data.model.ProtocolOptions
import com.mdevz.xelvra.data.model.Security
import com.mdevz.xelvra.data.model.ServerProfile
import com.mdevz.xelvra.data.model.Transport
import org.json.JSONArray
import org.json.JSONObject

object ProfileJsonCodec {

    fun encodeProfiles(profiles: List<ServerProfile>): String {
        val root = JSONObject()
        root.put("version", 1)

        val items = JSONArray()

        profiles.forEach { profile ->
            items.put(encodeProfile(profile))
        }

        root.put("profiles", items)

        return root.toString()
    }

    fun decodeProfiles(json: String): List<ServerProfile> {
        if (json.isBlank()) {
            return emptyList()
        }

        val root = JSONObject(json)

        val version = root.optInt("version", 1)
        if (version != 1) {
            throw IllegalArgumentException(
                "Unsupported profile storage version: $version"
            )
        }

        val items = root.optJSONArray("profiles")
            ?: return emptyList()

        return buildList {
            for (index in 0 until items.length()) {
                add(
                    decodeProfile(
                        items.getJSONObject(index)
                    )
                )
            }
        }
    }

    private fun encodeProfile(
        profile: ServerProfile
    ): JSONObject {
        return JSONObject().apply {
            put("id", profile.id)
            put("remarks", profile.remarks)
            put("protocol", profile.protocol.name)
            put("address", profile.address)
            put("port", profile.port)

            put(
                "protocolOptions",
                encodeProtocolOptions(profile.protocolOptions)
            )

            put(
                "transport",
                encodeTransport(profile.transport)
            )

            put(
                "security",
                encodeSecurity(profile.security)
            )
        }
    }

    private fun decodeProfile(
        json: JSONObject
    ): ServerProfile {
        val protocol = Protocol.valueOf(
            json.getString("protocol")
        )

        return ServerProfile(
            id = json.getString("id"),
            remarks = json.getString("remarks"),
            protocol = protocol,
            address = json.getString("address"),
            port = json.getInt("port"),
            protocolOptions = decodeProtocolOptions(
                protocol,
                json.getJSONObject("protocolOptions")
            ),
            transport = decodeTransport(
                json.getJSONObject("transport")
            ),
            security = decodeSecurity(
                json.getJSONObject("security")
            )
        )
    }

    private fun encodeProtocolOptions(
        options: ProtocolOptions
    ): JSONObject {
        return when (options) {
            is ProtocolOptions.VMess -> JSONObject().apply {
                put("uuid", options.uuid)
                put("alterId", options.alterId)
                put("encryption", options.encryption)
            }

            is ProtocolOptions.VLess -> JSONObject().apply {
                put("uuid", options.uuid)
                put("flow", options.flow)
            }

            is ProtocolOptions.Trojan -> JSONObject().apply {
                put("password", options.password)
            }

            is ProtocolOptions.Shadowsocks -> JSONObject().apply {
                put("method", options.method)
                put("password", options.password)
            }
        }
    }

    private fun decodeProtocolOptions(
        protocol: Protocol,
        json: JSONObject
    ): ProtocolOptions {
        return when (protocol) {
            Protocol.VMESS -> {
                ProtocolOptions.VMess(
                    uuid = json.getString("uuid"),
                    alterId = json.optInt("alterId", 0),
                    encryption = json.optString(
                        "encryption",
                        "auto"
                    )
                )
            }

            Protocol.VLESS -> {
                ProtocolOptions.VLess(
                    uuid = json.getString("uuid"),
                    flow = json.optString("flow", "")
                )
            }

            Protocol.TROJAN -> {
                ProtocolOptions.Trojan(
                    password = json.getString("password")
                )
            }

            Protocol.SHADOWSOCKS -> {
                ProtocolOptions.Shadowsocks(
                    method = json.getString("method"),
                    password = json.getString("password")
                )
            }
        }
    }

    private fun encodeTransport(
        transport: Transport
    ): JSONObject {
        return when (transport) {
            Transport.Tcp -> JSONObject().apply {
                put("type", "tcp")
            }

            is Transport.WebSocket -> JSONObject().apply {
                put("type", "websocket")
                put("host", transport.host)
                put("path", transport.path)
            }

            is Transport.Grpc -> JSONObject().apply {
                put("type", "grpc")
                put("serviceName", transport.serviceName)
            }

            is Transport.Http -> JSONObject().apply {
                put("type", "http")
                put("host", transport.host)
                put("path", transport.path)
            }

            is Transport.HttpUpgrade -> JSONObject().apply {
                put("type", "httpupgrade")
                put("host", transport.host)
                put("path", transport.path)
            }

            is Transport.XHttp -> JSONObject().apply {
                put("type", "xhttp")
                put("host", transport.host)
                put("path", transport.path)
                put("mode", transport.mode)
            }
        }
    }

    private fun decodeTransport(
        json: JSONObject
    ): Transport {
        return when (json.getString("type")) {
            "tcp" -> Transport.Tcp

            "websocket" -> Transport.WebSocket(
                host = json.optString("host", ""),
                path = json.optString("path", "")
            )

            "grpc" -> Transport.Grpc(
                serviceName = json.optString(
                    "serviceName",
                    ""
                )
            )

            "http" -> Transport.Http(
                host = json.optString("host", ""),
                path = json.optString("path", "")
            )

            "httpupgrade" -> Transport.HttpUpgrade(
                host = json.optString("host", ""),
                path = json.optString("path", "")
            )

            "xhttp" -> Transport.XHttp(
                host = json.optString("host", ""),
                path = json.optString("path", ""),
                mode = json.optString("mode", "")
            )

            else -> throw IllegalArgumentException(
                "Unsupported transport type"
            )
        }
    }

    private fun encodeSecurity(
        security: Security
    ): JSONObject {
        return when (security) {
            Security.None -> JSONObject().apply {
                put("type", "none")
            }

            is Security.Tls -> JSONObject().apply {
                put("type", "tls")
                put("serverName", security.serverName)
                put("fingerprint", security.fingerprint)
                put("alpn", security.alpn)
            }

            is Security.Reality -> JSONObject().apply {
                put("type", "reality")
                put("serverName", security.serverName)
                put("fingerprint", security.fingerprint)
                put("publicKey", security.publicKey)
                put("shortId", security.shortId)
                put("spiderX", security.spiderX)
            }
        }
    }

    private fun decodeSecurity(
        json: JSONObject
    ): Security {
        return when (json.getString("type")) {
            "none" -> Security.None

            "tls" -> Security.Tls(
                serverName = json.optString(
                    "serverName",
                    ""
                ),
                fingerprint = json.optString(
                    "fingerprint",
                    ""
                ),
                alpn = json.optString("alpn", "")
            )

            "reality" -> Security.Reality(
                serverName = json.optString(
                    "serverName",
                    ""
                ),
                fingerprint = json.optString(
                    "fingerprint",
                    ""
                ),
                publicKey = json.optString(
                    "publicKey",
                    ""
                ),
                shortId = json.optString(
                    "shortId",
                    ""
                ),
                spiderX = json.optString(
                    "spiderX",
                    ""
                )
            )

            else -> throw IllegalArgumentException(
                "Unsupported security type"
            )
        }
    }
}
