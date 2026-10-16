package com.mdevz.xelvra.core.xray

object XrayAssetCoordinator {

    private val lock =
        Any()

    private var coreRunning =
        false

    private var coreStarting =
        false

    fun <T> withCoreStartup(
        block: () -> T
    ): T {
        synchronized(lock) {
            check(!coreStarting) {
                "Xray core startup is already in progress"
            }

            coreStarting = true

            try {
                val result =
                    block()

                coreRunning = true

                return result
            } finally {
                coreStarting = false
            }
        }
    }

    fun markCoreStopped() {
        synchronized(lock) {
            coreRunning = false
            coreStarting = false
        }
    }

    fun <T> withAssetMutation(
        block: () -> T
    ): T {
        synchronized(lock) {
            check(
                !coreRunning &&
                    !coreStarting
            ) {
                "Disconnect VPN before changing asset files"
            }

            return block()
        }
    }

    fun <T> withAssetRead(
        block: () -> T
    ): T {
        synchronized(lock) {
            return block()
        }
    }

    fun isMutationAllowed(): Boolean {
        synchronized(lock) {
            return !coreRunning &&
                !coreStarting
        }
    }
}
