package com.mdevz.xelvra.protocol.validator

import com.mdevz.xelvra.data.model.Protocol
import com.mdevz.xelvra.data.model.ProtocolOptions
import com.mdevz.xelvra.data.model.Security
import com.mdevz.xelvra.data.model.ServerProfile
import com.mdevz.xelvra.data.model.Transport
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileValidatorTest {

    private fun profile(
        transport: Transport,
        security: Security = Security.None
    ): ServerProfile {
        return ServerProfile(
            id = "validator-test",
            remarks = "Validation test",
            protocol = Protocol.VLESS,
            address = "example.com",
            port = 443,
            protocolOptions = ProtocolOptions.VLess(
                uuid = "123e4567-e89b-42d3-a456-426614174000"
            ),
            transport = transport,
            security = security
        )
    }

    private fun reality() = Security.Reality(
        serverName = "example.com",
        fingerprint = "chrome",
        publicKey = "test-public-key",
        shortId = "abcd1234"
    )

    @Test
    fun legacyHttpIsRejected() {
        assertEquals(
            ProfileValidation.Invalid(
                ProfileValidation.Field.TRANSPORT,
                ProfileValidation.Error.TRANSPORT_UNSUPPORTED
            ),
            ProfileValidator.validate(
                profile(Transport.Http())
            )
        )
    }

    @Test
    fun realityWebSocketIsRejected() {
        assertRealityTransportRejected(
            Transport.WebSocket()
        )
    }

    @Test
    fun realityHttpUpgradeIsRejected() {
        assertRealityTransportRejected(
            Transport.HttpUpgrade()
        )
    }

    private fun assertRealityTransportRejected(
        transport: Transport
    ) {
        assertEquals(
            ProfileValidation.Invalid(
                ProfileValidation.Field.TRANSPORT,
                ProfileValidation.Error
                    .REALITY_TRANSPORT_UNSUPPORTED
            ),
            ProfileValidator.validate(
                profile(transport, reality())
            )
        )
    }

    @Test
    fun realityTcpIsAccepted() {
        assertEquals(
            ProfileValidation.Valid,
            ProfileValidator.validate(
                profile(Transport.Tcp, reality())
            )
        )
    }

    @Test
    fun realityGrpcIsAccepted() {
        assertEquals(
            ProfileValidation.Valid,
            ProfileValidator.validate(
                profile(Transport.Grpc(), reality())
            )
        )
    }

    @Test
    fun realityXHttpIsAccepted() {
        assertEquals(
            ProfileValidation.Valid,
            ProfileValidator.validate(
                profile(Transport.XHttp(), reality())
            )
        )
    }

    @Test
    fun httpUpgradeWithoutRealityIsAccepted() {
        assertEquals(
            ProfileValidation.Valid,
            ProfileValidator.validate(
                profile(Transport.HttpUpgrade())
            )
        )
    }
}
