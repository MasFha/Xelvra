package com.mdevz.xelvra.core.vpn

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet

object VpnRuntimeManager {

    private val mainHandler =
        Handler(Looper.getMainLooper())

    private val listeners =
        CopyOnWriteArraySet<
            (VpnRuntimeState) -> Unit
        >()

    @Volatile
    private var currentState: VpnRuntimeState =
        VpnRuntimeState.Disconnected

    @Volatile
    private var sessionProfileId: String? =
        null

    fun getState(): VpnRuntimeState {
        return currentState
    }

    fun getSessionProfileId(): String? {
        return sessionProfileId
    }

    internal fun setSessionProfileId(
        profileId: String
    ) {
        require(profileId.isNotBlank()) {
            "Session profile ID must not be blank"
        }

        sessionProfileId = profileId
    }

    internal fun clearSessionProfileId() {
        sessionProfileId = null
    }

    fun isSessionActive(): Boolean {
        return when (currentState) {
            VpnRuntimeState.Preparing,
            VpnRuntimeState.Connecting,
            is VpnRuntimeState.Connected,
            VpnRuntimeState.WaitingForNetwork,
            VpnRuntimeState.Reconnecting,
            VpnRuntimeState.Disconnecting -> true

            VpnRuntimeState.Disconnected,
            is VpnRuntimeState.Failed -> false
        }
    }

    fun addListener(
        listener: (VpnRuntimeState) -> Unit
    ) {
        listeners.add(listener)

        dispatch(
            listener,
            currentState
        )
    }

    fun removeListener(
        listener: (VpnRuntimeState) -> Unit
    ) {
        listeners.remove(listener)
    }

    internal fun updateState(
        state: VpnRuntimeState
    ) {
        currentState = state

        listeners.forEach { listener ->
            dispatch(
                listener,
                state
            )
        }
    }

    private fun dispatch(
        listener: (VpnRuntimeState) -> Unit,
        state: VpnRuntimeState
    ) {
        if (
            Looper.myLooper() ==
            Looper.getMainLooper()
        ) {
            listener(state)
        } else {
            mainHandler.post {
                if (listeners.contains(listener)) {
                    listener(state)
                }
            }
        }
    }
}
