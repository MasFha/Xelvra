package com.mdevz.xelvra.ui.settings.assets

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.mdevz.xelvra.R
import com.mdevz.xelvra.core.vpn.VpnRuntimeManager
import com.mdevz.xelvra.core.xray.XrayAssetManager
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class AssetFilesActivity :
    AppCompatActivity() {

    private val executor =
        Executors.newSingleThreadExecutor()

    @Volatile
    private var busy =
        false

    @Volatile
    private var hasUpdates =
        false

    private var initialCheckStarted =
        false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(
            R.layout.activity_asset_files
        )

        applyInsets()

        val toolbar =
            findViewById<MaterialToolbar>(
                R.id.asset_toolbar
            )

        toolbar.setNavigationOnClickListener {
            finish()
        }

        findViewById<View>(
            R.id.check_updates_button
        ).setOnClickListener {
            checkForUpdates(
                userInitiated = true
            )
        }

        findViewById<View>(
            R.id.restore_assets_button
        ).setOnClickListener {
            if (ensureAssetsCanChange()) {
                confirmRestore()
            }
        }

        findViewById<View>(
            R.id.update_assets_button
        ).setOnClickListener {
            if (ensureAssetsCanChange()) {
                startUpdate()
            }
        }

        refreshAssets()
    }

    override fun onResume() {
        super.onResume()

        refreshAssets()

        if (!initialCheckStarted) {
            initialCheckStarted = true

            checkForUpdates(
                userInitiated = false
            )
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun applyInsets() {
        val root =
            findViewById<View>(
                R.id.asset_root
            )

        val toolbar =
            findViewById<View>(
                R.id.asset_toolbar
            )

        val scroll =
            findViewById<View>(
                R.id.asset_scroll
            )

        ViewCompat.setOnApplyWindowInsetsListener(
            root
        ) { _, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                )

            toolbar.setPadding(
                toolbar.paddingLeft,
                bars.top,
                toolbar.paddingRight,
                toolbar.paddingBottom
            )

            val params =
                toolbar.layoutParams

            params.height =
                android.util.TypedValue().let { value ->
                check(
                    theme.resolveAttribute(
                        android.R.attr.actionBarSize,
                        value,
                        true
                    )
                ) {
                    "actionBarSize tidak ditemukan"
                }
                android.util.TypedValue.complexToDimensionPixelSize(
                    value.data,
                    resources.displayMetrics
                )
            } + bars.top

            toolbar.layoutParams =
                params

            scroll.setPadding(
                scroll.paddingLeft,
                scroll.paddingTop,
                scroll.paddingRight,
                bars.bottom
            )

            insets
        }
    }

    private fun refreshAssets() {
        executor.execute {
            runCatching {

                if (canChangeAssets()) {
                    XrayAssetManager.ensureBundledAssets(
                        applicationContext
                    )
                }

                XrayAssetManager.ensureMetadata(
                    applicationContext
                )

                Triple(
                    XrayAssetManager.getAssetInfo(
                        applicationContext,
                        XrayAssetManager.GEOSITE
                    ),
                    XrayAssetManager.getAssetInfo(
                        applicationContext,
                        XrayAssetManager.GEOIP
                    ),
                    XrayAssetManager.getLastCheckedAt(
                        applicationContext
                    )
                )
            }.onSuccess { result ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }

                    renderAsset(
                        result.first,
                        R.id.geosite_status,
                        R.id.geosite_details
                    )

                    renderAsset(
                        result.second,
                        R.id.geoip_status,
                        R.id.geoip_details
                    )

                    renderLastChecked(
                        result.third
                    )

                    hasUpdates =
                        result.first.updateAvailable ||
                            result.second.updateAvailable

                    updateControls()
                }
            }.onFailure { error ->
                showError(
                    error,
                    getString(R.string.asset_error_read)
                )
            }
        }
    }

    private fun checkForUpdates(
        userInitiated: Boolean
    ) {
        if (busy) {
            return
        }

        busy = true
        updateControls()

        findViewById<TextView>(
            R.id.last_checked_text
        ).text =
            getString(R.string.asset_checking_updates)

        executor.execute {
            runCatching {
                XrayAssetManager.checkForUpdates(
                    applicationContext
                )
            }.onSuccess { result ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }

                    busy = false

                    renderAsset(
                        result.geosite,
                        R.id.geosite_status,
                        R.id.geosite_details
                    )

                    renderAsset(
                        result.geoip,
                        R.id.geoip_status,
                        R.id.geoip_details
                    )

                    renderLastChecked(
                        result.checkedAt
                    )

                    hasUpdates =
                        result.hasUpdates

                    updateControls()

                    if (userInitiated) {
                        Snackbar.make(
                            findViewById(
                                R.id.asset_root
                            ),
                            if (result.hasUpdates) {
                                getString(R.string.asset_update_available)
                            } else {
                                getString(R.string.asset_up_to_date_plural)
                            },
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }
            }.onFailure { error ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }

                    busy = false

                    renderLastChecked(
                        XrayAssetManager
                            .getLastCheckedAt(
                                applicationContext
                            )
                    )

                    updateControls()

                    Snackbar.make(
                        findViewById(
                            R.id.asset_root
                        ),
                        getString(
                            R.string.asset_error_check_updates
                        ),
                        Snackbar.LENGTH_LONG
                    )
                        .setAction(
                            getString(R.string.action_retry)
                        ) {
                            checkForUpdates(
                                userInitiated = true
                            )
                        }
                        .show()
                }
            }
        }
    }

    private fun renderAsset(
        info: XrayAssetManager.AssetInfo,
        statusId: Int,
        detailsId: Int
    ) {
        val localStatus =
            when (info.source) {
                XrayAssetManager.Source.BUNDLED ->
                    getString(R.string.asset_bundled)

                XrayAssetManager.Source.UPDATED ->
                    getString(R.string.asset_status_updated)

                XrayAssetManager.Source.MISSING ->
                    getString(R.string.asset_status_missing)
            }

        val remoteStatus =
            when {
                info.sha256 == null ->
                    null

                info.remoteSha256 == null ->
                    null

                info.updateAvailable ->
                    getString(
                        R.string.asset_status_update_available
                    )

                else ->
                    getString(
                        R.string.asset_status_up_to_date
                    )
            }

        findViewById<TextView>(
            statusId
        ).text =
            if (remoteStatus == null) {
                localStatus
            } else {
                "$localStatus • $remoteStatus"
            }

        val details =
            if (info.sha256 == null) {
                getString(R.string.asset_not_available)
            } else {
                buildString {
                    append(
                        formatBytes(
                            info.size
                        )
                    )

                    if (info.installedAt > 0L) {
                        append(
                            " • "
                        )

                        append(
                            getString(
                                R.string.asset_details_updated,
                                formatDate(
                                    info.installedAt
                                )
                            )
                        )
                    }

                    append(
                        "\n"
                    )

                    append(
                        getString(
                            R.string.asset_sha256_format,
                            info.sha256.take(12)
                        )
                    )
                }
            }

        findViewById<TextView>(
            detailsId
        ).text =
            details
    }

    private fun renderLastChecked(
        timestamp: Long
    ) {
        findViewById<TextView>(
            R.id.last_checked_text
        ).text =
            if (timestamp <= 0L) {
                getString(R.string.asset_last_checked_never)
            } else {
                getString(
                    R.string.asset_last_checked_format,
                    DateFormat
                        .getDateTimeInstance(
                            DateFormat.MEDIUM,
                            DateFormat.SHORT
                        )
                        .format(
                            Date(timestamp)
                        )
                )
            }
    }

    private fun startUpdate() {
        if (busy) {
            return
        }

        if (!ensureAssetsCanChange()) {
            return
        }

        busy = true
        updateControls()

        findViewById<View>(
            R.id.update_progress_container
        ).visibility =
            View.VISIBLE

        findViewById<LinearProgressIndicator>(
            R.id.update_progress
        ).apply {
            isIndeterminate = true
            progress = 0
        }

        findViewById<TextView>(
            R.id.update_progress_text
        ).text =
            getString(R.string.asset_preparing_update_long)

        executor.execute {
            runCatching {

                check(
                    canChangeAssets()
                ) {
                    getString(R.string.asset_vpn_update_blocked)
                }

                XrayAssetManager.updateAssets(
                    applicationContext
                ) { progress ->
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            renderProgress(
                                progress
                            )
                        }
                    }
                }
            }.onSuccess { result ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }

                    busy = false

                    findViewById<View>(
                        R.id.update_progress_container
                    ).visibility =
                        View.GONE

                    hasUpdates = false

                    updateControls()
                    refreshAssets()

                    val message =
                        if (
                            result.changedAssets.isEmpty()
                        ) {
                            getString(R.string.asset_already_up_to_date)
                        } else {
                            getString(
                                R.string.asset_updated_list,
                                result.changedAssets
                                    .map {
                                        displayName(it)
                                    }
                                    .joinToString()
                            )
                        }

                    Snackbar.make(
                        findViewById(
                            R.id.asset_root
                        ),
                        message,
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }.onFailure { error ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }

                    busy = false

                    findViewById<View>(
                        R.id.update_progress_container
                    ).visibility =
                        View.GONE

                    updateControls()

                    Snackbar.make(
                        findViewById(
                            R.id.asset_root
                        ),
                        getString(
                            R.string.asset_update_failed
                        ),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun renderProgress(
        state: XrayAssetManager.UpdateProgress
    ) {
        findViewById<TextView>(
            R.id.update_progress_text
        ).text =
            getString(
                R.string.asset_progress_format,
                displayName(state.assetName),
                if (state.stage == "Downloading") {
                    getString(
                        R.string.asset_stage_downloading
                    )
                } else {
                    state.stage
                }
            )

        val indicator =
            findViewById<LinearProgressIndicator>(
                R.id.update_progress
            )

        if (
            state.stage == "Downloading" &&
            state.totalBytes > 0L
        ) {
            indicator.isIndeterminate =
                false

            val percent =
                (
                    state.downloadedBytes *
                        100L /
                        state.totalBytes
                    )
                    .coerceIn(
                        0L,
                        100L
                    )
                    .toInt()

            indicator.setProgressCompat(
                percent,
                true
            )
        } else {
            indicator.isIndeterminate =
                true
        }
    }

    private fun confirmRestore() {
        MaterialAlertDialogBuilder(this)
            .setTitle(
                getString(R.string.asset_restore_title)
            )
            .setMessage(
                getString(R.string.asset_restore_message)
            )
            .setNegativeButton(
                android.R.string.cancel,
                null
            )
            .setPositiveButton(
                getString(R.string.asset_restore_action)
            ) { _, _ ->
                restoreBundled()
            }
            .show()
    }

    private fun restoreBundled() {
        if (busy) {
            return
        }

        if (!ensureAssetsCanChange()) {
            return
        }

        busy = true
        updateControls()

        executor.execute {
            runCatching {
                check(
                    canChangeAssets()
                ) {
                    getString(R.string.asset_vpn_restore_blocked)
                }

                XrayAssetManager.restoreBundledAssets(
                    applicationContext
                )
            }.onSuccess {
                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }

                    busy = false
                    updateControls()
                    refreshAssets()

                    Snackbar.make(
                        findViewById(
                            R.id.asset_root
                        ),
                        getString(R.string.asset_restore_success),
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }.onFailure { error ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }

                    busy = false
                    updateControls()

                    Snackbar.make(
                        findViewById(
                            R.id.asset_root
                        ),
                        getString(
                            R.string.asset_restore_failed
                        ),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun ensureAssetsCanChange(): Boolean {
        if (canChangeAssets()) {
            return true
        }

        Snackbar.make(
            findViewById(
                R.id.asset_root
            ),
            getString(R.string.asset_vpn_change_blocked),
            Snackbar.LENGTH_LONG
        ).show()

        return false
    }

    private fun canChangeAssets(): Boolean {
        return !VpnRuntimeManager.isSessionActive()
    }

    private fun updateControls() {
        val canChange =
            canChangeAssets()

        findViewById<View>(
            R.id.check_updates_button
        ).isEnabled =
            !busy

        findViewById<View>(
            R.id.update_assets_button
        ).isEnabled =
            !busy &&
                canChange &&
                hasUpdates

        findViewById<View>(
            R.id.restore_assets_button
        ).isEnabled =
            !busy &&
                canChange
    }

    private fun showError(
        error: Throwable,
        fallback: String
    ) {
        runOnUiThread {
            if (isFinishing || isDestroyed) {
                return@runOnUiThread
            }

            Snackbar.make(
                findViewById(
                    R.id.asset_root
                ),
                fallback,
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun displayName(
        name: String
    ): String {
        return when (name) {
            XrayAssetManager.GEOSITE ->
                "GeoSite"

            XrayAssetManager.GEOIP ->
                "GeoIP"

            else ->
                name
        }
    }

    private fun formatDate(
        timestamp: Long
    ): String {
        return DateFormat
            .getDateInstance(
                DateFormat.MEDIUM
            )
            .format(
                Date(timestamp)
            )
    }

    private fun formatBytes(
        bytes: Long
    ): String {
        if (bytes < 1024L) {
            return "$bytes B"
        }

        val units =
            arrayOf(
                "KB",
                "MB",
                "GB"
            )

        var value =
            bytes.toDouble()

        var unit = -1

        while (
            value >= 1024.0 &&
            unit < units.lastIndex
        ) {
            value /= 1024.0
            unit++
        }

        return String.format(
            Locale.US,
            "%.1f %s",
            value,
            units[unit]
        )
    }
}
