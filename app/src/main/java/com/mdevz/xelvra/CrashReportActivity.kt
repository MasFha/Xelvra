package com.mdevz.xelvra

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mdevz.xelvra.core.crash.CrashReportParser
import com.mdevz.xelvra.core.crash.CrashReportStore
import com.mdevz.xelvra.core.crash.CrashReportSummary

class CrashReportActivity : AppCompatActivity() {

    private var currentSummary: CrashReportSummary? = null

    private val createDocumentLauncher =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument(
                "text/plain"
            )
        ) { uri ->
            if (uri == null) {
                return@registerForActivityResult
            }

            val summary =
                currentSummary
                    ?: return@registerForActivityResult

            runCatching {
                contentResolver
                    .openOutputStream(uri)
                    ?.use { output ->
                        summary.reportFile
                            .inputStream()
                            .use { input ->
                                input.copyTo(output)
                            }
                    }
                    ?: error(
                        "Unable to open output stream"
                    )
            }.onSuccess {
                showSaveSuccess(
                    summary.reportFile.name,
                    getString(
                        R.string.crash_save_location_selected
                    )
                )
            }.onFailure {
                showSaveFailure()
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(
            R.layout.activity_crash_report
        )

        val root =
            findViewById<android.view.View>(
                R.id.crash_report_root
            )

        ViewCompat.setOnApplyWindowInsetsListener(
            root
        ) { view, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                )

            view.setPadding(
                bars.left,
                bars.top,
                bars.right,
                bars.bottom
            )

            insets
        }

        val file =
            CrashReportStore.latest(this)

        val summary =
            file?.let {
                CrashReportParser.parse(it)
            }

        if (summary == null) {
            openMain()
            return
        }

        currentSummary = summary

        CrashReportStore.beginPresentation(
            this,
            summary.reportFile
        )

        bindSummary(summary)
        bindActions(summary)
    }

     private fun bindSummary(
        summary: CrashReportSummary
    ) {
        findViewById<TextView>(
            R.id.crash_exception
        ).text =
            summary.exceptionType
                .substringAfterLast('.')

        findViewById<TextView>(
            R.id.crash_class_value
        ).text =
            summary.className
                ?.substringAfterLast('.')
                ?: getString(
                    R.string.crash_not_available
                )

        findViewById<TextView>(
            R.id.crash_method_value
        ).text =
            summary.methodName
                ?: getString(
                    R.string.crash_not_available
                )

        findViewById<TextView>(
            R.id.crash_line_value
        ).text =
            summary.lineNumber
                ?.toString()
                ?: getString(
                    R.string.crash_not_available
                )

        findViewById<TextView>(
            R.id.crash_report_id_value
        ).text =
            summary.reportId
    }

    private fun bindActions(
        summary: CrashReportSummary
    ) {
        findViewById<MaterialButton>(
            R.id.crash_copy_button
        ).setOnClickListener {
            copySummary(summary)
        }

        findViewById<MaterialButton>(
            R.id.crash_share_button
        ).setOnClickListener {
            shareReport(summary)
        }

        findViewById<MaterialButton>(
            R.id.crash_save_button
        ).setOnClickListener {
            saveReport(summary)
        }

        findViewById<MaterialButton>(
            R.id.crash_restart_button
        ).setOnClickListener {
            CrashReportStore.markPresented(
                this,
                summary.reportFile
            )
            openMain()
        }

        findViewById<MaterialButton>(
            R.id.crash_close_button
        ).setOnClickListener {
            CrashReportStore.markPresented(
                this,
                summary.reportFile
            )
            finishAndRemoveTask()
        }
    }

    private fun copySummary(
        summary: CrashReportSummary
    ) {
        val notAvailable =
            getString(
                R.string.crash_not_available
            )

        val safeSummary =
            buildString {
                appendLine(
                    getString(
                        R.string.crash_copy_header
                    )
                )
                appendLine()
                append(
                    getString(
                        R.string.crash_copy_exception,
                        summary.exceptionType
                            .substringAfterLast('.')
                    )
                )
                appendLine()
                append(
                    getString(
                        R.string.crash_copy_class,
                        summary.className
                            ?.substringAfterLast('.')
                            ?: notAvailable
                    )
                )
                appendLine()
                append(
                    getString(
                        R.string.crash_copy_method,
                        summary.methodName
                            ?: notAvailable
                    )
                )
                appendLine()
                append(
                    getString(
                        R.string.crash_copy_line,
                        summary.lineNumber
                            ?.toString()
                            ?: notAvailable
                    )
                )
                appendLine()
                append(
                    getString(
                        R.string.crash_copy_report_id,
                        summary.reportId
                    )
                )
            }

        val clipboard =
            getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                getString(
                    R.string.crash_copy_clip_label
                ),
                safeSummary
            )
        )

        Toast.makeText(
            this,
            R.string.crash_copy_success,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun shareReport(
        summary: CrashReportSummary
    ) {
        runCatching {
            val uri =
                FileProvider.getUriForFile(
                    this,
                    "$packageName.crashreports",
                    summary.reportFile
                )

            val intent =
                Intent(
                    Intent.ACTION_SEND
                ).apply {
                    type = "text/plain"

                    putExtra(
                        Intent.EXTRA_STREAM,
                        uri
                    )

                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        getString(
                            R.string.crash_share_subject,
                            summary.reportId
                        )
                    )

                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }

            startActivity(
                Intent.createChooser(
                    intent,
                    getString(
                        R.string.crash_share_chooser
                    )
                )
            )
        }.onFailure {
            Toast.makeText(
                this,
                R.string.crash_share_failed,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun saveReport(
        summary: CrashReportSummary
    ) {
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {
            saveWithMediaStore(summary)
        } else {
            createDocumentLauncher.launch(
                summary.reportFile.name
            )
        }
    }

    private fun saveWithMediaStore(
        summary: CrashReportSummary
    ) {
        val relativePath =
            Environment.DIRECTORY_DOCUMENTS +
                "/Xelvra Report"

        runCatching {
            val values =
                ContentValues().apply {
                    put(
                        MediaStore.MediaColumns.DISPLAY_NAME,
                        summary.reportFile.name
                    )
                    put(
                        MediaStore.MediaColumns.MIME_TYPE,
                        "text/plain"
                    )
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        relativePath
                    )
                    put(
                        MediaStore.MediaColumns.IS_PENDING,
                        1
                    )
                }

            val collection =
                MediaStore.Files.getContentUri(
                    MediaStore.VOLUME_EXTERNAL_PRIMARY
                )

            val uri =
                contentResolver.insert(
                    collection,
                    values
                )
                    ?: error(
                        "Unable to create MediaStore entry"
                    )

            try {
                contentResolver
                    .openOutputStream(uri, "w")
                    ?.use { output ->
                        summary.reportFile
                            .inputStream()
                            .use { input ->
                                input.copyTo(output)
                            }
                    }
                    ?: error(
                        "Unable to open MediaStore stream"
                    )

                val completed =
                    ContentValues().apply {
                        put(
                            MediaStore.MediaColumns.IS_PENDING,
                            0
                        )
                    }

                contentResolver.update(
                    uri,
                    completed,
                    null,
                    null
                )
            } catch (error: Throwable) {
                runCatching {
                    contentResolver.delete(
                        uri,
                        null,
                        null
                    )
                }

                throw error
            }
        }.onSuccess {
            showSaveSuccess(
                summary.reportFile.name,
                "Documents/Xelvra Report"
            )
        }.onFailure {
            showSaveFailure()
        }
    }

    private fun showSaveSuccess(
        fileName: String,
        location: String
    ) {
        MaterialAlertDialogBuilder(this)
            .setTitle(
                R.string.crash_save_success_title
            )
            .setMessage(
                getString(
                    R.string.crash_save_success_message,
                    fileName,
                    location
                )
            )
            .setPositiveButton(
                android.R.string.ok,
                null
            )
            .show()
    }

    private fun showSaveFailure() {
        MaterialAlertDialogBuilder(this)
            .setTitle(
                R.string.crash_save_failed_title
            )
            .setMessage(
                R.string.crash_save_failed_message
            )
            .setPositiveButton(
                android.R.string.ok,
                null
            )
            .show()
    }

    override fun onDestroy() {
        if (
            !isChangingConfigurations
        ) {
            currentSummary?.let {
                CrashReportStore.endPresentation(
                    this,
                    it.reportFile
                )
            }
        }

        super.onDestroy()
    }

     private fun openMain() {
        startActivity(
            Intent(
                this,
                MainActivity::class.java
            ).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            )
        )

        finish()
    }
}
