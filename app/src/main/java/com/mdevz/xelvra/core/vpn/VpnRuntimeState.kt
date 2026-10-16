package com.mdevz.xelvra.core.vpn

sealed interface VpnRuntimeState {

    data object Disconnected : VpnRuntimeState

    data object Preparing : VpnRuntimeState

    data object Connecting : VpnRuntimeState

    data class Connected(
        val startedAt: Long
    ) : VpnRuntimeState

    data object WaitingForNetwork : VpnRuntimeState

    data object Reconnecting : VpnRuntimeState

    data object Disconnecting : VpnRuntimeState

    data class Failed(
        val error: String
    ) : VpnRuntimeState
}
