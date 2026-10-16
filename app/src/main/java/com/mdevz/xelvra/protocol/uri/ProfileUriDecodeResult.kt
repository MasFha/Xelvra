package com.mdevz.xelvra.protocol.uri

import com.mdevz.xelvra.data.model.ServerProfile

sealed interface ProfileUriDecodeResult {

    data class Success(
        val profile: ServerProfile
    ) : ProfileUriDecodeResult

    data class Error(
        val error: ErrorType
    ) : ProfileUriDecodeResult

    enum class ErrorType {
        EMPTY_URI,
        UNSUPPORTED_PROTOCOL,
        INVALID_URI,
        INVALID_SHADOWSOCKS_URI,
        INVALID_SHADOWSOCKS_CREDENTIALS,
        INVALID_SHADOWSOCKS_ENDPOINT,
        INVALID_ADDRESS,
        INVALID_PORT
    }
}
