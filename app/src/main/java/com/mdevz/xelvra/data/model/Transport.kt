package com.mdevz.xelvra.data.model

sealed interface Transport {

    data object Tcp : Transport

    data class WebSocket(
        val host: String = "",
        val path: String = ""
    ) : Transport

    data class Grpc(
        val serviceName: String = ""
    ) : Transport

    data class Http(
        val host: String = "",
        val path: String = ""
    ) : Transport

    data class HttpUpgrade(
        val host: String = "",
        val path: String = ""
    ) : Transport

    data class XHttp(
        val host: String = "",
        val path: String = "",
        val mode: String = ""
    ) : Transport
}
