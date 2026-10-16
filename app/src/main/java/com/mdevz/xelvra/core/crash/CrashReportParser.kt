package com.mdevz.xelvra.core.crash

import java.io.File

data class CrashReportSummary(
    val reportId: String,
    val exceptionType: String,
    val className: String?,
    val methodName: String?,
    val fileName: String?,
    val lineNumber: Int?,
    val reportFile: File
)

object CrashReportParser {

    fun parse(
        file: File
    ): CrashReportSummary? {
        return runCatching {
            val fields =
                file.useLines(Charsets.UTF_8) { lines ->
                    lines
                        .take(80)
                        .mapNotNull { line ->
                            val separator =
                                line.indexOf(':')

                            if (separator <= 0) {
                                null
                            } else {
                                val key =
                                    line.substring(
                                        0,
                                        separator
                                    ).trim()

                                val value =
                                    line.substring(
                                        separator + 1
                                    ).trim()

                                key to value
                            }
                        }
                        .toMap()
                }

            val reportId =
                fields["Report ID"]
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: return null

            val exceptionType =
                fields["Exception"]
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: "Unknown"

            CrashReportSummary(
                reportId = reportId,
                exceptionType = exceptionType,
                className =
                    fields["Class"]
                        ?.nullIfUnavailable(),
                methodName =
                    fields["Method"]
                        ?.nullIfUnavailable(),
                fileName =
                    fields["File"]
                        ?.nullIfUnavailable(),
                lineNumber =
                    fields["Line"]
                        ?.toIntOrNull(),
                reportFile = file
            )
        }.getOrNull()
    }

    private fun String.nullIfUnavailable(): String? {
        return takeUnless {
            isBlank() ||
                equals(
                    "unknown",
                    ignoreCase = true
                ) ||
                equals(
                    "not available",
                    ignoreCase = true
                )
        }
    }
}
