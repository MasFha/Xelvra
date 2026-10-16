package com.mdevz.xelvra.protocol.uri

import android.net.Uri
import android.util.Base64
import com.mdevz.xelvra.data.model.Protocol
import com.mdevz.xelvra.data.model.ProtocolOptions
import com.mdevz.xelvra.data.model.Security
import com.mdevz.xelvra.data.model.ServerProfile
import com.mdevz.xelvra.data.model.Transport
import org.json.JSONObject
import java.util.UUID

object ProfileUriCodec {

    fun decode(
        value: String
    ): ProfileUriDecodeResult {
        val raw = value.trim()

        if (raw.isBlank()) {
            return error(
                ProfileUriDecodeResult.ErrorType.EMPTY_URI
            )
        }

        return try {
            when {
                raw.startsWith(
                    "vmess://",
                    ignoreCase = true
                ) -> decodeVmess(raw)

                raw.startsWith(
                    "vless://",
                    ignoreCase = true
                ) -> decodeVless(raw)

                raw.startsWith(
                    "trojan://",
                    ignoreCase = true
                ) -> decodeTrojan(raw)

                raw.startsWith(
                    "ss://",
                    ignoreCase = true
                ) -> decodeShadowsocks(raw)

                else -> error(
                    ProfileUriDecodeResult.ErrorType
                        .UNSUPPORTED_PROTOCOL
                )
            }
        } catch (
            _: Exception
        ) {
            error(
                ProfileUriDecodeResult.ErrorType.INVALID_URI
            )
        }
    }

    fun encode(
        profile: ServerProfile
    ): String {
        return when (profile.protocol) {
            Protocol.VMESS ->
                encodeVmess(profile)

            Protocol.VLESS ->
                encodeVless(profile)

            Protocol.TROJAN ->
                encodeTrojan(profile)

            Protocol.SHADOWSOCKS ->
                encodeShadowsocks(profile)
        }
    }

    private fun decodeVmess(
        raw: String
    ): ProfileUriDecodeResult {
        val encoded = raw.substringAfter(
            "://"
        )

        val decoded = decodeBase64(encoded)

        val json = JSONObject(decoded)

        val address = json.optString("add")
        val port = json.optString("port")
            .toIntOrNull()
            ?: json.optInt("port", 0)

        val uuid = json.optString("id")

        val remarks = json.optString("ps")
            .ifBlank {
                defaultRemarks(
                    Protocol.VMESS,
                    address
                )
            }

        val network = json.optString(
            "net",
            "tcp"
        )

        val host = json.optString("host")
        val path = json.optString("path")

        val transport = when (
            network.lowercase()
        ) {
            "ws" -> Transport.WebSocket(
                host = host,
                path = path
            )

            "grpc" -> Transport.Grpc(
                serviceName = path
            )

            "http", "h2" -> Transport.Http(
                host = host,
                path = path
            )

            "httpupgrade" ->
                Transport.HttpUpgrade(
                    host = host,
                    path = path
                )

            "xhttp", "splithttp" ->
                Transport.XHttp(
                    host = host,
                    path = path,
                    mode = json.optString(
                        "mode"
                    )
                )

            else -> Transport.Tcp
        }

        val tlsValue = json.optString("tls")
            .lowercase()

        val sni = json.optString("sni")
        val fingerprint =
            json.optString("fp")
        val alpn = json.optString("alpn")

        val security = when (tlsValue) {
            "tls" -> Security.Tls(
                serverName = sni,
                fingerprint = fingerprint,
                alpn = alpn
            )

            "reality" -> Security.Reality(
                serverName = sni,
                fingerprint = fingerprint,
                publicKey = json.optString(
                    "pbk"
                ),
                shortId = json.optString(
                    "sid"
                ),
                spiderX = json.optString(
                    "spx"
                )
            )

            else -> Security.None
        }

        return success(
            ServerProfile(
                id = UUID.randomUUID()
                    .toString(),
                remarks = remarks,
                protocol = Protocol.VMESS,
                address = address,
                port = port,
                protocolOptions =
                    ProtocolOptions.VMess(
                        uuid = uuid,
                        alterId =
                            json.optString("aid")
                                .toIntOrNull()
                                ?: json.optInt(
                                    "aid",
                                    0
                                ),
                        encryption =
                            json.optString(
                                "scy",
                                "auto"
                            )
                    ),
                transport = transport,
                security = security
            )
        )
    }

