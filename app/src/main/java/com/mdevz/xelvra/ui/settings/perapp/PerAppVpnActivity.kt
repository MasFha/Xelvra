package com.mdevz.xelvra.ui.settings.perapp

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mdevz.xelvra.R
import com.mdevz.xelvra.core.vpn.VpnRuntimeManager
import com.mdevz.xelvra.data.local.PerAppVpnMode
import com.mdevz.xelvra.data.local.SettingsStore
import java.util.Locale
import java.util.concurrent.Executors

class PerAppVpnActivity :
    AppCompatActivity() {

    private lateinit var settingsStore:
        SettingsStore

    private lateinit var adapter:
        PerAppVpnAdapter

    private lateinit var list:
        RecyclerView

    private lateinit var empty:
        TextView

    private lateinit var count:
        TextView

    private var allApps:
        List<PerAppEntry> =
        emptyList()

    private val appLoader =
        Executors.newSingleThreadExecutor()

    private val searchHandler =
        Handler(
            Looper.getMainLooper()
        )

    private var searchRunnable:
        Runnable? = null

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContentView(
            R.layout.activity_per_app_vpn
        )

        settingsStore =
            SettingsStore(this)

        configureInsets()
        configureToolbar()
        configureMode()
        configureApps()
        configureSearch()
    }

    private fun ensurePerAppCanChange(): Boolean {
        if (!VpnRuntimeManager.isSessionActive()) {
            return true
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(
                R.string.vpn_running_per_app_title
            )
            .setMessage(
                R.string.vpn_running_per_app_message
            )
            .setPositiveButton(
                R.string.action_ok,
                null
            )
            .show()

        return false
    }

    private fun configureInsets() {
        val root =
            findViewById<View>(
                R.id.per_app_root
            )

        val toolbar =
            findViewById<View>(
                R.id.per_app_toolbar
            )

        val content =
            findViewById<View>(
                R.id.per_app_content
            )

        val toolbarBaseHeight =
            dp(56)

        val contentBottom =
            content.paddingBottom

        ViewCompat
            .setOnApplyWindowInsetsListener(
                root
            ) { _, insets ->

                val status =
                    insets.getInsets(
                        WindowInsetsCompat
                            .Type.statusBars()
                    )

                val navigation =
                    insets.getInsets(
                        WindowInsetsCompat
                            .Type.navigationBars()
                    )

                toolbar.layoutParams =
                    toolbar.layoutParams
                        .apply {
                            height =
                                toolbarBaseHeight +
                                    status.top
                        }

                toolbar.setPadding(
                    toolbar.paddingLeft,
                    status.top,
                    toolbar.paddingRight,
                    toolbar.paddingBottom
                )

                content.setPadding(
                    content.paddingLeft,
                    content.paddingTop,
                    content.paddingRight,
                    contentBottom +
                        navigation.bottom
                )

                insets
            }
    }

    private fun configureToolbar() {
        val toolbar =
            findViewById<MaterialToolbar>(
                R.id.per_app_toolbar
            )

        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun modeRadioId(
        mode: PerAppVpnMode
    ): Int {
        return when (mode) {
            PerAppVpnMode.ALL_APPS ->
                R.id.mode_all_apps

            PerAppVpnMode.SELECTED_APPS ->
                R.id.mode_selected_apps

            PerAppVpnMode.BYPASS_SELECTED ->
                R.id.mode_bypass_selected
        }
    }

    private fun modeFromRadioId(
        checkedId: Int
    ): PerAppVpnMode {
        return when (checkedId) {
            R.id.mode_selected_apps ->
                PerAppVpnMode.SELECTED_APPS

            R.id.mode_bypass_selected ->
                PerAppVpnMode.BYPASS_SELECTED

            else ->
                PerAppVpnMode.ALL_APPS
        }
    }

    private fun configureMode() {
        val group =
            findViewById<RadioGroup>(
                R.id.per_app_mode_group
            )

        group.setOnCheckedChangeListener(
            null
        )

        val current =
            settingsStore
                .getPerAppVpnMode()

        group.check(
            modeRadioId(
                current
            )
        )

        updateAppControls(
            current
        )

        group.setOnCheckedChangeListener {
                radioGroup,
                checkedId ->

            val requested =
                modeFromRadioId(
                    checkedId
                )

            val persisted =
                settingsStore
                    .getPerAppVpnMode()

            if (requested == persisted) {
                updateAppControls(
                    persisted
                )
                return@setOnCheckedChangeListener
            }

            if (!ensurePerAppCanChange()) {
                radioGroup.setOnCheckedChangeListener(
                    null
                )

                radioGroup.check(
                    modeRadioId(
                        persisted
                    )
                )

                updateAppControls(
                    persisted
                )

                configureMode()
                return@setOnCheckedChangeListener
            }

            settingsStore
                .setPerAppVpnMode(
                    requested
                )

            updateAppControls(
                requested
            )
        }
    }

    private fun configureApps() {
        list =
            findViewById(
                R.id.per_app_list
            )

        empty =
            findViewById(
                R.id.per_app_empty
            )

        count =
            findViewById(
                R.id.per_app_selected_count
            )

        val selected =
            settingsStore
                .getPerAppVpnPackages()
                .toMutableSet()

        adapter =
            PerAppVpnAdapter(
                packageManager =
                    packageManager,
                selectedPackages =
                    selected
            ) { packages ->

                if (!ensurePerAppCanChange()) {
                    false
                } else {
                    settingsStore
                        .setPerAppVpnPackages(
                            packages
                        )

                    updateCount(
                        packages.size
                    )

                    true
                }
            }

        val layoutManager =
            LinearLayoutManager(
                this
            ).apply {
                initialPrefetchItemCount =
                    8
            }

        list.layoutManager =
            layoutManager

        list.adapter =
            adapter

        list.setHasFixedSize(true)

        list.itemAnimator =
            null

        list.setItemViewCacheSize(
            12
        )

        list.recycledViewPool
            .setMaxRecycledViews(
                0,
                20
            )

        updateCount(
            selected.size
        )

        loadAppsAsync()
    }

    private fun loadAppsAsync() {
        val loading =
            findViewById<View>(
                R.id.per_app_loading
            )

        loading.visibility =
            View.VISIBLE

        empty.visibility =
            View.GONE

        list.visibility =
            View.INVISIBLE

        appLoader.execute {
            val apps =
                runCatching {
                    loadLaunchableApps()
                }.getOrElse {
                    emptyList()
                }

            runOnUiThread {
                if (
                    isFinishing ||
                    isDestroyed
                ) {
                    return@runOnUiThread
                }

                allApps =
                    apps

                adapter.submitList(
                    apps
                )

                loading.visibility =
                    View.GONE

                list.visibility =
                    View.VISIBLE

                updateEmptyState()

                updateAppControls(
                    settingsStore
                        .getPerAppVpnMode()
                )
            }
        }
    }

    private fun configureSearch() {
        findViewById<EditText>(
            R.id.per_app_search
        ).doAfterTextChanged {
            val query =
                it?.toString()
                    .orEmpty()

            searchRunnable?.let(
                searchHandler::removeCallbacks
            )

            searchRunnable =
                Runnable {
                    filterApps(
                        query
                    )
                }

            searchHandler.postDelayed(
                searchRunnable!!,
                180L
            )
        }
    }

    private fun filterApps(
        query: String
    ) {
        val normalized =
            query.trim()
                .lowercase(
                    Locale.getDefault()
                )

        val filtered =
            if (
                normalized.isEmpty()
            ) {
                allApps
            } else {
                allApps.filter { item ->
                    item.label
                        .lowercase(
                            Locale.getDefault()
                        )
                        .contains(
                            normalized
                        ) ||
                        item.packageName
                            .lowercase(
                                Locale.getDefault()
                            )
                            .contains(
                                normalized
                            )
                }
            }

        adapter.submitList(
            filtered
        ) {
            updateEmptyState()
        }
    }

    private fun loadLaunchableApps():
        List<PerAppEntry> {

        val launcherIntent =
            Intent(
                Intent.ACTION_MAIN
            ).apply {
                addCategory(
                    Intent.CATEGORY_LAUNCHER
                )
            }

        val activities =
            if (
                android.os.Build.VERSION.SDK_INT >=
                android.os.Build.VERSION_CODES.TIRAMISU
            ) {
                packageManager
                    .queryIntentActivities(
                        launcherIntent,
                        PackageManager
                            .ResolveInfoFlags
                            .of(0)
                    )
            } else {
                @Suppress("DEPRECATION")
                packageManager
                    .queryIntentActivities(
                        launcherIntent,
                        0
                    )
            }

        return activities
            .asSequence()
            .mapNotNull { info ->

                val packageName =
                    info.activityInfo
                        ?.packageName
                        ?: return@mapNotNull null

                if (
                    packageName ==
                    this.packageName
                ) {
                    return@mapNotNull null
                }

                PerAppEntry(
                    packageName =
                        packageName,
                    label =
                        info.loadLabel(
                            packageManager
                        )
                            ?.toString()
                            ?.trim()
                            ?.takeIf {
                                it.isNotEmpty()
                            }
                            ?: packageName
                )
            }
            .distinctBy {
                it.packageName
            }
            .sortedWith(
                compareBy(
                    String
                        .CASE_INSENSITIVE_ORDER
                ) {
                    it.label
                }
            )
            .toList()
    }

    private fun updateAppControls(
        mode: PerAppVpnMode
    ) {
        val enabled =
            mode !=
                PerAppVpnMode.ALL_APPS

        findViewById<View>(
            R.id.per_app_search
        ).isEnabled =
            enabled

        findViewById<View>(
            R.id.applications_header
        ).alpha =
            if (enabled) {
                1f
            } else {
                0.45f
            }

        findViewById<View>(
            R.id.per_app_list_card
        ).alpha =
            if (enabled) {
                1f
            } else {
                0.45f
            }

        if (::list.isInitialized) {
            list.isEnabled =
                enabled
        }
    }

    private fun updateCount(
        selected: Int
    ) {
        count.text =
            selected.toString()
    }

    private fun updateEmptyState() {
        val isEmpty =
            adapter.currentList
                .isEmpty()

        list.visibility =
            if (isEmpty) {
                View.GONE
            } else {
                View.VISIBLE
            }

        empty.visibility =
            if (isEmpty) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun dp(
        value: Int
    ): Int {
        return (
            value *
                resources
                    .displayMetrics
                    .density
            ).toInt()
    }

    override fun onResume() {
        super.onResume()

        configureMode()

        if (::adapter.isInitialized) {
            val persisted =
                settingsStore
                    .getPerAppVpnPackages()

            adapter.replaceSelectedPackages(
                persisted
            )

            updateCount(
                persisted.size
            )
        }
    }

    override fun onDestroy() {
        searchRunnable?.let(
            searchHandler::removeCallbacks
        )

        appLoader.shutdownNow()

        if (::adapter.isInitialized) {
            adapter.close()
        }

        super.onDestroy()
    }
}
