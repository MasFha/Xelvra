package com.mdevz.xelvra.data.model

data class ServerProfile(
    val id: String,
    val remarks: String,
    val protocol: Protocol,
    val address: String,
    val port: Int,
    val protocolOptions: ProtocolOptions,
    val transport: Transport = Transport.Tcp,
    val security: Security = Security.None
)