    private fun encodeVmess(
        profile: ServerProfile
    ): String {
        val options =
            profile.protocolOptions
                as? ProtocolOptions.VMess
                ?: throw IllegalArgumentException(
                    "Invalid VMess options"
                )

        val json = JSONObject()

        json.put("v", "2")
        json.put("ps", profile.remarks)
        json.put("add", profile.address)
        json.put("port", profile.port.toString())
        json.put("id", options.uuid)
        json.put(
            "aid",
            options.alterId.toString()
        )
        json.put(
            "scy",
            options.encryption
        )

        when (
            val transport = profile.transport
        ) {
            Transport.Tcp -> {
                json.put("net", "tcp")
                json.put("type", "none")
            }

            is Transport.WebSocket -> {
                json.put("net", "ws")
                json.put(
                    "host",
                    transport.host
                )
                json.put(
                    "path",
                    transport.path
                )
            }

            is Transport.Grpc -> {
                json.put("net", "grpc")
                json.put(
                    "path",
                    transport.serviceName
                )
            }

            is Transport.Http -> {
                json.put("net", "http")
                json.put(
                    "host",
                    transport.host
                )
                json.put(
                    "path",
                    transport.path
                )
            }

            is Transport.HttpUpgrade -> {
                json.put(
                    "net",
                    "httpupgrade"
                )
                json.put(
                    "host",
                    transport.host
                )
                json.put(
                    "path",
                    transport.path
                )
            }

            is Transport.XHttp -> {
                json.put("net", "xhttp")
                json.put(
                    "host",
                    transport.host
                )
                json.put(
                    "path",
                    transport.path
                )
                json.put(
                    "mode",
                    transport.mode
                )
            }
        }

        putVmessSecurity(
            json,
            profile.security
        )

        val encoded = Base64.encodeToString(
            json.toString()
                .toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )

        return "vmess://$encoded"
    }

    private fun putVmessSecurity(
        json: JSONObject,
        security: Security
    ) {
        when (security) {
            Security.None -> {
                json.put("tls", "")
            }

            is Security.Tls -> {
                json.put("tls", "tls")
                json.put(
                    "sni",
                    security.serverName
                )
                json.put(
                    "fp",
                    security.fingerprint
                )
                json.put(
                    "alpn",
                    security.alpn
                )
            }

            is Security.Reality -> {
                json.put("tls", "reality")
                json.put(
                    "sni",
                    security.serverName
                )
                json.put(
                    "fp",
                    security.fingerprint
                )
                json.put(
                    "pbk",
                    security.publicKey
                )
                json.put(
                    "sid",
                    security.shortId
                )
                json.put(
                    "spx",
                    security.spiderX
                )
            }
        }
    }

    private fun decodeVless(
        raw: String
    ): ProfileUriDecodeResult {
        val uri = Uri.parse(raw)

        val uuid = uri.userInfo.orEmpty()
        val address = normalizeHost(
            uri.host.orEmpty()
        )
        val port = uri.port

        val transport =
            decodeTransport(uri)

        val security =
            decodeSecurity(uri)

        val flow =
            query(uri, "flow")

        return success(
            ServerProfile(
                id = UUID.randomUUID()
                    .toString(),
                remarks = remarks(
                    uri,
                    Protocol.VLESS,
                    address
                ),
                protocol = Protocol.VLESS,
                address = address,
                port = port,
                protocolOptions =
                    ProtocolOptions.VLess(
                        uuid = uuid,
                        flow = flow
                    ),
                transport = transport,
                security = security
            )
        )
    }

    private fun encodeVless(
        profile: ServerProfile
    ): String {
        val options =
            profile.protocolOptions
                as? ProtocolOptions.VLess
                ?: throw IllegalArgumentException(
                    "Invalid Vless options"
                )

        return buildStandardUri(
            scheme = "vless",
            userInfo = options.uuid,
            profile = profile,
            extraQuery = buildList {
                if (
                    options.flow.isNotBlank()
                ) {
                    add(
                        "flow" to options.flow
                    )
                }
            }
        )
    }

