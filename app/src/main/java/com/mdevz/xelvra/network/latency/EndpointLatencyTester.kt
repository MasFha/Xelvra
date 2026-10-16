package com.mdevz.xelvra.network.latency

import android.os.SystemClock
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

data class EndpointKey(
    val address: String,
    val port: Int
) {
    companion object {
        fun from(
            address: String,
            port: Int
        ): EndpointKey {
            return EndpointKey(
                address = normalizeAddress(address),
                port = port
            )
        }

        private fun normalizeAddress(
            value: String
        ): String {
            return value
                .trim()
                .removePrefix("[")
                .removeSuffix("]")
                .removeSuffix(".")
                .lowercase()
        }
    }
}

sealed interface EndpointLatencyResult {

    data class Success(
        val milliseconds: Long
    ) : EndpointLatencyResult

    data object Timeout : EndpointLatencyResult

    data object Failed : EndpointLatencyResult
}

object EndpointLatencyTester {

    private const val SAMPLE_COUNT = 3
    private const val CONNECT_TIMEOUT_MS = 3_000

    fun test(
        address: String,
        port: Int
    ): EndpointLatencyResult {
        if (
            address.isBlank() ||
            port !in 1..65535
        ) {
            return EndpointLatencyResult.Failed
        }

        val samples = mutableListOf<Long>()
        var timeoutCount = 0

        repeat(SAMPLE_COUNT) {
            when (
                val sample = measureOnce(
                    address = address
                        .trim()
                        .removePrefix("[")
                        .removeSuffix("]"),
                    port = port
                )
            ) {
                is SampleResult.Success -> {
                    samples += sample.milliseconds
                }

                SampleResult.Timeout -> {
                    timeoutCount++
                }

                SampleResult.Failed -> Unit
            }
        }

        if (samples.size >= 2) {
            val sorted = samples.sorted()

            val median = if (sorted.size == 2) {
                (
                    sorted[0] +
                        sorted[1]
                    ) / 2L
            } else {
                sorted[1]
            }

            return EndpointLatencyResult.Success(
                milliseconds = median
                    .coerceAtLeast(1L)
            )
        }

        return if (timeoutCount > 0) {
            EndpointLatencyResult.Timeout
        } else {
            EndpointLatencyResult.Failed
        }
    }

    private fun measureOnce(
        address: String,
        port: Int
    ): SampleResult {
        val socket = Socket()

        return try {
            val startedAt =
                SystemClock.elapsedRealtimeNanos()

            socket.connect(
                InetSocketAddress(
                    address,
                    port
                ),
                CONNECT_TIMEOUT_MS
            )

            val elapsedNanos =
                SystemClock.elapsedRealtimeNanos() -
                    startedAt

            val milliseconds =
                (elapsedNanos + 999_999L) /
                    1_000_000L

            SampleResult.Success(
                milliseconds = milliseconds
                    .coerceAtLeast(1L)
            )
        } catch (_: SocketTimeoutException) {
            SampleResult.Timeout
        } catch (_: Exception) {
            SampleResult.Failed
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
                // Nothing to do.
            }
        }
    }

    private sealed interface SampleResult {

        data class Success(
            val milliseconds: Long
        ) : SampleResult

        data object Timeout : SampleResult

        data object Failed : SampleResult
    }
}
