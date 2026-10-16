package com.mdevz.xelvra.core.traffic

import android.net.TrafficStats
import android.os.Process
import android.os.SystemClock
import java.util.ArrayDeque

object TrafficMonitor {

    private const val SAMPLE_INTERVAL_MS = 1000L
    private const val MAX_HISTORY = 60

    private val lock = Any()

    @Volatile
    private var generation = 0L

    private var worker: Thread? = null

    private val uploadHistory =
        ArrayDeque<Long>(MAX_HISTORY)

    private val downloadHistory =
        ArrayDeque<Long>(MAX_HISTORY)

    @Volatile
    private var snapshot =
        TrafficSnapshot()

    fun start() {
        synchronized(lock) {
            stopLocked()

            generation += 1L
            val currentGeneration = generation

            val uid = Process.myUid()

            val initialRx =
                TrafficStats.getUidRxBytes(uid)

            val initialTx =
                TrafficStats.getUidTxBytes(uid)

            if (
                initialRx == TrafficStats.UNSUPPORTED.toLong() ||
                initialTx == TrafficStats.UNSUPPORTED.toLong()
            ) {
                snapshot = TrafficSnapshot()
                return
            }

            uploadHistory.clear()
            downloadHistory.clear()

            snapshot = TrafficSnapshot()

            worker =
                Thread(
                    {
                        runMonitor(
                            generation = currentGeneration,
                            uid = uid,
                            baselineRx = initialRx,
                            baselineTx = initialTx
                        )
                    },
                    "XelvraTrafficMonitor"
                ).apply {
                    start()
                }
        }
    }

    fun stop() {
        synchronized(lock) {
            stopLocked()
        }
    }

    fun getSnapshot(): TrafficSnapshot {
        return snapshot
    }

    private fun runMonitor(
        generation: Long,
        uid: Int,
        baselineRx: Long,
        baselineTx: Long
    ) {
        var previousRx = baselineRx
        var previousTx = baselineTx
        var previousTime =
            SystemClock.elapsedRealtime()

        while (isGenerationActive(generation)) {
            try {
                Thread.sleep(SAMPLE_INTERVAL_MS)
            } catch (_: InterruptedException) {
                break
            }

            if (!isGenerationActive(generation)) {
                break
            }

            val now =
                SystemClock.elapsedRealtime()

            val rx =
                TrafficStats.getUidRxBytes(uid)

            val tx =
                TrafficStats.getUidTxBytes(uid)

            if (
                rx == TrafficStats.UNSUPPORTED.toLong() ||
                tx == TrafficStats.UNSUPPORTED.toLong()
            ) {
                continue
            }

            val elapsed =
                (now - previousTime)
                    .coerceAtLeast(1L)

            val rxDelta =
                (rx - previousRx)
                    .coerceAtLeast(0L)

            val txDelta =
                (tx - previousTx)
                    .coerceAtLeast(0L)

            val downloadRate =
                calculateBytesPerSecond(
                    rxDelta,
                    elapsed
                )

            val uploadRate =
                calculateBytesPerSecond(
                    txDelta,
                    elapsed
                )

            val sessionDownload =
                (rx - baselineRx)
                    .coerceAtLeast(0L)

            val sessionUpload =
                (tx - baselineTx)
                    .coerceAtLeast(0L)

            val generationIsCurrent =
                synchronized(lock) {
                    if (generation != this.generation) {
                        false
                    } else {
                        appendHistory(
                            uploadHistory,
                            uploadRate
                        )

                        appendHistory(
                            downloadHistory,
                            downloadRate
                        )

                        snapshot =
                            TrafficSnapshot(
                                uploadBytesPerSecond =
                                    uploadRate,
                                downloadBytesPerSecond =
                                    downloadRate,
                                sessionUploadBytes =
                                    sessionUpload,
                                sessionDownloadBytes =
                                    sessionDownload,
                                uploadHistory =
                                    uploadHistory.toList(),
                                downloadHistory =
                                    downloadHistory.toList()
                            )

                        true
                    }
                }

            if (!generationIsCurrent) {
                break
            }

            previousRx = rx
            previousTx = tx
            previousTime = now
        }
    }

    private fun isGenerationActive(
        expectedGeneration: Long
    ): Boolean {
        return generation == expectedGeneration
    }

    private fun calculateBytesPerSecond(
        bytesDelta: Long,
        elapsedMs: Long
    ): Long {
        if (bytesDelta <= 0L) {
            return 0L
        }

        val rate =
            bytesDelta.toDouble() *
                1000.0 /
                elapsedMs.coerceAtLeast(1L).toDouble()

        return when {
            rate >= Long.MAX_VALUE.toDouble() ->
                Long.MAX_VALUE
            rate <= 0.0 ->
                0L
            else ->
                rate.toLong()
        }
    }

    private fun appendHistory(
        history: ArrayDeque<Long>,
        value: Long
    ) {
        if (history.size >= MAX_HISTORY) {
            history.removeFirst()
        }

        history.addLast(value)
    }

    private fun stopLocked() {
        generation += 1L

        worker?.interrupt()
        worker = null

        uploadHistory.clear()
        downloadHistory.clear()

        snapshot = TrafficSnapshot()
    }
}