    private fun decodeTrojan(
        raw: String
    ): ProfileUriDecodeResult {
        val uri = Uri.parse(raw)

        val address = normalizeHost(
            uri.host.orEmpty()
        )

        return success(
            ServerProfile(
                id = UUID.randomUUID()
                    .toString(),
                remarks = remarks(
                    uri,
                    Protocol.TROJAN,
                    address
                ),
                protocol = Protocol.TROJAN,
                address = address,
                port = uri.port,
                protocolOptions =
                    ProtocolOptions.Trojan(
                        password =
                            uri.userInfo.orEmpty()
                    ),
                transport =
                    decodeTransport(uri),
                security =
                    decodeSecurity(uri)
            )
        )
    }

    private fun encodeTrojan(
        profile: ServerProfile
    ): String {
        val options =
            profile.protocolOptions
                as? ProtocolOptions.Trojan
                ?: throw IllegalArgumentException(
                    "Invalid Trojan options"
                )

        return buildStandardUri(
            scheme = "trojan",
            userInfo = options.password,
            profile = profile
        )
    }

    private fun decodeShadowsocks(
        raw: String
    ): ProfileUriDecodeResult {
        val withoutScheme =
            raw.substringAfter("://")

        val fragmentIndex =
            withoutScheme.indexOf('#')

        val body = if (fragmentIndex >= 0) {
            withoutScheme.substring(
                0,
                fragmentIndex
            )
        } else {
            withoutScheme
        }

        val remarks = if (
            fragmentIndex >= 0
        ) {
            Uri.decode(
                withoutScheme.substring(
                    fragmentIndex + 1
                )
            )
        } else {
            ""
        }

        val queryFree =
            body.substringBefore('?')

        val atIndex =
            queryFree.lastIndexOf('@')

        val credentials: String
        val endpoint: String

        if (atIndex >= 0) {
            val credentialPart =
                queryFree.substring(
                    0,
                    atIndex
                )

            credentials =
                if (
                    credentialPart.contains(':')
                ) {
                    Uri.decode(
                        credentialPart
                    )
                } else {
                    decodeBase64(
                        credentialPart
                    )
                }

            endpoint =
                queryFree.substring(
                    atIndex + 1
                )
        } else {
            val decoded =
                decodeBase64(queryFree)

            val decodedAt =
                decoded.lastIndexOf('@')

            if (decodedAt < 0) {
                return error(
                    ProfileUriDecodeResult.ErrorType
                        .INVALID_SHADOWSOCKS_URI
                )
            }

            credentials =
                decoded.substring(
                    0,
                    decodedAt
                )

            endpoint =
                decoded.substring(
                    decodedAt + 1
                )
        }

        val separator =
            credentials.indexOf(':')

        if (separator <= 0) {
            return error(
                ProfileUriDecodeResult.ErrorType
                    .INVALID_SHADOWSOCKS_CREDENTIALS
            )
        }

        val method =
            credentials.substring(
                0,
                separator
            )

        val password =
            credentials.substring(
                separator + 1
            )

        val hostPort =
            parseHostPort(endpoint)
                ?: return error(
                    ProfileUriDecodeResult.ErrorType
                        .INVALID_SHADOWSOCKS_ENDPOINT
                )

        return success(
            ServerProfile(
                id = UUID.randomUUID()
                    .toString(),
                remarks = remarks.ifBlank {
                    defaultRemarks(
                        Protocol.SHADOWSOCKS,
                        hostPort.first
                    )
                },
                protocol =
                    Protocol.SHADOWSOCKS,
                address = hostPort.first,
                port = hostPort.second,
                protocolOptions =
                    ProtocolOptions.Shadowsocks(
                        method = method,
                        password = password
                    ),
                transport = Transport.Tcp,
                security = Security.None
            )
        )
    }

    private fun encodeShadowsocks(
        profile: ServerProfile
    ): String {
        val options =
            profile.protocolOptions
                as? ProtocolOptions.Shadowsocks
                ?: throw IllegalArgumentException(
                    "Invalid Shadowsocks options"
                )

        val credentials =
            "${options.method}:${options.password}"

        val encodedCredentials =
            Base64.encodeToString(
                credentials.toByteArray(
                    Charsets.UTF_8
                ),
                Base64.NO_WRAP or
                    Base64.NO_PADDING or
                    Base64.URL_SAFE
            )

        return buildString {
            append("ss://")
            append(encodedCredentials)
            append("@")
            append(
                encodeHost(
                    profile.address
                )
            )
            append(":")
            append(profile.port)

            if (
                profile.remarks.isNotBlank()
            ) {
                append("#")
                append(
                    Uri.encode(
                        profile.remarks
                    )
                )
            }
        }
    }

