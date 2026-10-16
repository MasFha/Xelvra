package com.mdevz.xelvra.core.crash

import android.content.Context
import java.io.File

object CrashReportStore {

    private const val DIRECTORY_NAME =
        "crash_reports"

    private const val MAX_REPORTS =
        10

    private const val FILE_PREFIX =
        "xelvra-crash-"

    private const val FILE_SUFFIX =
        ".txt"

    private const val PRESENTATION_PREFERENCES =
        "xelvra_crash_report_state"

    private const val KEY_PRESENTED_REPORT =
        "presented_report"

    private const val KEY_PRESENTING_REPORT =
        "presenting_report"

    private const val KEY_CRASHED_PRESENTATION_REPORT =
        "crashed_presentation_report"

    fun save(
        context: Context,
        report: CrashReport
    ): Boolean {
        return runCatching {
            val directory =
                File(
                    context.filesDir,
                    DIRECTORY_NAME
                )

            if (
                !directory.exists() &&
                !directory.mkdirs()
            ) {
                error(
                    "Unable to create crash report directory"
                )
            }

            val target =
                File(
                    directory,
                    FILE_PREFIX +
                        report.reportId +
                        FILE_SUFFIX
                )

            val temporary =
                File(
                    directory,
                    target.name + ".tmp"
                )

            temporary.writeText(
                report.fullReport,
                Charsets.UTF_8
            )

            if (target.exists()) {
                target.delete()
            }

            if (!temporary.renameTo(target)) {
                target.writeText(
                    report.fullReport,
                    Charsets.UTF_8
                )

                temporary.delete()
            }

            prune(directory)

            true
        }.getOrDefault(false)
    }

    fun latest(
        context: Context
    ): File? {
        return reportFiles(context)
            .maxByOrNull {
                it.lastModified()
            }
    }

    fun hasReport(
        context: Context
    ): Boolean {
        return latest(context) != null
    }

    fun pending(
        context: Context
    ): File? {
        val report =
            latest(context)
                ?: return null

        val preferences =
            context.getSharedPreferences(
                PRESENTATION_PREFERENCES,
                Context.MODE_PRIVATE
            )

        val presented =
            preferences.getString(
                KEY_PRESENTED_REPORT,
                null
            )

        if (report.name == presented) {
            return null
        }

        val crashedPresentationReport =
            preferences.getString(
                KEY_CRASHED_PRESENTATION_REPORT,
                null
            )

        if (
            crashedPresentationReport != null &&
            report.name == crashedPresentationReport
        ) {
            return null
        }

        return report
    }

    fun beginPresentation(
        context: Context,
        report: File
    ) {
        context.getSharedPreferences(
            PRESENTATION_PREFERENCES,
            Context.MODE_PRIVATE
        )
            .edit()
            .putString(
                KEY_PRESENTING_REPORT,
                report.name
            )
            .remove(
                KEY_CRASHED_PRESENTATION_REPORT
            )
            .apply()
    }

    fun endPresentation(
        context: Context,
        report: File
    ) {
        val preferences =
            context.getSharedPreferences(
                PRESENTATION_PREFERENCES,
                Context.MODE_PRIVATE
            )

        if (
            preferences.getString(
                KEY_PRESENTING_REPORT,
                null
            ) == report.name
        ) {
            preferences
                .edit()
                .remove(
                    KEY_PRESENTING_REPORT
                )
                .apply()
        }
    }

    fun recordCrashDuringPresentation(
        context: Context,
        crashReport: CrashReport
    ) {
        val preferences =
            context.getSharedPreferences(
                PRESENTATION_PREFERENCES,
                Context.MODE_PRIVATE
            )

        val presenting =
            preferences.getString(
                KEY_PRESENTING_REPORT,
                null
            )
                ?: return

        val crashReportFileName =
            FILE_PREFIX +
                crashReport.reportId +
                FILE_SUFFIX

        preferences
            .edit()
            .putString(
                KEY_CRASHED_PRESENTATION_REPORT,
                crashReportFileName
            )
            .remove(
                KEY_PRESENTING_REPORT
            )
            .commit()
    }

    fun markPresented(
        context: Context,
        report: File
    ) {
        context.getSharedPreferences(
            PRESENTATION_PREFERENCES,
            Context.MODE_PRIVATE
        )
            .edit()
            .putString(
                KEY_PRESENTED_REPORT,
                report.name
            )
            .remove(
                KEY_PRESENTING_REPORT
            )
            .remove(
                KEY_CRASHED_PRESENTATION_REPORT
            )
            .apply()
    }

    fun markLatestPresented(
        context: Context
    ) {
        latest(context)?.let {
            markPresented(
                context,
                it
            )
        }
    }

    private fun reportFiles(
        context: Context
    ): List<File> {
        val directory =
            File(
                context.filesDir,
                DIRECTORY_NAME
            )

        return directory
            .listFiles()
            ?.filter {
                it.isFile &&
                    it.name.startsWith(
                        FILE_PREFIX
                    ) &&
                    it.name.endsWith(
                        FILE_SUFFIX
                    )
            }
            .orEmpty()
    }

    private fun prune(
        directory: File
    ) {
        val reports =
            directory
                .listFiles()
                ?.filter {
                    it.isFile &&
                        it.name.startsWith(
                            FILE_PREFIX
                        ) &&
                        it.name.endsWith(
                            FILE_SUFFIX
                        )
                }
                ?.sortedByDescending {
                    it.lastModified()
                }
                .orEmpty()

        reports
            .drop(MAX_REPORTS)
            .forEach {
                runCatching {
                    it.delete()
                }
            }
    }
}
