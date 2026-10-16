package com.mdevz.xelvra.ui.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.InsetDrawable
import android.os.Bundle
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.ActionMenuView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.chip.ChipGroup
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mdevz.xelvra.R
import com.mdevz.xelvra.core.log.AppLog

class LogsFragment : Fragment(
    R.layout.fragment_logs
) {

    private lateinit var logText: TextView

    private var toolbarStyleView: MaterialToolbar? = null
    private var toolbarStyleRunnable: Runnable? = null

    private var categoryFilter:
        AppLog.Category? = null

    private var levelFilter:
        AppLog.Level? = null

    private val logsRenderHandler =
        android.os.Handler(android.os.Looper.getMainLooper())

    private var logsRenderScheduled = false

    private val pendingLogsRender = Runnable {
        logsRenderScheduled = false
        if (isAdded && view != null) {
            renderLogs()
        }
    }

    private val listener: () -> Unit = {
        if (!logsRenderScheduled) {
            logsRenderScheduled = true
            logsRenderHandler.postDelayed(pendingLogsRender, 100L)
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        bindToolbarActionCircles(view)

        super.onViewCreated(
            view,
            savedInstanceState
        )

        logText =
            view.findViewById(
                R.id.logs_text
            )

        val categoryFilters =
            view.findViewById<ChipGroup>(
                R.id.logs_filters
            )

        categoryFilters.setOnCheckedStateChangeListener {
                _,
                checkedIds ->

            categoryFilter = when (
                checkedIds.firstOrNull()
            ) {
                R.id.filter_app ->
                    AppLog.Category.APP

                R.id.filter_vpn ->
                    AppLog.Category.VPN

                R.id.filter_xray ->
                    AppLog.Category.XRAY

                R.id.filter_traffic ->
                    AppLog.Category.TRAFFIC

                else -> null
            }

            renderLogs()
        }

        val levelFilters =
            view.findViewById<ChipGroup>(
                R.id.logs_level_filters
            )

        levelFilters.setOnCheckedStateChangeListener {
                _,
                checkedIds ->

            levelFilter = when (
                checkedIds.firstOrNull()
            ) {
                R.id.filter_warning ->
                    AppLog.Level.WARNING

                R.id.filter_error ->
                    AppLog.Level.ERROR

                else -> null
            }

            renderLogs()
        }

        val toolbar =
            view.findViewById<MaterialToolbar>(
                R.id.logs_toolbar
            )

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_copy_logs -> {
                    copyLogs()
                    true
                }

                R.id.action_clear_logs -> {
                    showClearLogsDialog()
                    true
                }

                else -> false
            }
        }

        val styleRunnable = Runnable {
            if (toolbarStyleView === toolbar && toolbar.isAttachedToWindow) {
                styleToolbarActions(toolbar)
            }
        }
        toolbarStyleView = toolbar
        toolbarStyleRunnable = styleRunnable
        toolbar.post(styleRunnable)

        renderLogs()
    }

    override fun onDestroyView() {
        logsRenderHandler.removeCallbacks(pendingLogsRender)
        logsRenderScheduled = false
        toolbarStyleRunnable?.let { runnable ->
            toolbarStyleView?.removeCallbacks(runnable)
        }
        toolbarStyleRunnable = null
        toolbarStyleView = null
        super.onDestroyView()
    }

    override fun onStart() {
        super.onStart()

        AppLog.addListener(
            listener
        )

        renderLogs()
    }

    override fun onStop() {

        AppLog.removeListener(
            listener
        )
        logsRenderHandler.removeCallbacks(pendingLogsRender)
        logsRenderScheduled = false

        super.onStop()
    }

    private fun renderLogs() {
        if (!isAdded || !::logText.isInitialized) {
            return
        }

        val entries =
            AppLog.snapshot()
                .filter { entry ->
                    (
                        categoryFilter == null ||
                            entry.category ==
                                categoryFilter
                    ) &&
                        (
                            levelFilter == null ||
                                entry.level ==
                                    levelFilter
                        )
                }

        logText.text =
            if (entries.isEmpty()) {
                getString(
                    R.string.logs_empty
                )
            } else {
                val output =
                    SpannableStringBuilder()

                entries.forEachIndexed {
                        index,
                        entry ->

                    if (index > 0) {
                        output.append('\n')
                    }

                    output.append(
                        styleLogEntry(
                            entry = entry
                        )
                    )
                }

                output
            }
    }

    private fun styleLogEntry(
        entry: AppLog.Entry
    ): CharSequence {
        val formatted =
            entry.formatDisplay()

        val styled =
            SpannableString(formatted)

        val severityColor =
            when (entry.level) {
                AppLog.Level.INFO ->
                    null

                AppLog.Level.WARNING ->
                    Color.rgb(
                        239,
                        108,
                        0
                    )

                AppLog.Level.ERROR ->
                    Color.rgb(
                        198,
                        40,
                        40
                    )
            }

        if (severityColor != null) {
            val separator =
                formatted.indexOf("  ")

            val messageStart =
                if (separator >= 0) {
                    separator + 2
                } else {
                    0
                }

            styled.setSpan(
                ForegroundColorSpan(
                    severityColor
                ),
                messageStart,
                formatted.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val match =
            PING_PATTERN.find(
                formatted
            ) ?: return styled

        val latency =
            match.groupValues
                .getOrNull(1)
                ?.toLongOrNull()
                ?: return styled

        val number =
            match.groupValues[1]

        val relativeStart =
            match.value.indexOf(
                number
            )

        if (relativeStart < 0) {
            return styled
        }

        val start =
            match.range.first +
                relativeStart

        val end =
            start +
                number.length

        val pingColor =
            when {
                latency < 100L ->
                    Color.rgb(
                        46,
                        125,
                        50
                    )

                latency < 200L ->
                    Color.rgb(
                        249,
                        168,
                        37
                    )

                latency < 400L ->
                    Color.rgb(
                        239,
                        108,
                        0
                    )

                else ->
                    Color.rgb(
                        198,
                        40,
                        40
                    )
            }

        styled.setSpan(
            ForegroundColorSpan(
                pingColor
            ),
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        return styled
    }

    private fun styleToolbarActions(
        toolbar: MaterialToolbar
    ) {
        val actionMenu =
            (0 until toolbar.childCount)
                .map {
                    toolbar.getChildAt(it)
                }
                .filterIsInstance<ActionMenuView>()
                .firstOrNull()
                ?: return

        val density =
            toolbar.resources.displayMetrics.density

        val itemSize =
            (44 * density).toInt()

        val circleInset =
            (4 * density).toInt()

        for (
            index in 0 until actionMenu.childCount
        ) {
            val action =
                actionMenu.getChildAt(index)

            val circle =
                androidx.appcompat.content.res.AppCompatResources
                    .getDrawable(
                        toolbar.context,
                        R.drawable.bg_toolbar_action_circle
                    )
                    ?: continue

            action.background =
                InsetDrawable(
                    circle,
                    circleInset
                )

            action.setPadding(
                (10 * density).toInt(),
                (10 * density).toInt(),
                (10 * density).toInt(),
                (10 * density).toInt()
            )

            action.layoutParams =
                (action.layoutParams
                    as ActionMenuView.LayoutParams).apply {
                    width = itemSize
                    height = itemSize
                    leftMargin = 0
                    rightMargin = 0
                }
        }
    }

    private fun showClearLogsDialog() {
        MaterialAlertDialogBuilder(
            requireContext()
        )
            .setTitle(
                R.string.logs_clear_title
            )
            .setMessage(
                R.string.logs_clear_message
            )
            .setNegativeButton(
                android.R.string.cancel,
                null
            )
            .setPositiveButton(
                R.string.logs_clear
            ) {
                    _,
                    _ ->

                AppLog.clear()
            }
            .show()
    }

    private fun copyLogs() {
        val text =
            AppLog.snapshot()
                .joinToString(
                    separator = "\n"
                ) { entry ->
                    entry.format()
                }

        if (text.isBlank()) {
            Toast.makeText(
                requireContext(),
                R.string.logs_empty,
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val clipboard =
            requireContext()
                .getSystemService(
                    Context.CLIPBOARD_SERVICE
                ) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                getString(
                    R.string.logs_clipboard_label
                ),
                text
            )
        )

        Toast.makeText(
            requireContext(),
            R.string.logs_copied,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun bindToolbarActionCircles(
        root: View
    ) {
        val toolbar =
            root.findViewById<MaterialToolbar>(
                R.id.logs_toolbar
            )

        toolbar.post {
            toolbar.menu
                .findItem(
                    R.id.action_copy_logs
                )
                ?.actionView
                ?.findViewById<View>(
                    R.id.toolbar_action_copy
                )
                ?.setOnClickListener {
                    toolbar.menu
                        .performIdentifierAction(
                            R.id.action_copy_logs,
                            0
                        )
                }

            toolbar.menu
                .findItem(
                    R.id.action_clear_logs
                )
                ?.actionView
                ?.findViewById<View>(
                    R.id.toolbar_action_clear
                )
                ?.setOnClickListener {
                    toolbar.menu
                        .performIdentifierAction(
                            R.id.action_clear_logs,
                            0
                        )
                }
        }
    }

    companion object {
        private val PING_PATTERN =
            Regex(
                """Ping\s*:\s*(\d+)\s+ms"""
            )
    }

}
