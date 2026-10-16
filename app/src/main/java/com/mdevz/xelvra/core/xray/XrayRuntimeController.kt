package com.mdevz.xelvra.core.xray

import android.content.Context
import com.mdevz.xelvra.core.log.AppLog
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object XrayRuntimeController {

    private val lock = Any()

    private val delayMeasurementLock =
        Any()

    private val controller: CoreController by lazy {
        Libv2ray.newCoreController(
            object : CoreCallbackHandler {

                override fun startup(): Long {
                    AppLog.xray("Core startup")
                    return 0L
                }

                override fun shutdown(): Long {
                    AppLog.xray("Core shutdown")
                    return 0L
                }

                override fun onEmitStatus(
                    code: Long,
                    message: String?
                ): Long {
                    if (code != 0L) {
                        AppLog.xray(
                            "Core status code=$code"
                        )
                    }
                    return 0L
                }
            }
        )
    }

    fun initialize(
        context: Context
    ) {
        synchronized(lock) {
            Libv2ray.touch()

            XrayAssetManager.prepareForCore(
                context
            )

            Libv2ray.initCoreEnv(
                context.filesDir.absolutePath,
                ""
            )

            controller
        }
    }

    fun start(
        config: String,
        tunFd: Int
    ) {
        require(tunFd > 0) {
            "A valid TUN file descriptor is required"
        }

        synchronized(lock) {
            check(!controller.isRunning) {
                "Xray core is already running"
            }

            XrayAssetCoordinator.withCoreStartup {
                AppLog.xray(
                    "Starting core with TUN fd=$tunFd"
                )

                controller.startLoop(
                    config,
                    tunFd
                )

                AppLog.xray(
                    "Core running=${controller.isRunning}"
                )

                check(controller.isRunning) {
                    "Xray core did not enter running state"
                }
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            try {
                if (controller.isRunning) {
                    AppLog.xray("Stopping core")
                    controller.stopLoop()
                }
            } finally {
                XrayAssetCoordinator.markCoreStopped()
            }
        }
    }

    fun isRunning(): Boolean {
        return controller.isRunning
    }

    fun measureDelay(
        url: String,
        timeoutSeconds: Int
    ): Long {
        require(url.isNotBlank()) {
            "Delay test URL must not be blank"
        }

        require(timeoutSeconds > 0) {
            "Timeout must be greater than zero"
        }

        synchronized(delayMeasurementLock) {
            check(controller.isRunning) {
                "Xray core is not running"
            }

            val executor =
                Executors.newSingleThreadExecutor()

            val future =
                executor.submit<Long> {
                    controller.measureDelay(
                        url.trim()
                    )
                }

            return try {
                future.get(
                    timeoutSeconds.toLong(),
                    TimeUnit.SECONDS
                )
            } catch (error: TimeoutException) {
                future.cancel(true)

                throw TimeoutException(
                    "Delay test timed out after " +
                        "$timeoutSeconds seconds"
                )
            } finally {
                executor.shutdownNow()
            }
        }
    }


}
