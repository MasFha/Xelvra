package com.mdevz.xelvra.protocol.validator

sealed interface ProfileValidation {

    data object Valid : ProfileValidation

    data class Invalid(
        val field: Field,
        val error: Error
    ) : ProfileValidation

    enum class Field {
        REMARKS,
        ADDRESS,
        PORT,
        UUID,
        PASSWORD,
        METHOD,
        TRANSPORT,
        REALITY_PUBLIC_KEY,
        REALITY_SHORT_ID
    }

    enum class Error {
        REMARKS_REQUIRED,
        ADDRESS_REQUIRED,
        PORT_RANGE,
        UUID_INVALID,
        PASSWORD_REQUIRED,
        METHOD_REQUIRED,
        TRANSPORT_UNSUPPORTED,
        REALITY_TRANSPORT_UNSUPPORTED,
        REALITY_PUBLIC_KEY_REQUIRED,
        REALITY_SHORT_ID_REQUIRED
    }
}
