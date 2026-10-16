package com.mdevz.xelvra.ui.settings

import java.util.concurrent.Executors
import java.util.concurrent.TimeoutException
import com.mdevz.xelvra.core.xray.XrayRuntimeController
import com.mdevz.xelvra.core.log.AppLog
import com.google.android.material.button.MaterialButton
import com.mdevz.xelvra.core.xray.XrayCoreManager
import android.os.Bundle
import android.content.Intent
import com.mdevz.xelvra.ui.settings.routing.SmartRoutingActivity
import android.view.View
import android.view.LayoutInflater
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.materialswitch.MaterialSwitch
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.mdevz.xelvra.R
import com.mdevz.xelvra.core.vpn.VpnRuntimeManager
import com.mdevz.xelvra.core.theme.ThemeManager
import com.mdevz.xelvra.data.local.AppTheme
import com.mdevz.xelvra.data.local.PerAppVpnMode
import com.mdevz.xelvra.data.local.BypassLanMode
import com.mdevz.xelvra.data.local.SettingsStore
import com.mdevz.xelvra.data.local.DomainStrategy
import com.mdevz.xelvra.data.local.RoutingMode
import com.mdevz.xelvra.ui.settings.perapp.PerAppVpnActivity
import com.mdevz.xelvra.core.xray.XrayAssetManager
import com.mdevz.xelvra.ui.settings.assets.AssetFilesActivity

