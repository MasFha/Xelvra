package com.mdevz.xelvra.core.diagnostics

import com.mdevz.xelvra.core.vpn.VpnRuntimeManager
import com.mdevz.xelvra.core.vpn.VpnRuntimeState
import com.mdevz.xelvra.core.xray.XrayRuntimeController

enum class DiagnosticVpnStatus {
    DISCONNECTED,
    PREPARING,
    CONNECTING,
    CONNECTED,
    WAITING_FOR_NETWORK,
    RECONNECTING,
    DISCONNECTING,
    FAILED
}

data class ConnectionDiagnosticSnapshot(
    val vpnStatus: DiagnosticVpnStatus,
    val xrayRunning: Boolean,
    val connectedAt: Long?,
    val vpnError: String?
)

object ConnectionDiagnostics {

    fun snapshot(): ConnectionDiagnosticSnapshot {
        val state = VpnRuntimeManager.getState()

        val status = when (state) {
            VpnRuntimeState.Disconnected ->
                DiagnosticVpnStatus.DISCONNECTED

            VpnRuntimeState.Preparing ->
                DiagnosticVpnStatus.PREPARING

            VpnRuntimeState.Connecting ->
                DiagnosticVpnStatus.CONNECTING

            is VpnRuntimeState.Connected ->
                DiagnosticVpnStatus.CONNECTED

            VpnRuntimeState.WaitingForNetwork ->
                DiagnosticVpnStatus.WAITING_FOR_NETWORK

            VpnRuntimeState.Reconnecting ->
                DiagnosticVpnStatus.RECONNECTING

            VpnRuntimeState.Disconnecting ->
                DiagnosticVpnStatus.DISCONNECTING

            is VpnRuntimeState.Failed ->
                DiagnosticVpnStatus.FAILED
        }

        val connectedAt =
            (state as? VpnRuntimeState.Connected)
                ?.startedAt

        val error =
            (state as? VpnRuntimeState.Failed)
                ?.error

        val xrayRunning = runCatching {
            XrayRuntimeController.isRunning()
        }.getOrDefault(false)

        return ConnectionDiagnosticSnapshot(
            vpnStatus = status,
            xrayRunning = xrayRunning,
            connectedAt = connectedAt,
            vpnError = error
        )
    }
}