    private fun decodeTransport(
        uri: Uri
    ): Transport {
        return when (
            query(uri, "type")
                .lowercase()
        ) {
            "ws", "websocket" ->
                Transport.WebSocket(
                    host = query(
                        uri,
                        "host"
                    ),
                    path = query(
                        uri,
                        "path"
                    )
                )

            "grpc" ->
                Transport.Grpc(
                    serviceName =
                        query(
                            uri,
                            "serviceName"
                        ).ifBlank {
                            query(
                                uri,
                                "service"
                            )
                        }
                )

            "http", "h2" ->
                Transport.Http(
                    host = query(
                        uri,
                        "host"
                    ),
                    path = query(
                        uri,
                        "path"
                    )
                )

            "httpupgrade" ->
                Transport.HttpUpgrade(
                    host = query(
                        uri,
                        "host"
                    ),
                    path = query(
                        uri,
                        "path"
                    )
                )

            "xhttp", "splithttp" ->
                Transport.XHttp(
                    host = query(
                        uri,
                        "host"
                    ),
                    path = query(
                        uri,
                        "path"
                    ),
                    mode = query(
                        uri,
                        "mode"
                    )
                )

            else -> Transport.Tcp
        }
    }

    private fun decodeSecurity(
        uri: Uri
    ): Security {
        return when (
            query(uri, "security")
                .lowercase()
        ) {
            "tls" -> Security.Tls(
                serverName =
                    query(uri, "sni"),
                fingerprint =
                    query(uri, "fp"),
                alpn =
                    query(uri, "alpn")
            )

            "reality" ->
                Security.Reality(
                    serverName =
                        query(uri, "sni"),
                    fingerprint =
                        query(uri, "fp"),
                    publicKey =
                        query(uri, "pbk"),
                    shortId =
                        query(uri, "sid"),
                    spiderX =
                        query(uri, "spx")
                )

            else -> Security.None
        }
    }

    private fun buildStandardUri(
        scheme: String,
        userInfo: String,
        profile: ServerProfile,
        extraQuery:
            List<Pair<String, String>> =
                emptyList()
    ): String {
        val queryItems =
            mutableListOf<Pair<String, String>>()

        queryItems +=
            encodeTransport(profile.transport)

        queryItems +=
            encodeSecurity(profile.security)

        queryItems += extraQuery

        return buildString {
            append(scheme)
            append("://")
            append(Uri.encode(userInfo))
            append("@")
            append(
                encodeHost(
                    profile.address
                )
            )
            append(":")
            append(profile.port)

            if (queryItems.isNotEmpty()) {
                append("?")

                append(
                    queryItems.joinToString(
                        "&"
                    ) { (key, value) ->
                        "${Uri.encode(key)}=" +
                            Uri.encode(value)
                    }
                )
            }

            if (
                profile.remarks.isNotBlank()
            ) {
                append("#")
                append(
                    Uri.encode(
                        profile.remarks
                    )
                )
            }
        }
    }

    private fun encodeTransport(
        transport: Transport
    ): List<Pair<String, String>> {
        return when (transport) {
            Transport.Tcp ->
                listOf(
                    "type" to "tcp"
                )

            is Transport.WebSocket ->
                buildList {
                    add("type" to "ws")

                    addIfNotBlank(
                        "host",
                        transport.host
                    )

                    addIfNotBlank(
                        "path",
                        transport.path
                    )
                }

            is Transport.Grpc ->
                buildList {
                    add("type" to "grpc")

                    addIfNotBlank(
                        "serviceName",
                        transport.serviceName
                    )
                }

            is Transport.Http ->
                buildList {
                    add("type" to "http")

                    addIfNotBlank(
                        "host",
                        transport.host
                    )

                    addIfNotBlank(
                        "path",
                        transport.path
                    )
                }

            is Transport.HttpUpgrade ->
                buildList {
                    add(
                        "type" to
                            "httpupgrade"
                    )

                    addIfNotBlank(
                        "host",
                        transport.host
                    )

                    addIfNotBlank(
                        "path",
                        transport.path
                    )
                }

            is Transport.XHttp ->
                buildList {
                    add("type" to "xhttp")

                    addIfNotBlank(
                        "host",
                        transport.host
                    )

                    addIfNotBlank(
                        "path",
                        transport.path
                    )

                    addIfNotBlank(
                        "mode",
                        transport.mode
                    )
                }
        }
    }

