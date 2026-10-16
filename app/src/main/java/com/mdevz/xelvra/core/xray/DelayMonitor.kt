package com.mdevz.xelvra.core.xray

import com.mdevz.xelvra.core.log.AppLog
import java.util.concurrent.TimeoutException

object DelayMonitor {

    private val lock = Any()

    @Volatile
    private var generation = 0L

    private var worker: Thread? = null

    private var qualityState: DelayQualityState =
        DelayQualityState.NotAvailable

    fun getQualityState(): DelayQualityState =
        synchronized(lock) {
            qualityState
        }

    fun start(
        url: String,
        intervalSeconds: Int,
        timeoutSeconds: Int
    ) {
        require(url.isNotBlank()) {
            "Delay test URL must not be blank"
        }

        require(intervalSeconds > 0) {
            "Delay interval must be greater than zero"
        }

        require(timeoutSeconds > 0) {
            "Delay timeout must be greater than zero"
        }

        val localGeneration: Long

        synchronized(lock) {
            generation += 1L
            localGeneration = generation
            qualityState = DelayQualityState.Measuring

            val previousWorker = worker

            worker = null

            previousWorker?.interrupt()

            if (
                previousWorker != null &&
                previousWorker !== Thread.currentThread()
            ) {
                try {
                    previousWorker.join(
                        WORKER_STOP_WAIT_MS
                    )
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                }
            }

            worker =
                Thread(
                    {
                        runLoop(
                            generation = localGeneration,
                            url = url.trim(),
                            intervalSeconds = intervalSeconds,
                            timeoutSeconds = timeoutSeconds
                        )
                    },
                    "XelvraDelayMonitor"
                ).apply {
                    isDaemon = true
                    start()
                }
        }
    }

    fun stop() {
        val thread: Thread?

        synchronized(lock) {
            generation += 1L
            qualityState = DelayQualityState.NotAvailable
            thread = worker
            worker = null
        }

        thread?.interrupt()
    }

    private fun runLoop(
        generation: Long,
        url: String,
        intervalSeconds: Int,
        timeoutSeconds: Int
    ) {
        while (
            isCurrent(generation) &&
            !Thread.currentThread().isInterrupted
        ) {
            if (!XrayRuntimeController.isRunning()) {
                if (!sleepInterval(
                        generation,
                        intervalSeconds
                    )
                ) {
                    return
                }

                continue
            }

            try {
                val delay =
                    XrayRuntimeController.measureDelay(
                        url = url,
                        timeoutSeconds = timeoutSeconds
                    )

                if (!isCurrent(generation)) {
                    return
                }

                if (delay >= 0L) {
                    publishQualityResult(
                        generation,
                        DelayQualityState.Success(
                            milliseconds = delay,
                            measuredAt = System.currentTimeMillis()
                        ),
                        "Ping : ${delay} ms"
                    )
                } else {
                    publishQualityResult(
                        generation,
                        DelayQualityState.Failed(
                            measuredAt = System.currentTimeMillis()
                        ),
                        "Ping : failed",
                        AppLog.Level.WARNING
                    )
                }
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            } catch (error: TimeoutException) {
                if (!isCurrent(generation)) {
                    return
                }

                publishQualityResult(
                    generation,
                    DelayQualityState.Timeout(
                        measuredAt = System.currentTimeMillis()
                    ),
                    "Ping : timeout",
                    AppLog.Level.WARNING
                )
            } catch (error: Throwable) {
                if (!isCurrent(generation)) {
                    return
                }

                if (
                    Thread.currentThread()
                        .isInterrupted
                ) {
                    return
                }

                publishQualityResult(
                    generation,
                    DelayQualityState.Failed(
                        measuredAt = System.currentTimeMillis()
                    ),
                    "Ping : failed",
                    AppLog.Level.WARNING
                )
            }

            if (!sleepInterval(
                    generation,
                    intervalSeconds
                )
            ) {
                return
            }
        }
    }

    private fun sleepInterval(
        generation: Long,
        intervalSeconds: Int
    ): Boolean {
        if (!isCurrent(generation)) {
            return false
        }

        return try {
            Thread.sleep(
                intervalSeconds * 1000L
            )

            isCurrent(generation)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    private fun isCurrent(
        candidate: Long
    ): Boolean {
        return generation == candidate
    }

    private fun publishQualityResult(
        candidate: Long,
        state: DelayQualityState,
        message: String,
        level: AppLog.Level = AppLog.Level.INFO
    ) {
        synchronized(lock) {
            if (
                generation != candidate ||
                Thread.currentThread().isInterrupted
            ) {
                return
            }

            qualityState = state
            AppLog.xray(
                message = message,
                level = level
            )
        }
    }

    private const val WORKER_STOP_WAIT_MS =
        1500L
}