class SettingsFragment : Fragment(
    R.layout.fragment_settings
) {

    companion object {
        private const val LEGAL_PRIVACY_URL =
            "https://masfha.github.io/xelvra-legal/privacy.html"

        private const val LEGAL_TERMS_URL =
            "https://masfha.github.io/xelvra-legal/terms.html"

        private const val LEGAL_LICENSES_URL =
            "https://masfha.github.io/xelvra-legal/licenses.html"
    }
    private lateinit var settingsStore:
        SettingsStore

    private var renderingXrayInlineSettings =
        false

    private val realDelayExecutor =
        Executors.newSingleThreadExecutor()


    override fun onDestroy() {
        realDelayExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {

        super.onViewCreated(
            view,
            savedInstanceState
        )

        settingsStore =
            SettingsStore(
                requireContext()
            )

        bindToolbar(view)

        bindPerAppVpn(view)
        bindRemoteDns(view)
        bindDirectDns(view)
        bindFakeDns(view)
        bindMux(view)
        bindFragment(view)
        bindAutoConnect(view)
        bindAutoReconnect(view)
        bindSniffing(view)
        bindDomainStrategy(view)
        bindRoutingMode(view)
        bindAssetFiles(view)
        bindCoreUpdates(view)
        bindSmartRouting(view)
        bindTestUrl(view)
        bindTimeout(view)
        bindTheme(view)
        bindLanguage(view)
        bindVpnInterfaceAddress(view)
        bindMtu(view)
        bindIpv6(view)
        bindBypassLan(view)

        renderPerAppVpn(view)
        renderRemoteDns(view)
        renderDirectDns(view)
        renderFakeDns(view)
        renderSniffing(view)
        renderFragment(view)
        renderMux(view)
        renderDomainStrategy(view)
        renderRoutingMode(view)
        renderAssetFiles(view)
        renderTestUrl(view)
        renderTimeout(view)
        renderAutoConnect(view)
        renderAutoReconnect(view)
        renderTheme(view)
        renderLanguage(view)
        renderVpnInterfaceAddress(view)
        renderMtu(view)
        renderIpv6(view)
        renderBypassLan(view)
    }


    private fun bindSmartRouting(root: View) {
        root.findViewById<View>(
            R.id.setting_smart_routing
        ).setOnClickListener {
            startActivity(
                Intent(
                    requireContext(),
                    SmartRoutingActivity::class.java
                )
            )
        }
    }

    private fun bindCoreUpdates(root: View) {
        root.findViewById<View>(
            R.id.setting_core_updates
        ).setOnClickListener {
            showCoreUpdatesDialog()
        }
    }

    private fun showCoreUpdatesDialog() {
        val context = requireContext()

        val installed = when (
            val result = XrayCoreManager.getVersion()
        ) {
            is XrayCoreManager.VersionResult.Success ->
                result.version

            is XrayCoreManager.VersionResult.Failure ->
                getString(R.string.about_xray_unavailable)
        }

        val details = TextView(context).apply {
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            setTextIsSelectable(true)
            text = getString(
                R.string.core_updates_installed,
                installed
            ) + "\n\n" + getString(R.string.core_updates_checking)
        }

        val scroll = android.widget.ScrollView(context).apply {
            addView(details)
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.core_updates_title)
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(
                R.string.core_updates_open_github,
                null
            )
            .create()

        var githubUrl: String? = null

        dialog.setOnShowListener {
            dialog.getButton(
                android.content.DialogInterface.BUTTON_NEUTRAL
            ).apply {
                isEnabled = false
                setOnClickListener {
                    githubUrl?.let { url ->
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                android.net.Uri.parse(url)
                            )
                        )
                    }
                }
            }
        }

        dialog.show()

        val executor = java.util.concurrent.Executors
            .newSingleThreadExecutor()

        executor.execute {
            val result = runCatching {
                fetchCoreReleaseChannels()
            }

            activity?.runOnUiThread {
                if (!isAdded || !dialog.isShowing) {
                    return@runOnUiThread
                }

                result.onSuccess { releases ->
                    val stable = releases.firstOrNull {
                        !it.prerelease
                    }

                    val preview = releases.firstOrNull {
                        it.prerelease
                    }

                    val installedVersion = Regex(
                        """(?i)xray-core\s*v?(\d+\.\d+\.\d+)"""
                    ).find(installed)?.groupValues?.getOrNull(1)

                    fun comparison(tag: String): String {
                        val remoteVersion = Regex(
                            """v?(\d+\.\d+\.\d+)"""
                        ).find(tag)?.groupValues?.getOrNull(1)

                        if (
                            installedVersion == null ||
                            remoteVersion == null
                        ) {
                            return getString(
                                R.string.core_updates_compare_unavailable
                            )
                        }

                        val local = installedVersion
                            .split(".")
                            .map { it.toInt() }

                        val remote = remoteVersion
                            .split(".")
                            .map { it.toInt() }

                        val difference = remote.zip(local)
                            .firstOrNull { it.first != it.second }

                        return when {
                            difference == null ->
                                getString(
                                    R.string.core_updates_up_to_date
                                )
                            difference.first > difference.second ->
                                getString(
                                    R.string.core_updates_available
                                )
                            else ->
                                getString(
                                    R.string.core_updates_newer_installed
                                )
                        }
                    }

                    details.text = buildString {
                        append(
                            getString(
                                R.string.core_updates_installed,
                                installed
                            )
                        )

                        for ((title, release) in listOf(
                            getString(
                                R.string.core_updates_stable
                            ) to stable,
                            getString(
                                R.string.core_updates_preview
                            ) to preview
                        )) {
                            append("\n\n")
                            append(title)
                            append("\n")

                            if (release == null) {
                                append(
                                    getString(
                                        R.string.core_updates_channel_empty
                                    )
                                )
                                continue
                            }

                            append(release.tag)
                            append("\n")
                            append(release.date)
                            append("\n")
                            append(comparison(release.tag))
                            append("\n\n")
                            append(
                                getString(
                                    R.string.core_updates_changelog
                                )
                            )
                            append("\n")
                            append(
                                release.notes.ifBlank {
                                    getString(
                                        R.string.core_updates_no_changelog
                                    )
                                }
                            )
                            append("\n")
                            append(release.url)
                        }
                    }

                    githubUrl =
                        stable?.url ?: preview?.url

                    dialog.getButton(
                        android.content.DialogInterface.BUTTON_NEUTRAL
                    ).isEnabled = githubUrl != null
                }

                result.onFailure { error ->
                    details.text = getString(
                        R.string.core_updates_installed,
                        installed
                    ) + "\n\n" +
                        getString(
                            R.string.core_updates_failed,
                            error.message ?: error.javaClass.simpleName
                        )
                }
            }

            executor.shutdown()
        }
    }

    private data class CoreReleaseChannel(
        val tag: String,
        val date: String,
        val notes: String,
        val url: String,
        val prerelease: Boolean
    )

    private fun fetchCoreReleaseChannels():
        List<CoreReleaseChannel> {

        val connection = java.net.URL(
            "https://api.github.com/repos/XTLS/Xray-core/releases?per_page=30"
        ).openConnection() as java.net.HttpURLConnection

        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.setRequestProperty(
            "Accept",
            "application/vnd.github+json"
        )
        connection.setRequestProperty(
            "User-Agent",
            "Xelvra-Core-Update-Checker"
        )

        try {
            if (connection.responseCode != 200) {
                error(
                    "GitHub HTTP ${connection.responseCode}"
                )
            }

            val response = connection.inputStream
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }

            val json = org.json.JSONArray(response)

            val releases = mutableListOf<CoreReleaseChannel>()

            for (index in 0 until json.length()) {
                val item = json.getJSONObject(index)

                if (item.optBoolean("draft")) continue

                val url = item.optString("html_url")

                if (!url.startsWith(
                    "https://github.com/XTLS/Xray-core/releases/"
                )) continue

                releases.add(
                    CoreReleaseChannel(
                        tag = item.optString("tag_name"),
                        date = item.optString("published_at")
                            .take(10),
                        notes = item.optString("body"),
                        url = url,
                        prerelease = item.optBoolean(
                            "prerelease"
                        )
                    )
                )
            }

            if (releases.isEmpty()) {
                error("No published releases returned")
            }

            return releases
                .sortedByDescending { it.date }
        } finally {
            connection.disconnect()
        }
    }

    override fun onResume() {
        super.onResume()

        view?.let {
            renderPerAppVpn(it)
            renderRemoteDns(it)
            renderDirectDns(it)
            renderFakeDns(it)
            renderSniffing(it)
            renderFragment(it)
            renderMux(it)
            renderDomainStrategy(it)
            renderRoutingMode(it)
            renderAssetFiles(it)
            renderTestUrl(it)
            renderTimeout(it)
            renderAutoConnect(it)
            renderAutoReconnect(it)
            renderTheme(it)
            renderLanguage(it)
            renderVpnInterfaceAddress(it)
            renderMtu(it)
            renderIpv6(it)
            renderBypassLan(it)
        }
    }

    private fun ensureSettingsCanChange(): Boolean {
        if (!VpnRuntimeManager.isSessionActive()) {
            return true
        }

        MaterialAlertDialogBuilder(
            requireContext()
        )
            .setTitle(
                R.string.vpn_running_settings_title
            )
            .setMessage(
                R.string.vpn_running_settings_message
            )
            .setPositiveButton(
                R.string.action_ok,
                null
            )
            .show()

        return false
    }

    private fun bindToolbar(
        root: View
    ) {
        root.findViewById<View>(
            R.id.toolbar_action_info
        ).setOnClickListener {
            showAboutDialog()
        }
    }

    private fun showAboutDialog() {
        val context = requireContext()

        val content =
            layoutInflater.inflate(
                R.layout.dialog_about,
                null,
                false
            )

        val packageInfo =
            context.packageManager.getPackageInfo(
                context.packageName,
                0
            )

        val versionName =
            packageInfo.versionName
                ?: getString(R.string.value_unknown)

        val versionCode =
            packageInfo.versionCode.toLong()

        content.findViewById<TextView>(
            R.id.about_app_version
        ).text =
            getString(
                R.string.about_version_format,
                versionName,
                versionCode
            )

        val xrayVersion =
            when (
                val result =
                    XrayCoreManager.getVersion()
            ) {
                is XrayCoreManager.VersionResult.Success ->
                    result.version

                is XrayCoreManager.VersionResult.Failure -> {
                    AppLog.xray(
                        message =
                            "Unable to read Xray core version (" +
                                result.error.javaClass.simpleName +
                                ")",
                        level = AppLog.Level.WARNING
                    )

                    getString(
                        R.string.about_xray_unavailable
                    )
                }
            }

        content.findViewById<TextView>(
            R.id.about_xray_version
        ).text =
            xrayVersion

        val dialog =
            MaterialAlertDialogBuilder(context)
                .setView(content)
                .create()

        content.findViewById<View>(
            R.id.about_privacy
        ).setOnClickListener {
            openLegalUrl(
                LEGAL_PRIVACY_URL
            )
        }

        content.findViewById<View>(
            R.id.about_terms
        ).setOnClickListener {
            openLegalUrl(
                LEGAL_TERMS_URL
            )
        }

        content.findViewById<View>(
            R.id.about_licenses
        ).setOnClickListener {
            openLegalUrl(
                LEGAL_LICENSES_URL
            )
        }

        content.findViewById<View>(
            R.id.about_close_button
        ).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun openLegalUrl(
        url: String
    ) {
        val intent =
            Intent(
                Intent.ACTION_VIEW,
                android.net.Uri.parse(url)
            )

        runCatching {
            startActivity(intent)
        }.onFailure {
            AppLog.app(
                message =
                    "Unable to open legal page (" +
                        it.javaClass.simpleName +
                        ")",
                level = AppLog.Level.WARNING
            )
        }
    }

    private fun bindAutoConnect(
        root: View
    ) {
        val row =
            root.findViewById<View>(
                R.id.setting_auto_connect
            )

        val toggle =
            row.findViewById<MaterialSwitch>(
                R.id.setting_switch
            )

        toggle.isClickable = false

        row.setOnClickListener {
            if (!ensureSettingsCanChange()) {
                renderAutoConnect(root)
                return@setOnClickListener
            }

            settingsStore.setAutoConnectEnabled(
                !settingsStore.isAutoConnectEnabled()
            )

            renderAutoConnect(root)
        }
    }

    private fun renderAutoConnect(
        root: View
    ) {
        val toggle =
            root.findViewById<View>(
                R.id.setting_auto_connect
            ).findViewById<MaterialSwitch>(
                R.id.setting_switch
            )

        toggle.setOnCheckedChangeListener(null)

        toggle.isChecked =
            settingsStore.isAutoConnectEnabled()

        toggle.isClickable = false
    }

    private fun bindAutoReconnect(
        root: View
    ) {
        val row = root.findViewById<View>(
            R.id.setting_auto_reconnect
        )

        val toggle = row.findViewById<MaterialSwitch>(
            R.id.setting_switch
        )

        toggle.isClickable = false

        row.setOnClickListener {
            if (!ensureSettingsCanChange()) {
                renderAutoReconnect(root)
                return@setOnClickListener
            }

            settingsStore.setAutoReconnectEnabled(
                !settingsStore.isAutoReconnectEnabled()
            )

            renderAutoReconnect(root)
        }
    }

    private fun renderAutoReconnect(
        root: View
    ) {
        val toggle = root.findViewById<View>(
            R.id.setting_auto_reconnect
        ).findViewById<MaterialSwitch>(
            R.id.setting_switch
        )

        toggle.setOnCheckedChangeListener(null)
        toggle.isChecked =
            settingsStore.isAutoReconnectEnabled()
        toggle.isClickable = false
    }

    private fun bindTimeout(
        root: View
    ) {
        root.findViewById<View>(
            R.id.setting_timeout
        ).setOnClickListener {
            showTimeoutDialog(
                root
            )
        }
    }

    private fun renderTimeout(
        root: View
    ) {
        val seconds =
            settingsStore
                .getConnectionTimeoutSeconds()

        root.findViewById<View>(
            R.id.setting_timeout
        ).findViewById<TextView>(
            R.id.setting_value
        ).text =
            getString(
                R.string.settings_timeout_seconds_value,
                seconds
            )
    }

    private fun showTimeoutDialog(
        root: View
    ) {
        if (!ensureSettingsCanChange()) {
            return
        }

        val dialogView =
            LayoutInflater.from(
                requireContext()
            ).inflate(
                R.layout.dialog_timeout,
                null
            )

        val inputLayout =
            dialogView.findViewById<TextInputLayout>(
                R.id.timeout_input_layout
            )

        val input =
            dialogView.findViewById<TextInputEditText>(
                R.id.timeout_input
            )

        input.setText(
            settingsStore
                .getConnectionTimeoutSeconds()
                .toString()
        )

        val dialog =
            MaterialAlertDialogBuilder(
                requireContext()
            )
                .setTitle(
                    R.string.settings_timeout
                )
                .setView(
                    dialogView
                )
                .setNegativeButton(
                    android.R.string.cancel,
                    null
                )
                .setPositiveButton(
                    android.R.string.ok,
                    null
                )
                .create()

        dialog.setOnShowListener {
            dialog.getButton(
                android.app.AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {
                val seconds =
                    input.text
                        ?.toString()
                        ?.trim()
                        ?.toIntOrNull()

                if (
                    seconds == null ||
                    seconds !in
                        SettingsStore
                            .MIN_CONNECTION_TIMEOUT_SECONDS..
                        SettingsStore
                            .MAX_CONNECTION_TIMEOUT_SECONDS
                ) {
                    inputLayout.error =
                        getString(
                            R.string.settings_timeout_invalid,
                            SettingsStore
                                .MIN_CONNECTION_TIMEOUT_SECONDS,
                            SettingsStore
                                .MAX_CONNECTION_TIMEOUT_SECONDS
                        )

                    return@setOnClickListener
                }

                inputLayout.error =
                    null

                if (!ensureSettingsCanChange()) {
                    renderTimeout(root)
                    dialog.dismiss()
                    return@setOnClickListener
                }

                settingsStore
                    .setConnectionTimeoutSeconds(
                        seconds
                    )

                renderTimeout(
                    root
                )

                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun bindTestUrl(
        root: View
    ) {
        val row =
            root.findViewById<View>(
                R.id.setting_test_url
            )

        val switch =
            row.findViewById<MaterialSwitch>(
                R.id.setting_switch
            )

        switch.setOnCheckedChangeListener {
                button,
                enabled ->

            if (!button.isPressed) {
                return@setOnCheckedChangeListener
            }

            if (!ensureSettingsCanChange()) {
                renderTestUrl(root)
                return@setOnCheckedChangeListener
            }

            settingsStore.setTestUrlEnabled(
                enabled
            )

            renderTestUrl(root)
        }

        row.setOnClickListener {
            showTestUrlDialog(
                root
            )
        }
    }

    private fun renderTestUrl(
        root: View
    ) {
        val value =
            root.findViewById<View>(
                R.id.setting_test_url
            ).findViewById<TextView>(
                R.id.setting_value
            )

        val enabled =
            settingsStore.isTestUrlEnabled()

        value.text =
            settingsStore.getTestUrl()

        value.alpha =
            if (enabled) {
                1.0f
            } else {
                0.5f
            }

        root.findViewById<View>(
            R.id.setting_test_url
        ).findViewById<MaterialSwitch>(
            R.id.setting_switch
        ).isChecked =
            enabled
    }

    private fun showTestUrlDialog(
        root: View
    ) {
        val dialogView =
            LayoutInflater.from(
                requireContext()
            ).inflate(
                R.layout.dialog_test_url,
                null
            )

        val inputLayout =
            dialogView.findViewById<TextInputLayout>(
                R.id.test_url_input_layout
            )

        val input =
            dialogView.findViewById<TextInputEditText>(
                R.id.test_url_input
            )

        val intervalInputLayout =
            dialogView.findViewById<TextInputLayout>(
                R.id.test_url_interval_input_layout
            )

        val intervalInput =
            dialogView.findViewById<TextInputEditText>(
                R.id.test_url_interval_input
            )

        val testButton =
            dialogView.findViewById<MaterialButton>(
                R.id.test_url_test_button
            )

        val testResult =
            dialogView.findViewById<TextView>(
                R.id.test_url_test_result
            )

        input.setText(
            settingsStore.getTestUrl()
        )

        intervalInput.setText(
            settingsStore
                .getTestUrlIntervalSeconds()
                .toString()
        )

        fun readValidInterval(): Int? {
            val seconds =
                intervalInput.text
                    ?.toString()
                    ?.trim()
                    ?.toIntOrNull()

            if (
                seconds == null ||
                seconds !in
                    SettingsStore
                        .MIN_TEST_URL_INTERVAL_SECONDS..
                    SettingsStore
                        .MAX_TEST_URL_INTERVAL_SECONDS
            ) {
                intervalInputLayout.error =
                    getString(
                        R.string
                            .settings_test_url_interval_invalid,
                        SettingsStore
                            .MIN_TEST_URL_INTERVAL_SECONDS,
                        SettingsStore
                            .MAX_TEST_URL_INTERVAL_SECONDS
                    )

                return null
            }

            intervalInputLayout.error = null
            return seconds
        }

        fun readValidUrl(): String? {
            val value =
                input.text
                    ?.toString()
                    ?.trim()
                    .orEmpty()

            val uri =
                runCatching {
                    android.net.Uri.parse(
                        value
                    )
                }.getOrNull()

            val valid =
                value.isNotBlank() &&
                    uri != null &&
                    (
                        uri.scheme.equals(
                            "http",
                            ignoreCase = true
                        ) ||
                        uri.scheme.equals(
                            "https",
                            ignoreCase = true
                        )
                    ) &&
                    !uri.host.isNullOrBlank()

            if (!valid) {
                inputLayout.error =
                    getString(
                        R.string.settings_test_url_invalid
                    )

                return null
            }

            inputLayout.error = null
            return value
        }

        val dialog =
            MaterialAlertDialogBuilder(
                requireContext()
            )
                .setTitle(
                    R.string.settings_test_url
                )
                .setView(
                    dialogView
                )
                .setNegativeButton(
                    android.R.string.cancel,
                    null
                )
                .setPositiveButton(
                    android.R.string.ok,
                    null
                )
                .create()

        testButton.setOnClickListener {
            val url =
                readValidUrl()
                    ?: return@setOnClickListener

            if (!settingsStore.isTestUrlEnabled()) {
                testResult.visibility =
                    View.VISIBLE

                testResult.text =
                    getString(
                        R.string
                            .settings_test_connection_disabled
                    )

                return@setOnClickListener
            }

            if (!XrayRuntimeController.isRunning()) {
                testResult.visibility =
                    View.VISIBLE

                testResult.text =
                    getString(
                        R.string
                            .settings_test_connection_not_running
                    )

                return@setOnClickListener
            }

            val timeoutSeconds =
                settingsStore
                    .getConnectionTimeoutSeconds()

            testButton.isEnabled = false
            input.isEnabled = false
            intervalInput.isEnabled = false

            testResult.visibility =
                View.VISIBLE

            testResult.text =
                getString(
                    R.string
                        .settings_test_connection_testing
                )

            realDelayExecutor.execute {
                val result =
                    runCatching {
                        XrayRuntimeController.measureDelay(
                            url = url,
                            timeoutSeconds =
                                timeoutSeconds
                        )
                    }

                activity?.runOnUiThread {
                    if (!isAdded) {
                        return@runOnUiThread
                    }

                    testButton.isEnabled = true
                    input.isEnabled = true
                    intervalInput.isEnabled = true

                    result.fold(
                        onSuccess = { delay ->
                            val safeDelay =
                                delay.coerceAtLeast(0L)

                            testResult.text =
                                getString(
                                    R.string
                                        .settings_test_connection_result,
                                    safeDelay
                                )

                            AppLog.xray(
                                "Ping : $safeDelay ms"
                            )
                        },
                        onFailure = { error ->
                            testResult.text =
                                getString(
                                    R.string
                                        .settings_test_connection_failed
                                )

                            if (error is TimeoutException) {
                                AppLog.xray(
                                    message = "Ping : timeout",
                                    level = AppLog.Level.WARNING
                                )
                            } else {
                                AppLog.xray(
                                    message = "Ping : failed",
                                    level = AppLog.Level.WARNING
                                )
                            }
                        }
                    )
                }
            }
        }

        dialog.setOnShowListener {
            dialog.getButton(
                android.app.AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {
                val value =
                    readValidUrl()
                        ?: return@setOnClickListener

                val intervalSeconds =
                    readValidInterval()
                        ?: return@setOnClickListener

                if (!ensureSettingsCanChange()) {
                    return@setOnClickListener
                }

                settingsStore.setTestUrl(
                    value
                )

                settingsStore
                    .setTestUrlIntervalSeconds(
                        intervalSeconds
                    )

                renderTestUrl(
                    root
                )

                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun bindAssetFiles(
        root: View
    ) {
        root.findViewById<View>(
            R.id.setting_asset_files
        ).setOnClickListener {
            startActivity(
                Intent(
                    requireContext(),
                    AssetFilesActivity::class.java
                )
            )
        }
    }

    private fun renderAssetFiles(
        root: View
    ) {
        val installed =
            XrayAssetManager.hasAsset(
                requireContext(),
                XrayAssetManager.GEOSITE
            ) &&
                XrayAssetManager.hasAsset(
                    requireContext(),
                    XrayAssetManager.GEOIP
                )

        root.findViewById<View>(
            R.id.setting_asset_files
        ).findViewById<TextView>(
            R.id.setting_value
        ).text =
            if (installed) {
                getString(R.string.asset_installed)
            } else {
                getString(R.string.asset_bundled)
            }
    }

    private fun bindRoutingMode(
        root: View
    ) {
        root.findViewById<View>(
            R.id.setting_routing_mode
        ).setOnClickListener {
            showRoutingModeDialog(
                root
            )
        }
    }

    private fun renderRoutingMode(
        root: View
    ) {
        val mode =
            settingsStore.getRoutingMode()

        root.findViewById<View>(
            R.id.setting_routing_mode
        ).findViewById<TextView>(
            R.id.setting_value
        ).text =
            mode.displayName
    }

    private fun showRoutingModeDialog(
        root: View
    ) {
        if (!ensureSettingsCanChange()) {
            return
        }

        val values =
            RoutingMode.entries

        val labels =
            values
                .map {
                    if (it.requiresGeoAssets) {
                        "${it.displayName} • ${getString(R.string.settings_requires_assets)}"
                    } else {
                        it.displayName
                    }
                }
                .toTypedArray()

        val current =
            settingsStore.getRoutingMode()

        val checked =
            values.indexOf(
                current
            )

        MaterialAlertDialogBuilder(
            requireContext()
        )
            .setTitle(
                R.string.settings_routing_mode
            )
            .setSingleChoiceItems(
                labels,
                checked
            ) { dialog, which ->
                if (!ensureSettingsCanChange()) {
                    renderRoutingMode(root)
                    dialog.dismiss()
                    return@setSingleChoiceItems
                }

                settingsStore.setRoutingMode(
                    values[which]
                )

                renderRoutingMode(
                    root
                )

                dialog.dismiss()
            }
            .setNegativeButton(
                android.R.string.cancel,
                null
            )
            .show()
    }

    private fun bindDomainStrategy(
        root: View
    ) {
        root.findViewById<View>(
            R.id.setting_domain_strategy
        ).setOnClickListener {
            showDomainStrategyDialog(
                root
            )
        }
    }

    private fun renderDomainStrategy(
        root: View
    ) {
        val strategy =
            settingsStore.getDomainStrategy()

        root.findViewById<View>(
            R.id.setting_domain_strategy
        ).findViewById<TextView>(
            R.id.setting_value
        ).text =
            strategy.xrayValue
    }

    private fun showDomainStrategyDialog(
        root: View
    ) {
        if (!ensureSettingsCanChange()) {
            return
        }

        val values =
            DomainStrategy.entries

        val labels =
            values
                .map {
                    it.xrayValue
                }
                .toTypedArray()

        val current =
            settingsStore.getDomainStrategy()

        val checked =
            values.indexOf(
                current
            )

        MaterialAlertDialogBuilder(
            requireContext()
        )
            .setTitle(
                R.string.settings_domain_strategy
            )
            .setSingleChoiceItems(
                labels,
                checked
            ) { dialog, which ->
                if (!ensureSettingsCanChange()) {
                    renderDomainStrategy(root)
                    dialog.dismiss()
                    return@setSingleChoiceItems
                }

                settingsStore.setDomainStrategy(
                    values[which]
                )

                renderDomainStrategy(
                    root
                )

                dialog.dismiss()
            }
            .setNegativeButton(
                android.R.string.cancel,
                null
            )
            .show()
    }

    private fun showFragmentDialog(root: View) {
        val context = requireContext()
        val content = layoutInflater.inflate(
            R.layout.dialog_fragment_material3,
            null
        )

        val packetsLayout =
            content.findViewById<TextInputLayout>(
                R.id.fragment_dialog_packets_layout
            )
        val packetsInput =
            content.findViewById<
                com.google.android.material.textfield.MaterialAutoCompleteTextView
            >(R.id.fragment_dialog_packets_input)

        val lengthLayout =
            content.findViewById<TextInputLayout>(
                R.id.fragment_dialog_length_layout
            )
        val lengthInput =
            content.findViewById<TextInputEditText>(
                R.id.fragment_dialog_length_input
            )

        val intervalLayout =
            content.findViewById<TextInputLayout>(
                R.id.fragment_dialog_interval_layout
            )
        val intervalInput =
            content.findViewById<TextInputEditText>(
                R.id.fragment_dialog_interval_input
            )

        val maxSplitLayout =
            content.findViewById<TextInputLayout>(
                R.id.fragment_dialog_max_split_layout
            )
        val maxSplitInput =
            content.findViewById<TextInputEditText>(
                R.id.fragment_dialog_max_split_input
            )

        val presets = SettingsStore.FRAGMENT_PACKET_PRESETS

        packetsInput.setSimpleItems(presets.toTypedArray())
        packetsInput.setText(
            settingsStore.getFragmentPackets(),
            false
        )

        lengthInput.setText(settingsStore.getFragmentLength())
        intervalInput.setText(settingsStore.getFragmentInterval())
        maxSplitInput.setText(settingsStore.getFragmentMaxSplit())

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.settings_fragment_dialog_title)
            .setView(content)
            .setNegativeButton(
                android.R.string.cancel,
                null
            )
            .setPositiveButton(
                R.string.settings_fragment_dialog_save,
                null
            )
            .create()

        dialog.setOnShowListener {
            dialog.getButton(
                android.content.DialogInterface.BUTTON_POSITIVE
            ).setOnClickListener {
                val packets = packetsInput.text
                    ?.toString()?.trim().orEmpty()
                val length = lengthInput.text
                    ?.toString()?.trim().orEmpty()
                val interval = intervalInput.text
                    ?.toString()?.trim().orEmpty()
                val maxSplit = maxSplitInput.text
                    ?.toString()?.trim().orEmpty()

                packetsLayout.error = null
                lengthLayout.error = null
                intervalLayout.error = null
                maxSplitLayout.error = null

                var valid = true

                if (packets !in presets) {
                    packetsLayout.error = getString(
                        R.string.settings_fragment_packets_invalid
                    )
                    valid = false
                }

                if (!isValidFragmentRange(length)) {
                    lengthLayout.error = getString(
                        R.string.settings_fragment_range_invalid
                    )
                    valid = false
                }

                if (!isValidFragmentRange(interval)) {
                    intervalLayout.error = getString(
                        R.string.settings_fragment_range_invalid
                    )
                    valid = false
                }

                if (!isValidFragmentRange(maxSplit)) {
                    maxSplitLayout.error = getString(
                        R.string.settings_fragment_range_invalid
                    )
                    valid = false
                }

                if (!valid) {
                    return@setOnClickListener
                }

                if (!ensureSettingsCanChange()) {
                    return@setOnClickListener
                }

                val changed =
                    packets != settingsStore.getFragmentPackets() ||
                    length != settingsStore.getFragmentLength() ||
                    interval != settingsStore.getFragmentInterval() ||
                    maxSplit != settingsStore.getFragmentMaxSplit()

                if (changed) {
                    settingsStore.setFragmentPackets(packets)
                    settingsStore.setFragmentLength(length)
                    settingsStore.setFragmentInterval(interval)
                    settingsStore.setFragmentMaxSplit(maxSplit)
                }

                renderFragment(root)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun bindFragment(root: View) {
        val row = root.findViewById<View>(R.id.setting_fragment)
        val toggle = row.findViewById<MaterialSwitch>(R.id.setting_switch)

        toggle.setOnCheckedChangeListener { _, enabled ->
            if (renderingXrayInlineSettings) {
                return@setOnCheckedChangeListener
            }

            if (!ensureSettingsCanChange()) {
                renderFragment(root)
                return@setOnCheckedChangeListener
            }

            settingsStore.setFragmentEnabled(enabled)
            renderFragment(root)
        }

        row.findViewById<View>(
            R.id.fragment_advanced_button
        ).setOnClickListener {
            if (!settingsStore.isFragmentEnabled()) {
                return@setOnClickListener
            }

            if (!ensureSettingsCanChange()) {
                return@setOnClickListener
            }

            showFragmentDialog(root)
        }
    }

    private fun renderFragment(root: View) {
        val row = root.findViewById<View>(R.id.setting_fragment)
        val enabled = settingsStore.isFragmentEnabled()

        renderingXrayInlineSettings = true
        try {
            row.findViewById<MaterialSwitch>(
                R.id.setting_switch
            ).isChecked = enabled

            val advanced = row.findViewById<View>(
                R.id.fragment_advanced_button
            )
            advanced.isEnabled = enabled
            advanced.isClickable = enabled
            advanced.alpha = if (enabled) 1f else 0.55f

            row.findViewById<android.widget.TextView>(
                R.id.fragment_summary
            ).text = getString(
                R.string.settings_fragment_summary_format,
                settingsStore.getFragmentPackets(),
                settingsStore.getFragmentLength(),
                settingsStore.getFragmentInterval(),
                settingsStore.getFragmentMaxSplit()
            )
        } finally {
            renderingXrayInlineSettings = false
        }
    }

    private fun isValidFragmentRange(
        value: String
    ): Boolean {
        val parts =
            value.trim().split("-")

        if (parts.size !in 1..2) {
            return false
        }

        val numbers =
            parts.map {
                it.trim().toIntOrNull()
                    ?: return false
            }

        if (numbers.any { it <= 0 }) {
            return false
        }

        return numbers.size == 1 ||
            numbers[0] <= numbers[1]
    }

    private fun bindMux(root: View) {
        val row = root.findViewById<View>(R.id.setting_mux)

        row.findViewById<MaterialSwitch>(
            R.id.setting_switch
        ).setOnCheckedChangeListener { _, enabled ->
            if (renderingXrayInlineSettings) {
                return@setOnCheckedChangeListener
            }

            if (!ensureSettingsCanChange()) {
                renderMux(root)
                return@setOnCheckedChangeListener
            }

            settingsStore.setMuxEnabled(enabled)
            renderMux(root)
        }

        row.findViewById<View>(
            R.id.mux_advanced_button
        ).setOnClickListener {
            if (settingsStore.isMuxEnabled()) {
                showMuxDialog(root)
            }
        }
    }

    private fun showMuxDialog(root: View) {
        val context = requireContext()
        val view = layoutInflater.inflate(
            R.layout.dialog_mux_material3,
            null,
            false
        )

        val min = SettingsStore.MIN_MUX_CONCURRENCY
        val max = SettingsStore.MAX_MUX_CONCURRENCY
        val modes = SettingsStore.MUX_UDP_443_MODES

        var tcp = settingsStore.getMuxConcurrency()
        var xudp = settingsStore.getMuxXudpConcurrency()

        val tcpValue = view.findViewById<android.widget.TextView>(
            R.id.mux_tcp_value
        )
        val xudpValue = view.findViewById<android.widget.TextView>(
            R.id.mux_xudp_value
        )

        fun updateValues() {
            tcpValue.text = tcp.toString()
            xudpValue.text = xudp.toString()
        }

        updateValues()

        view.findViewById<View>(
            R.id.mux_tcp_minus
        ).setOnClickListener {
            tcp = (tcp - 1).coerceAtLeast(min)
            updateValues()
        }

        view.findViewById<View>(
            R.id.mux_tcp_plus
        ).setOnClickListener {
            tcp = (tcp + 1).coerceAtMost(max)
            updateValues()
        }

        view.findViewById<View>(
            R.id.mux_xudp_minus
        ).setOnClickListener {
            xudp = (xudp - 1).coerceAtLeast(min)
            updateValues()
        }

        view.findViewById<View>(
            R.id.mux_xudp_plus
        ).setOnClickListener {
            xudp = (xudp + 1).coerceAtMost(max)
            updateValues()
        }

        val dropdown = view.findViewById<
            com.google.android.material.textfield.MaterialAutoCompleteTextView
        >(R.id.mux_udp_dropdown)

        val dropdownLayout = view.findViewById<
            com.google.android.material.textfield.TextInputLayout
        >(R.id.mux_udp_layout)

        dropdown.setAdapter(
            android.widget.ArrayAdapter(
                context,
                android.R.layout.simple_dropdown_item_1line,
                modes
            )
        )
        dropdown.setText(
            settingsStore.getMuxUdp443Mode(),
            false
        )

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.settings_mux_dialog_title)
            .setView(view)
            .create()

        view.findViewById<View>(
            R.id.mux_cancel
        ).setOnClickListener {
            dialog.dismiss()
        }

        view.findViewById<View>(
            R.id.mux_save
        ).setOnClickListener {
            val udpMode = dropdown.text
                ?.toString()
                ?.trim()
                ?.lowercase()
                .orEmpty()

            if (udpMode !in modes) {
                dropdownLayout.error = getString(
                    R.string.settings_mux_udp_443_invalid
                )
                return@setOnClickListener
            }

            val changed =
                tcp != settingsStore.getMuxConcurrency() ||
                xudp != settingsStore.getMuxXudpConcurrency() ||
                udpMode != settingsStore.getMuxUdp443Mode()

            if (changed && !ensureSettingsCanChange()) {
                return@setOnClickListener
            }

            if (changed) {
                settingsStore.setMuxConcurrency(tcp)
                settingsStore.setMuxXudpConcurrency(xudp)
                settingsStore.setMuxUdp443Mode(udpMode)
            }

            renderMux(root)
            dialog.dismiss()
        }

        dialog.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams
                .SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
        dialog.show()
    }

    private fun renderMux(root: View) {
        val row = root.findViewById<View>(R.id.setting_mux)
        val enabled = settingsStore.isMuxEnabled()

        renderingXrayInlineSettings = true
        try {
            row.findViewById<MaterialSwitch>(
                R.id.setting_switch
            ).isChecked = enabled
        } finally {
            renderingXrayInlineSettings = false
        }

        row.findViewById<android.widget.TextView>(
            R.id.mux_summary
        ).text = if (enabled) {
            getString(
                R.string.settings_mux_summary_active,
                settingsStore.getMuxConcurrency(),
                settingsStore.getMuxXudpConcurrency(),
                settingsStore.getMuxUdp443Mode()
            )
        } else {
            getString(R.string.settings_mux_summary_off)
        }

        row.findViewById<View>(
            R.id.mux_advanced_button
        ).apply {
            isEnabled = enabled
            isClickable = enabled
            alpha = if (enabled) 1f else 0.55f
        }
    }

    private fun bindSniffing(root: View) {
        val row = root.findViewById<View>(R.id.setting_sniffing)

        row.findViewById<MaterialSwitch>(
            R.id.setting_switch
        ).setOnCheckedChangeListener { _, enabled ->
            if (renderingXrayInlineSettings) {
                return@setOnCheckedChangeListener
            }

            if (!ensureSettingsCanChange()) {
                renderSniffing(root)
                return@setOnCheckedChangeListener
            }

            settingsStore.setSniffingEnabled(enabled)
            renderSniffing(root)
        }

        row.findViewById<View>(
            R.id.sniffing_advanced_button
        ).setOnClickListener {
            if (settingsStore.isSniffingEnabled()) {
                showSniffingDialog(root)
            }
        }
    }

    private fun showSniffingDialog(root: View) {
        val context = requireContext()
        val density = resources.displayMetrics.density

        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(
                (24 * density).toInt(),
                (8 * density).toInt(),
                (24 * density).toInt(),
                (8 * density).toInt()
            )
        }

        fun makeOption(
            title: Int,
            checked: Boolean
        ): MaterialSwitch {
            return MaterialSwitch(context).apply {
                text = getString(title)
                isChecked = checked
                minHeight = (56 * density).toInt()
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                container.addView(this)
            }
        }

        val routeOnly = makeOption(
            R.string.settings_sniffing_route_only,
            settingsStore.isSniffingRouteOnly()
        )
        val http = makeOption(
            R.string.settings_sniffing_http,
            settingsStore.isSniffingHttpEnabled()
        )
        val tls = makeOption(
            R.string.settings_sniffing_tls,
            settingsStore.isSniffingTlsEnabled()
        )
        val quic = makeOption(
            R.string.settings_sniffing_quic,
            settingsStore.isSniffingQuicEnabled()
        )

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.settings_sniffing_dialog_title)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(
                R.string.settings_sniffing_dialog_save,
                null
            )
            .create()

        dialog.setOnShowListener {
            dialog.getButton(
                android.content.DialogInterface.BUTTON_POSITIVE
            ).setOnClickListener {
                val newRouteOnly = routeOnly.isChecked
                val newHttp = http.isChecked
                val newTls = tls.isChecked
                val newQuic = quic.isChecked

                val changed =
                    newRouteOnly != settingsStore.isSniffingRouteOnly() ||
                    newHttp != settingsStore.isSniffingHttpEnabled() ||
                    newTls != settingsStore.isSniffingTlsEnabled() ||
                    newQuic != settingsStore.isSniffingQuicEnabled()

                if (changed && !ensureSettingsCanChange()) {
                    return@setOnClickListener
                }

                if (changed) {
                    settingsStore.setSniffingRouteOnly(newRouteOnly)
                    settingsStore.setSniffingHttpEnabled(newHttp)
                    settingsStore.setSniffingTlsEnabled(newTls)
                    settingsStore.setSniffingQuicEnabled(newQuic)
                }

                renderSniffing(root)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun renderSniffing(root: View) {
        val row = root.findViewById<View>(R.id.setting_sniffing)
        val enabled = settingsStore.isSniffingEnabled()

        renderingXrayInlineSettings = true
        try {
            row.findViewById<MaterialSwitch>(
                R.id.setting_switch
            ).isChecked = enabled
        } finally {
            renderingXrayInlineSettings = false
        }

        val details = mutableListOf<String>()

        if (enabled) {
            if (settingsStore.isSniffingRouteOnly()) {
                details.add(getString(R.string.settings_sniffing_summary_routing))
            }
            if (settingsStore.isSniffingHttpEnabled()) {
                details.add("HTTP")
            }
            if (settingsStore.isSniffingTlsEnabled()) {
                details.add("TLS")
            }
            if (settingsStore.isSniffingQuicEnabled()) {
                details.add("QUIC")
            }
        }

        val summary = when {
            !enabled -> getString(R.string.settings_sniffing_summary_off)
            details.isEmpty() -> getString(R.string.settings_sniffing_summary_empty)
            else -> details.joinToString(" · ")
        }

        row.findViewById<android.widget.TextView>(
            R.id.sniffing_summary
        ).text = summary

        row.findViewById<View>(
            R.id.sniffing_advanced_button
        ).apply {
            isEnabled = enabled
            isClickable = enabled
            alpha = if (enabled) 1f else 0.55f
        }
    }

    private fun bindFakeDns(
        root: View
    ) {
        val row =
            root.findViewById<View>(
                R.id.setting_fakedns
            )

        val switch =
            row.findViewById<MaterialSwitch>(
                R.id.setting_switch
            )

        switch.isClickable = false

        row.setOnClickListener {
            if (!ensureSettingsCanChange()) {
                renderFakeDns(root)
                return@setOnClickListener
            }

            val enabled =
                !settingsStore.isFakeDnsEnabled()

            settingsStore.setFakeDnsEnabled(
                enabled
            )

            renderFakeDns(root)
        }
    }

    private fun renderFakeDns(
        root: View
    ) {
        root.findViewById<View>(
            R.id.setting_fakedns
        ).findViewById<MaterialSwitch>(
            R.id.setting_switch
        ).isChecked =
            settingsStore.isFakeDnsEnabled()
    }

    private fun bindDirectDns(
        root: View
    ) {
        root.findViewById<View>(
            R.id.setting_direct_dns
        ).setOnClickListener {
            showDirectDnsModeDialog(
                root
            )
        }
    }

    private fun renderDirectDns(
        root: View
    ) {
        val directDns =
            settingsStore.getDirectDns()

        val displayValue =
            when (directDns) {
                "System" ->
                    getString(
                        R.string.settings_value_system
                    )

                "1.1.1.1" ->
                    getString(
                        R.string.settings_value_cloudflare
                    )

                "8.8.8.8" ->
                    getString(
                        R.string.settings_value_google
                    )

                "9.9.9.9" ->
                    getString(
                        R.string.settings_value_quad9
                    )

                else ->
                    directDns
            }

        root.findViewById<View>(
            R.id.setting_direct_dns
        ).findViewById<TextView>(
            R.id.setting_value
        ).text =
            displayValue
    }

    private fun showDirectDnsModeDialog(
        root: View
    ) {
        if (!ensureSettingsCanChange()) {
            return
        }

        val current =
            settingsStore.getDirectDns()

        val options =
            arrayOf(
                getString(
                    R.string.settings_value_system
                ),
                getString(
                    R.string.settings_value_custom
                )
            )

        val checked =
            if (current == "System") {
                0
            } else {
                1
            }

        MaterialAlertDialogBuilder(
            requireContext()
        )
            .setTitle(
                R.string.settings_direct_dns
            )
            .setSingleChoiceItems(
                options,
                checked
            ) { dialog, which ->
                when (which) {
                    0 -> {
                        if (!ensureSettingsCanChange()) {
                            renderDirectDns(root)
                            dialog.dismiss()
                            return@setSingleChoiceItems
                        }

                        settingsStore.setDirectDns(
                            "System"
                        )

                        renderDirectDns(
                            root
                        )

                        dialog.dismiss()
                    }

                    1 -> {
                        dialog.dismiss()

                        showDirectDnsCustomDialog(
                            root
                        )
                    }
                }
            }
            .setNegativeButton(
                android.R.string.cancel,
                null
            )
            .show()
    }

    private fun showDirectDnsCustomDialog(
        root: View
    ) {
        if (!ensureSettingsCanChange()) {
            return
        }

        val content =
            layoutInflater.inflate(
                R.layout.dialog_direct_dns,
                null,
                false
            )

        val inputLayout =
            content.findViewById<TextInputLayout>(
                R.id.direct_dns_input_layout
            )

        val input =
            content.findViewById<TextInputEditText>(
                R.id.direct_dns_input
            )

        val current =
            settingsStore.getDirectDns()

        if (current != "System") {
            input.setText(
                current
            )
        }

        val dialog =
            MaterialAlertDialogBuilder(
                requireContext()
            )
                .setTitle(
                    R.string.settings_direct_dns
                )
                .setView(
                    content
                )
                .setNegativeButton(
                    android.R.string.cancel,
                    null
                )
                .setPositiveButton(
                    android.R.string.ok,
                    null
                )
                .create()

        dialog.setOnShowListener {
            dialog.getButton(
                android.app.AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {
                val value =
                    input.text
                        ?.toString()
                        ?.trim()
                        .orEmpty()

                if (value.isEmpty()) {
                    inputLayout.error =
                        getString(R.string.settings_direct_dns_required)

                    return@setOnClickListener
                }

                inputLayout.error =
                    null

                if (!ensureSettingsCanChange()) {
                    renderDirectDns(root)
                    dialog.dismiss()
                    return@setOnClickListener
                }

                settingsStore.setDirectDns(
                    value
                )

                renderDirectDns(
                    root
                )

                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun bindRemoteDns(
        root: View
    ) {
        val row =
            root.findViewById<View>(
                R.id.setting_remote_dns
            )

        val switch =
            row.findViewById<MaterialSwitch>(
                R.id.setting_switch
            )

        switch.setOnCheckedChangeListener {
                button,
                enabled ->

            if (!button.isPressed) {
                return@setOnCheckedChangeListener
            }

            if (!ensureSettingsCanChange()) {
                renderRemoteDns(root)
                return@setOnCheckedChangeListener
            }

            settingsStore.setRemoteDnsEnabled(
                enabled
            )

            renderRemoteDns(root)
        }

        row.setOnClickListener {
            showRemoteDnsDialog(
                root
            )
        }
    }

    private fun renderRemoteDns(
        root: View
    ) {
        val remoteDns =
            settingsStore.getRemoteDns()

        val displayValue =
            when (remoteDns) {
                "https://cloudflare-dns.com/dns-query" ->
                    getString(
                        R.string.settings_value_cloudflare_doh
                    )

                "https://dns.google/dns-query" ->
                    getString(
                        R.string.settings_value_google_doh
                    )

                "1.1.1.1" ->
                    getString(
                        R.string.settings_value_cloudflare
                    )

                "8.8.8.8" ->
                    getString(
                        R.string.settings_value_google
                    )

                else ->
                    remoteDns
            }

        root.findViewById<View>(
            R.id.setting_remote_dns
        ).findViewById<TextView>(
            R.id.setting_value
        ).text =
            displayValue
        val enabled =
            settingsStore.isRemoteDnsEnabled()

        root.findViewById<View>(
            R.id.setting_remote_dns
        ).findViewById<MaterialSwitch>(
            R.id.setting_switch
        ).isChecked =
            enabled

        root.findViewById<View>(
            R.id.setting_remote_dns
        ).findViewById<TextView>(
            R.id.setting_value
        ).alpha =
            if (enabled) {
                1.0f
            } else {
                0.5f
            }

    }

    private fun showRemoteDnsDialog(
        root: View
    ) {
        if (!ensureSettingsCanChange()) {
            return
        }

        val content =
            layoutInflater.inflate(
                R.layout.dialog_remote_dns,
                null,
                false
            )

        val inputLayout =
            content.findViewById<TextInputLayout>(
                R.id.remote_dns_input_layout
            )

        val input =
            content.findViewById<TextInputEditText>(
                R.id.remote_dns_input
            )

        input.setText(
            settingsStore.getRemoteDns()
        )

        val dialog =
            MaterialAlertDialogBuilder(
                requireContext()
            )
                .setTitle(
                    R.string.settings_remote_dns
                )
                .setView(
                    content
                )
                .setNegativeButton(
                    android.R.string.cancel,
                    null
                )
                .setPositiveButton(
                    android.R.string.ok,
                    null
                )
                .create()

        dialog.setOnShowListener {
            dialog.getButton(
                android.app.AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {
                val value =
                    input.text
                        ?.toString()
                        ?.trim()
                        .orEmpty()

                if (value.isEmpty()) {
                    inputLayout.error =
                        getString(R.string.settings_remote_dns_required)

                    return@setOnClickListener
                }

                inputLayout.error =
                    null

                if (!ensureSettingsCanChange()) {
                    renderRemoteDns(root)
                    dialog.dismiss()
                    return@setOnClickListener
                }

                settingsStore.setRemoteDns(
                    value
                )

                renderRemoteDns(
                    root
                )

                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun bindPerAppVpn(
        root: View
    ) {
        root.findViewById<View>(
            R.id.setting_per_app_vpn
        ).setOnClickListener {
            startActivity(
                Intent(
                    requireContext(),
                    PerAppVpnActivity::class.java
                )
            )
        }
    }

    private fun renderPerAppVpn(
        root: View
    ) {
        val value =
            root.findViewById<View>(
                R.id.setting_per_app_vpn
            ).findViewById<TextView>(
                R.id.setting_value
            )

        val count =
            settingsStore
                .getPerAppVpnPackages()
                .size

        value.text =
            when (
                settingsStore.getPerAppVpnMode()
            ) {
                PerAppVpnMode.ALL_APPS ->
                    getString(
                        R.string.settings_value_all_apps
                    )

                PerAppVpnMode.SELECTED_APPS ->
                    getString(
                        R.string.settings_value_selected_count,
                        count
                    )

                PerAppVpnMode.BYPASS_SELECTED ->
                    getString(
                        R.string.settings_value_bypass_count,
                        count
                    )
            }
    }

    private fun bindLanguage(
        root: View
    ) {
        root.findViewById<View>(
            R.id.setting_language
        ).setOnClickListener {
            showLanguageDialog()
        }
    }

    private fun showLanguageDialog() {
        val labels = arrayOf(
            getString(
                R.string.settings_value_system_default
            ),
            getString(
                R.string.settings_language_english
            ),
            getString(
                R.string.settings_language_indonesian
            )
        )

        val tags = arrayOf(
            "",
            "en",
            "id"
        )

        val currentTags =
            AppCompatDelegate
                .getApplicationLocales()
                .toLanguageTags()

        val currentLanguage =
            currentTags
                .split(",")
                .firstOrNull()
                .orEmpty()

        val checked =
            when {
                currentLanguage.isEmpty() ->
                    0

                currentLanguage.startsWith("en") ->
                    1

                currentLanguage.startsWith("id") ->
                    2

                else ->
                    0
            }

        MaterialAlertDialogBuilder(
            requireContext()
        )
            .setTitle(
                R.string.settings_language
            )
            .setSingleChoiceItems(
                labels,
                checked
            ) { dialog, which ->

                val selectedLocales =
                    if (tags[which].isEmpty()) {
                        LocaleListCompat
                            .getEmptyLocaleList()
                    } else {
                        LocaleListCompat
                            .forLanguageTags(
                                tags[which]
                            )
                    }

                if (
                    selectedLocales
                        .toLanguageTags() !=
                    AppCompatDelegate
                        .getApplicationLocales()
                        .toLanguageTags()
                ) {
                    AppCompatDelegate
                        .setApplicationLocales(
                            selectedLocales
                        )
                }

                dialog.dismiss()
            }
            .setNegativeButton(
                R.string.action_cancel,
                null
            )
            .show()
    }

    private fun bindTheme(
        root: View
    ) {
        root.findViewById<View>(
            R.id.setting_theme
        ).setOnClickListener {
            showThemeDialog()
        }
    }

    private fun showThemeDialog() {
        val themes = arrayOf(
            getString(
                R.string.settings_value_system_default
            ),
            getString(
                R.string.settings_theme_light
            ),
            getString(
                R.string.settings_theme_dark
            )
        )

        val values = arrayOf(
            AppTheme.SYSTEM,
            AppTheme.LIGHT,
            AppTheme.DARK
        )

        val current =
            settingsStore.getTheme()

        val checked =
            values.indexOf(current)
                .coerceAtLeast(0)

        MaterialAlertDialogBuilder(
            requireContext()
        )
            .setTitle(R.string.settings_theme)
            .setSingleChoiceItems(
                themes,
                checked
            ) { dialog, which ->

                val selected =
                    values[which]

                if (
                    selected !=
                    settingsStore.getTheme()
                ) {
                    settingsStore.setTheme(
                        selected
                    )

                    ThemeManager.apply(
                        selected
                    )

                    view?.let {
                        renderTheme(it)
                    }
                }

                dialog.dismiss()
            }
            .setNegativeButton(
                android.R.string.cancel,
                null
            )
            .show()
    }

    private fun bindBypassLan(
        root: View
    ) {
        root.findViewById<View>(
            R.id.setting_bypass_lan
        ).setOnClickListener {
            showBypassLanDialog(root)
        }
    }

    private fun renderBypassLan(
        root: View
    ) {
        val label =
            when (
                settingsStore.getBypassLanMode()
            ) {
                BypassLanMode.FOLLOW_CONFIG ->
                    R.string.settings_bypass_lan_follow_config

                BypassLanMode.BYPASS ->
                    R.string.settings_bypass_lan_bypass

                BypassLanMode.NOT_BYPASS ->
                    R.string.settings_bypass_lan_not_bypass
            }

        root.findViewById<View>(
            R.id.setting_bypass_lan
        ).findViewById<TextView>(
            R.id.setting_value
        ).text = getString(label)
    }

    private fun showBypassLanDialog(
        root: View
    ) {
        if (!ensureSettingsCanChange()) {
            renderBypassLan(root)
            return
        }

        val modes =
            arrayOf(
                BypassLanMode.FOLLOW_CONFIG,
                BypassLanMode.BYPASS,
                BypassLanMode.NOT_BYPASS
            )

        val labels =
            arrayOf(
                getString(
                    R.string.settings_bypass_lan_follow_config
                ),
                getString(
                    R.string.settings_bypass_lan_bypass
                ),
                getString(
                    R.string.settings_bypass_lan_not_bypass
                )
            )

        val checked =
            modes.indexOf(
                settingsStore.getBypassLanMode()
            ).coerceAtLeast(0)

        MaterialAlertDialogBuilder(
            requireContext()
        )
            .setTitle(
                R.string.settings_bypass_lan
            )
            .setSingleChoiceItems(
                labels,
                checked
            ) { dialog, which ->
                settingsStore.setBypassLanMode(
                    modes[which]
                )

                renderBypassLan(root)
                dialog.dismiss()
            }
            .setNegativeButton(
                R.string.action_cancel,
                null
            )
            .show()
    }

    private fun bindIpv6(
        root: View
    ) {
        val row =
            root.findViewById<View>(
                R.id.setting_ipv6
            )

        val toggle =
            row.findViewById<MaterialSwitch>(
                R.id.setting_switch
            )

        toggle.isClickable = false

        row.setOnClickListener {
            if (!ensureSettingsCanChange()) {
                renderIpv6(root)
                return@setOnClickListener
            }

            settingsStore.setIpv6Enabled(
                !settingsStore.isIpv6Enabled()
            )

            renderIpv6(root)
        }
    }

    private fun renderIpv6(
        root: View
    ) {
        val toggle =
            root.findViewById<View>(
                R.id.setting_ipv6
            ).findViewById<MaterialSwitch>(
                R.id.setting_switch
            )

        toggle.setOnCheckedChangeListener(null)

        toggle.isChecked =
            settingsStore.isIpv6Enabled()

        toggle.isClickable = false
    }

    private fun bindVpnInterfaceAddress(
        root: View
    ) {
        root.findViewById<View>(
            R.id.setting_vpn_interface_address
        ).setOnClickListener {
            if (!ensureSettingsCanChange()) {
                renderVpnInterfaceAddress(root)
                return@setOnClickListener
            }

            val labels =
                SettingsStore
                    .VPN_INTERFACE_ADDRESS_LABELS
                    .toTypedArray()

            MaterialAlertDialogBuilder(
                requireContext()
            )
                .setTitle(
                    R.string.settings_vpn_interface_address
                )
                .setSingleChoiceItems(
                    labels,
                    settingsStore
                        .getVpnInterfaceAddressIndex()
                ) { dialog, which ->

                    if (!ensureSettingsCanChange()) {
                        renderVpnInterfaceAddress(root)
                        dialog.dismiss()
                        return@setSingleChoiceItems
                    }

                    settingsStore
                        .setVpnInterfaceAddressIndex(which)

                    renderVpnInterfaceAddress(root)
                    dialog.dismiss()
                }
                .setNegativeButton(
                    android.R.string.cancel,
                    null
                )
                .show()
        }
    }

    private fun renderVpnInterfaceAddress(
        root: View
    ) {
        val index =
            settingsStore
                .getVpnInterfaceAddressIndex()

        val row =
            root.findViewById<View>(
                R.id.setting_vpn_interface_address
            )

        row.findViewById<TextView>(
            R.id.setting_value
        ).text =
            SettingsStore
                .VPN_INTERFACE_ADDRESS_LABELS[index]
    }

    private fun bindMtu(
        root: View
    ) {
        root.findViewById<View>(
            R.id.setting_mtu
        ).setOnClickListener {
            showMtuDialog()
        }
    }

    private fun showMtuDialog() {
        if (!ensureSettingsCanChange()) {
            return
        }

        val dialogView =
            LayoutInflater.from(
                requireContext()
            ).inflate(
                R.layout.dialog_mtu,
                null
            )

        val inputLayout =
            dialogView.findViewById<TextInputLayout>(
                R.id.mtu_input_layout
            )

        val input =
            dialogView.findViewById<TextInputEditText>(
                R.id.mtu_input
            )

        input.setText(
            settingsStore
                .getMtu()
                .toString()
        )

        input.selectAll()

        val dialog =
            MaterialAlertDialogBuilder(
                requireContext()
            )
                .setTitle(
                    R.string.settings_mtu
                )
                .setView(
                    dialogView
                )
                .setNegativeButton(
                    android.R.string.cancel,
                    null
                )
                .setPositiveButton(
                    android.R.string.ok,
                    null
                )
                .create()

        dialog.setOnShowListener {
            dialog.getButton(
                android.content.DialogInterface
                    .BUTTON_POSITIVE
            ).setOnClickListener {
                val mtu =
                    input.text
                        ?.toString()
                        ?.trim()
                        ?.toIntOrNull()

                if (
                    mtu == null ||
                    mtu !in
                        SettingsStore.MIN_MTU..
                            SettingsStore.MAX_MTU
                ) {
                    inputLayout.error =
                        getString(
                            R.string.settings_mtu_invalid
                        )

                    return@setOnClickListener
                }

                inputLayout.error = null

                if (!ensureSettingsCanChange()) {
                    view?.let {
                        renderMtu(it)
                    }
                    dialog.dismiss()
                    return@setOnClickListener
                }

                settingsStore.setMtu(
                    mtu
                )

                view?.let {
                    renderMtu(it)
                }

                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun renderMtu(
        root: View
    ) {
        val row =
            root.findViewById<View>(
                R.id.setting_mtu
            )

        row.findViewById<TextView>(
            R.id.setting_value
        ).text =
            settingsStore
                .getMtu()
                .toString()
    }

    private fun renderLanguage(
        root: View
    ) {
        val row =
            root.findViewById<View>(
                R.id.setting_language
            )

        val value =
            row.findViewById<TextView>(
                R.id.setting_value
            )

        val languageTags =
            AppCompatDelegate
                .getApplicationLocales()
                .toLanguageTags()

        value.text =
            when {
                languageTags.isBlank() ->
                    getString(
                        R.string.settings_value_system_default
                    )

                languageTags
                    .split(",")
                    .firstOrNull()
                    ?.startsWith("id") == true ->
                    getString(
                        R.string.settings_language_indonesian
                    )

                else ->
                    getString(
                        R.string.settings_language_english
                    )
            }
    }

    private fun renderTheme(
        root: View
    ) {
        val row =
            root.findViewById<View>(
                R.id.setting_theme
            )

        val value =
            row.findViewById<TextView>(
                R.id.setting_value
            )

        value.text =
            when (
                settingsStore.getTheme()
            ) {
                AppTheme.SYSTEM ->
                    getString(
                        R.string.settings_value_system_default
                    )

                AppTheme.LIGHT ->
                    getString(
                        R.string.settings_theme_light
                    )

                AppTheme.DARK ->
                    getString(
                        R.string.settings_theme_dark
                    )
            }
    }


}
