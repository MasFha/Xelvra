package com.mdevz.xelvra.protocol.validator

import com.mdevz.xelvra.data.model.Protocol
import com.mdevz.xelvra.data.model.ProtocolOptions
import com.mdevz.xelvra.data.model.Security
import com.mdevz.xelvra.data.model.ServerProfile
import com.mdevz.xelvra.data.model.Transport
import java.util.UUID

object ProfileValidator {

    fun validate(
        profile: ServerProfile
    ): ProfileValidation {

        if (profile.remarks.isBlank()) {
            return invalid(
                ProfileValidation.Field.REMARKS,
                ProfileValidation.Error.REMARKS_REQUIRED
            )
        }

        if (profile.address.isBlank()) {
            return invalid(
                ProfileValidation.Field.ADDRESS,
                ProfileValidation.Error.ADDRESS_REQUIRED
            )
        }

        if (profile.port !in 1..65535) {
            return invalid(
                ProfileValidation.Field.PORT,
                ProfileValidation.Error.PORT_RANGE
            )
        }

        val protocolOptionsMatch = when (profile.protocol) {
            Protocol.VMESS ->
                profile.protocolOptions is ProtocolOptions.VMess

            Protocol.VLESS ->
                profile.protocolOptions is ProtocolOptions.VLess

            Protocol.TROJAN ->
                profile.protocolOptions is ProtocolOptions.Trojan

            Protocol.SHADOWSOCKS ->
                profile.protocolOptions is ProtocolOptions.Shadowsocks
        }

        if (!protocolOptionsMatch) {
            return invalid(
                ProfileValidation.Field.PROTOCOL_OPTIONS,
                ProfileValidation.Error.PROTOCOL_OPTIONS_MISMATCH
            )
        }

        when (
            val options =
                profile.protocolOptions
        ) {
            is ProtocolOptions.VMess -> {
                if (!isValidUuid(options.uuid)) {
                    return invalid(
                        ProfileValidation.Field.UUID,
                        ProfileValidation.Error.UUID_INVALID
                    )
                }
            }

            is ProtocolOptions.VLess -> {
                if (!isValidUuid(options.uuid)) {
                    return invalid(
                        ProfileValidation.Field.UUID,
                        ProfileValidation.Error.UUID_INVALID
                    )
                }
            }

            is ProtocolOptions.Trojan -> {
                if (options.password.isBlank()) {
                    return invalid(
                        ProfileValidation.Field.PASSWORD,
                        ProfileValidation.Error.PASSWORD_REQUIRED
                    )
                }
            }

            is ProtocolOptions.Shadowsocks -> {
                if (options.method.isBlank()) {
                    return invalid(
                        ProfileValidation.Field.METHOD,
                        ProfileValidation.Error.METHOD_REQUIRED
                    )
                }

                if (options.password.isBlank()) {
                    return invalid(
                        ProfileValidation.Field.PASSWORD,
                        ProfileValidation.Error.PASSWORD_REQUIRED
                    )
                }
            }
        }

        if (profile.transport is Transport.Http) {
            return invalid(
                ProfileValidation.Field.TRANSPORT,
                ProfileValidation.Error.TRANSPORT_UNSUPPORTED
            )
        }

        if (profile.security is Security.Reality) {
            if (
                profile.transport !is Transport.Tcp &&
                profile.transport !is Transport.Grpc &&
                profile.transport !is Transport.XHttp
            ) {
                return invalid(
                    ProfileValidation.Field.TRANSPORT,
                    ProfileValidation.Error
                        .REALITY_TRANSPORT_UNSUPPORTED
                )
            }

            val reality =
                profile.security

            if (reality.publicKey.isBlank()) {
                return invalid(
                    ProfileValidation.Field.REALITY_PUBLIC_KEY,
                    ProfileValidation.Error
                        .REALITY_PUBLIC_KEY_REQUIRED
                )
            }

            if (reality.shortId.isBlank()) {
                return invalid(
                    ProfileValidation.Field.REALITY_SHORT_ID,
                    ProfileValidation.Error
                        .REALITY_SHORT_ID_REQUIRED
                )
            }
        }

        return ProfileValidation.Valid
    }

    private fun isValidUuid(
        value: String
    ): Boolean {
        return try {
            UUID.fromString(value)
            true
        } catch (
            _: IllegalArgumentException
        ) {
            false
        }
    }

    private fun invalid(
        field: ProfileValidation.Field,
        error: ProfileValidation.Error
    ): ProfileValidation.Invalid {
        return ProfileValidation.Invalid(
            field = field,
            error = error
        )
    }
}
