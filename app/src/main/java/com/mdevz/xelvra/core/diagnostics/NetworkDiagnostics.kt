package com.mdevz.xelvra.core.diagnostics

import android.os.Looper
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

enum class NetworkDiagnosticStatus {
    SUCCESS,
    FAILED,
    TIMEOUT
}

data class NetworkDiagnosticResult(
    val status: NetworkDiagnosticStatus,
    val latencyMs: Long? = null,
    val detail: String? = null
)

data class NetworkDiagnosticReport(
    val dns: NetworkDiagnosticResult,
    val internet: NetworkDiagnosticResult
)

object NetworkDiagnostics {

    private const val DNS_HOST = "example.com"
    private const val HTTPS_URL = "https://example.com/"
    private const val TIMEOUT_MS = 5000

    fun runTests(): NetworkDiagnosticReport {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "Network diagnostics must run off the main thread"
        }

        return NetworkDiagnosticReport(
            dns = testDns(),
            internet = testHttps()
        )
    }

    private fun testDns(): NetworkDiagnosticResult {
        val executor = Executors.newSingleThreadExecutor {
            task ->
            Thread(task, "xelvra-dns-diagnostic").apply {
                isDaemon = true
            }
        }

        val future = executor.submit(
            Callable {
                val start = System.nanoTime()
                val addresses = InetAddress.getAllByName(DNS_HOST)
                val elapsed = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - start
                )

                if (addresses.isEmpty()) {
                    NetworkDiagnosticResult(
                        NetworkDiagnosticStatus.FAILED
                    )
                } else {
                    NetworkDiagnosticResult(
                        NetworkDiagnosticStatus.SUCCESS,
                        elapsed
                    )
                }
            }
        )

        return try {
            future.get(
                TIMEOUT_MS.toLong(),
                TimeUnit.MILLISECONDS
            )
        } catch (_: TimeoutException) {
            future.cancel(true)
            NetworkDiagnosticResult(
                NetworkDiagnosticStatus.TIMEOUT
            )
        } catch (_: Exception) {
            NetworkDiagnosticResult(
                NetworkDiagnosticStatus.FAILED
            )
        } finally {
            executor.shutdownNow()
        }
    }

    private fun testHttps(): NetworkDiagnosticResult {
        var connection: HttpURLConnection? = null

        return try {
            val start = System.nanoTime()

            connection = (
                URL(HTTPS_URL).openConnection()
                    as HttpURLConnection
            ).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = false
                requestMethod = "GET"
                useCaches = false
            }

            val response = connection.responseCode

            val elapsed = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - start
            )

            if (response in 200..399) {
                NetworkDiagnosticResult(
                    NetworkDiagnosticStatus.SUCCESS,
                    elapsed
                )
            } else {
                NetworkDiagnosticResult(
                    NetworkDiagnosticStatus.FAILED,
                    elapsed,
                    "HTTP $response"
                )
            }
        } catch (_: java.net.SocketTimeoutException) {
            NetworkDiagnosticResult(
                NetworkDiagnosticStatus.TIMEOUT
            )
        } catch (_: Exception) {
            NetworkDiagnosticResult(
                NetworkDiagnosticStatus.FAILED
            )
        } finally {
            connection?.disconnect()
        }
    }
}
