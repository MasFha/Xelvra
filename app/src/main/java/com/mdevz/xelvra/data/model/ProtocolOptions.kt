package com.mdevz.xelvra.data.model

sealed interface ProtocolOptions {

    data class VMess(
        val uuid: String,
        val alterId: Int = 0,
        val encryption: String = "auto"
    ) : ProtocolOptions

    data class VLess(
        val uuid: String,
        val flow: String = ""
    ) : ProtocolOptions

    data class Trojan(
        val password: String
    ) : ProtocolOptions

    data class Shadowsocks(
        val method: String,
        val password: String
    ) : ProtocolOptions
}