    private fun encodeSecurity(
        security: Security
    ): List<Pair<String, String>> {
        return when (security) {
            Security.None ->
                listOf(
                    "security" to "none"
                )

            is Security.Tls ->
                buildList {
                    add("security" to "tls")

                    addIfNotBlank(
                        "sni",
                        security.serverName
                    )

                    addIfNotBlank(
                        "fp",
                        security.fingerprint
                    )

                    addIfNotBlank(
                        "alpn",
                        security.alpn
                    )
                }

            is Security.Reality ->
                buildList {
                    add(
                        "security" to
                            "reality"
                    )

                    addIfNotBlank(
                        "sni",
                        security.serverName
                    )

                    addIfNotBlank(
                        "fp",
                        security.fingerprint
                    )

                    addIfNotBlank(
                        "pbk",
                        security.publicKey
                    )

                    addIfNotBlank(
                        "sid",
                        security.shortId
                    )

                    addIfNotBlank(
                        "spx",
                        security.spiderX
                    )
                }
        }
    }

    private fun MutableList<
        Pair<String, String>
    >.addIfNotBlank(
        key: String,
        value: String
    ) {
        if (value.isNotBlank()) {
            add(key to value)
        }
    }

    private fun query(
        uri: Uri,
        key: String
    ): String {
        return uri.getQueryParameter(key)
            .orEmpty()
    }

    private fun remarks(
        uri: Uri,
        protocol: Protocol,
        address: String
    ): String {
        return uri.fragment
            ?.let(Uri::decode)
            ?.takeIf {
                it.isNotBlank()
            }
            ?: defaultRemarks(
                protocol,
                address
            )
    }

    private fun defaultRemarks(
        protocol: Protocol,
        address: String
    ): String {
        val name = when (protocol) {
            Protocol.VMESS -> "VMess"
            Protocol.VLESS -> "Vless"
            Protocol.TROJAN -> "Trojan"
            Protocol.SHADOWSOCKS ->
                "Shadowsocks"
        }

        return if (address.isBlank()) {
            name
        } else {
            "$name $address"
        }
    }

    private fun normalizeHost(
        host: String
    ): String {
        return host
            .removePrefix("[")
            .removeSuffix("]")
    }

    private fun encodeHost(
        host: String
    ): String {
        val clean = host
            .removePrefix("[")
            .removeSuffix("]")

        return if (clean.contains(':')) {
            "[$clean]"
        } else {
            clean
        }
    }

    private fun parseHostPort(
        value: String
    ): Pair<String, Int>? {
        val clean = value.trim()

        if (clean.startsWith("[")) {
            val end =
                clean.indexOf(']')

            if (end <= 1) {
                return null
            }

            val host =
                clean.substring(1, end)

            val port =
                clean.substring(
                    end + 1
                )
                    .removePrefix(":")
                    .toIntOrNull()
                    ?: return null

            return host to port
        }

        val separator =
            clean.lastIndexOf(':')

        if (separator <= 0) {
            return null
        }

        val host =
            clean.substring(
                0,
                separator
            )

        val port =
            clean.substring(
                separator + 1
            ).toIntOrNull()
                ?: return null

        return host to port
    }

    private fun decodeBase64(
        value: String
    ): String {
        val clean = value
            .trim()
            .replace(
                "\n",
                ""
            )
            .replace(
                "\r",
                ""
            )

        val padded = clean +
            "=".repeat(
                (4 - clean.length % 4) % 4
            )

        val bytes = try {
            Base64.decode(
                padded,
                Base64.DEFAULT
            )
        } catch (
            first: IllegalArgumentException
        ) {
            Base64.decode(
                padded,
                Base64.URL_SAFE
            )
        }

        return bytes.toString(
            Charsets.UTF_8
        )
    }

    private fun success(
        profile: ServerProfile
    ): ProfileUriDecodeResult {
        if (profile.address.isBlank()) {
            return error(
                ProfileUriDecodeResult.ErrorType
                    .INVALID_ADDRESS
            )
        }

        if (profile.port !in 1..65535) {
            return error(
                ProfileUriDecodeResult.ErrorType
                    .INVALID_PORT
            )
        }

        return ProfileUriDecodeResult
            .Success(profile)
    }

    private fun error(
        error:
            ProfileUriDecodeResult.ErrorType
    ): ProfileUriDecodeResult {
        return ProfileUriDecodeResult
            .Error(error)
    }
}
