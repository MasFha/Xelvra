package com.mdevz.xelvra.core.crash

import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import com.mdevz.xelvra.core.vpn.VpnRuntimeManager
import com.mdevz.xelvra.core.vpn.VpnRuntimeState
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class GlobalCrashHandler private constructor(
    private val context: Context,
    private val previousHandler:
        Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    private val handling =
        AtomicBoolean(false)

    override fun uncaughtException(
        thread: Thread,
        throwable: Throwable
    ) {
        if (
            handling.compareAndSet(
                false,
                true
            )
        ) {
            runCatching {
                val report =
                    createReport(
                        thread,
                        throwable
                    )

                CrashReportStore
                    .recordCrashDuringPresentation(
                        context,
                        report
                    )

                CrashReportStore.save(
                    context,
                    report
                )
            }
        }

        val previous =
            previousHandler

        if (
            previous != null &&
            previous !== this
        ) {
            previous.uncaughtException(
                thread,
                throwable
            )
        } else {
            Process.killProcess(
                Process.myPid()
            )
        }
    }

    private fun createReport(
        thread: Thread,
        throwable: Throwable
    ): CrashReport {
        val root =
            rootCause(throwable)

        val location =
            findXelvraFrame(root)
                ?: findXelvraFrame(
                    throwable
                )

        val reportId =
            buildReportId()

        val timestamp =
            System.currentTimeMillis()

        val exceptionType =
            root.javaClass.simpleName
                .ifBlank {
                    root.javaClass.name
                }

        val exceptionMessage =
            CrashSanitizer
                .sanitize(
                    root.message
                )
                .takeIf {
                    it.isNotBlank()
                }

        val fullReport =
            buildFullReport(
                reportId = reportId,
                timestamp = timestamp,
                thread = thread,
                throwable = throwable,
                root = root,
                location = location
            )

        return CrashReport(
            reportId = reportId,
            timestamp = timestamp,
            exceptionType =
                exceptionType,
            exceptionMessage =
                exceptionMessage,
            className =
                location?.className,
            methodName =
                location?.methodName,
            fileName =
                location?.fileName,
            lineNumber =
                location
                    ?.lineNumber
                    ?.takeIf {
                        it > 0
                    },
            fullReport =
                fullReport
        )
    }

    private fun buildFullReport(
        reportId: String,
        timestamp: Long,
        thread: Thread,
        throwable: Throwable,
        root: Throwable,
        location: StackTraceElement?
    ): String {
        val packageInfo =
            runCatching {
                context.packageManager
                    .getPackageInfo(
                        context.packageName,
                        0
                    )
            }.getOrNull()

        val versionName =
            packageInfo?.versionName
                ?: "unknown"

        val versionCode =
            packageInfo?.versionCode?.toLong()
                ?.toString()
                ?: "unknown"

        val time =
            SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss Z",
                Locale.US
            ).format(
                Date(timestamp)
            )

        val uptime =
            formatDuration(
                SystemClock.elapsedRealtime()
            )

        val vpnState =
            vpnStateLabel()

        val stackTrace =
            stackTraceOf(
                throwable
            )

        return buildString {
            appendLine(
                "Xelvra Crash Report"
            )
            appendLine(
                "==================="
            )
            appendLine()
            appendLine(
                "Report ID      : $reportId"
            )
            appendLine(
                "Time           : $time"
            )
            appendLine(
                "App Version    : $versionName ($versionCode)"
            )
            appendLine(
                "Android        : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
            )
            appendLine(
                "Device         : ${safeDevice()}"
            )
            appendLine(
                "ABI            : ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}"
            )
            appendLine(
                "Process Uptime : $uptime"
            )
            appendLine(
                "Thread         : ${CrashSanitizer.sanitize(thread.name)}"
            )
            appendLine(
                "VPN State      : $vpnState"
            )
            appendLine()

            appendLine(
                "Primary Error"
            )
            appendLine(
                "-------------"
            )
            appendLine(
                "Exception : ${root.javaClass.name}"
            )
            appendLine(
                "Class     : ${location?.className ?: "unknown"}"
            )
            appendLine(
                "Method    : ${location?.methodName ?: "unknown"}"
            )
            appendLine(
                "File      : ${location?.fileName ?: "unknown"}"
            )
            appendLine(
                "Line      : ${
                    location
                        ?.lineNumber
                        ?.takeIf { it > 0 }
                        ?: "unknown"
                }"
            )
            appendLine()

            appendLine(
                "Message"
            )
            appendLine(
                "-------"
            )
            appendLine(
                CrashSanitizer.sanitize(
                    root.message
                ).ifBlank {
                    "(none)"
                }
            )
            appendLine()

            appendLine(
                "Stack Trace"
            )
            appendLine(
                "-----------"
            )
            appendLine(
                CrashSanitizer.sanitize(
                    stackTrace
                )
            )
            appendLine()

            appendLine(
                "Privacy"
            )
            appendLine(
                "-------"
            )
            appendLine(
                "Generated locally by Xelvra."
            )
            appendLine(
                "Known credentials, proxy URIs, tokens and UUIDs are redacted."
            )
        }
    }

    private fun rootCause(
        throwable: Throwable
    ): Throwable {
        var current =
            throwable

        val seen =
            HashSet<Throwable>()

        while (
            current.cause != null &&
            current.cause !== current &&
            seen.add(current)
        ) {
            current =
                current.cause
                    ?: break
        }

        return current
    }

    private fun findXelvraFrame(
        throwable: Throwable
    ): StackTraceElement? {
        var current: Throwable? =
            throwable

        val seen =
            HashSet<Throwable>()

        while (
            current != null &&
            seen.add(current)
        ) {
            current.stackTrace
                .firstOrNull {
                    it.className.startsWith(
                        XELVRA_PACKAGE_PREFIX
                    )
                }
                ?.let {
                    return it
                }

            current =
                current.cause
        }

        return null
    }

    private fun stackTraceOf(
        throwable: Throwable
    ): String {
        val writer =
            StringWriter()

        PrintWriter(writer).use {
            throwable.printStackTrace(it)
        }

        return writer.toString()
    }

    private fun safeDevice(): String {
        val manufacturer =
            CrashSanitizer.sanitize(
                Build.MANUFACTURER
            )

        val model =
            CrashSanitizer.sanitize(
                Build.MODEL
            )

        return listOf(
            manufacturer,
            model
        )
            .filter {
                it.isNotBlank()
            }
            .joinToString(" ")
            .ifBlank {
                "unknown"
            }
    }

    private fun vpnStateLabel(): String {
        return when (
            VpnRuntimeManager.getState()
        ) {
            VpnRuntimeState.Disconnected ->
                "Disconnected"

            VpnRuntimeState.Preparing ->
                "Preparing"

            VpnRuntimeState.Connecting ->
                "Connecting"

            is VpnRuntimeState.Connected ->
                "Connected"

            VpnRuntimeState.WaitingForNetwork ->
                "WaitingForNetwork"

            VpnRuntimeState.Reconnecting ->
                "Reconnecting"

            VpnRuntimeState.Disconnecting ->
                "Disconnecting"

            is VpnRuntimeState.Failed ->
                "Failed"
        }
    }

    private fun buildReportId(): String {
        val value =
            UUID.randomUUID()
                .toString()
                .replace(
                    "-",
                    ""
                )
                .take(6)
                .uppercase(
                    Locale.US
                )

        return "XR-$value"
    }

    private fun formatDuration(
        milliseconds: Long
    ): String {
        val totalSeconds =
            milliseconds
                .coerceAtLeast(0L) /
                1000L

        val hours =
            totalSeconds / 3600L

        val minutes =
            (
                totalSeconds %
                    3600L
                ) / 60L

        val seconds =
            totalSeconds % 60L

        return String.format(
            Locale.US,
            "%02d:%02d:%02d",
            hours,
            minutes,
            seconds
        )
    }

    companion object {

        private const val XELVRA_PACKAGE_PREFIX =
            "com.mdevz.xelvra."

        fun install(
            context: Context
        ) {
            val current =
                Thread
                    .getDefaultUncaughtExceptionHandler()

            if (current is GlobalCrashHandler) {
                return
            }

            Thread
                .setDefaultUncaughtExceptionHandler(
                    GlobalCrashHandler(
                        context =
                            context.applicationContext,
                        previousHandler =
                            current
                    )
                )
        }
    }
}
