package com.mdevz.xelvra

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import com.mdevz.xelvra.core.vpn.VpnRuntimeState
import com.mdevz.xelvra.core.vpn.VpnRuntimeManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.mdevz.xelvra.ui.home.HomeFragment
import com.mdevz.xelvra.ui.logs.LogsFragment
import com.mdevz.xelvra.ui.profiles.ProfilesFragment
import com.mdevz.xelvra.ui.settings.SettingsFragment

class MainActivity : AppCompatActivity() {

    init {
        isAliveInProcess = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    showExitConfirmation()
                }
            }
        )

        val root = findViewById<View>(R.id.root)
        val fragmentContainer =
            findViewById<View>(R.id.fragment_container)

        val bottomNavigation =
            findViewById<BottomNavigationView>(R.id.bottom_navigation)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            root.setPadding(
                bars.left,
                bars.top,
                bars.right,
                0
            )

            bottomNavigation.setPadding(
                0,
                0,
                0,
                bars.bottom
            )

            bottomNavigation.post {
                fragmentContainer.updateLayoutParams<
                    androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
                > {
                    bottomMargin =
                        bottomNavigation.height
                }
            }

            insets
        }

        if (savedInstanceState == null) {
            bottomNavigation.setOnItemSelectedListener { item ->
                showNavigationDestination(
                    item.itemId
                )
            }

            bottomNavigation.selectedItemId =
                R.id.navigation_home
        } else {
            /*
             * FragmentManager restores the existing fragment,
             * including the Settings view hierarchy and its
             * NestedScrollView state. Do not replace it here.
             */
            bottomNavigation.post {
                bottomNavigation.setOnItemSelectedListener { item ->
                    showNavigationDestination(
                        item.itemId
                    )
                }
            }
        }
    }
    private fun showExitConfirmation() {
        val vpnActive =
            when (VpnRuntimeManager.getState()) {
                VpnRuntimeState.Preparing,
                VpnRuntimeState.Connecting,
                is VpnRuntimeState.Connected,
                VpnRuntimeState.WaitingForNetwork,
                VpnRuntimeState.Reconnecting ->
                    true

                VpnRuntimeState.Disconnecting,
                VpnRuntimeState.Disconnected,
                is VpnRuntimeState.Failed ->
                    false
            }

        val message =
            if (vpnActive) {
                R.string.exit_dialog_message_vpn_active
            } else {
                R.string.exit_dialog_message
            }

        MaterialAlertDialogBuilder(this)
            .setTitle(
                R.string.exit_dialog_title
            )
            .setMessage(message)
            .setNegativeButton(
                R.string.exit_dialog_cancel,
                null
            )
            .setPositiveButton(
                R.string.exit_dialog_exit
            ) { _, _ ->
                finish()
            }
            .show()
    }

    private fun showNavigationDestination(
        itemId: Int
    ): Boolean {
        val fragment =
            when (itemId) {
                R.id.navigation_home ->
                    HomeFragment()

                R.id.navigation_profiles ->
                    ProfilesFragment()

                R.id.navigation_logs ->
                    LogsFragment()

                R.id.navigation_settings ->
                    SettingsFragment()

                else ->
                    return false
            }

        supportFragmentManager
            .beginTransaction()
            .replace(
                R.id.fragment_container,
                fragment
            )
            .commit()

        return true
    }


    companion object {
        @Volatile
        var isAliveInProcess: Boolean = false
            private set
    }
}
