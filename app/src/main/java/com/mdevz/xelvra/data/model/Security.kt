package com.mdevz.xelvra.data.model

sealed interface Security {

    data object None : Security

    data class Tls(
        val serverName: String = "",
        val fingerprint: String = "",
        val alpn: String = ""
    ) : Security

    data class Reality(
        val serverName: String = "",
        val fingerprint: String = "",
        val publicKey: String = "",
        val shortId: String = "",
        val spiderX: String = ""
    ) : Security
}
