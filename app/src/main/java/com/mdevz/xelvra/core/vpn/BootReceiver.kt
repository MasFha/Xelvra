package com.mdevz.xelvra.core.vpn

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.mdevz.xelvra.core.log.AppLog
import com.mdevz.xelvra.data.local.ActiveProfileStore
import com.mdevz.xelvra.data.local.ProfileLocalStorage
import com.mdevz.xelvra.data.local.SettingsStore
import com.mdevz.xelvra.data.repository.ProfileRepository

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        if (
            intent.action !=
            Intent.ACTION_BOOT_COMPLETED
        ) {
            return
        }

        val appContext =
            context.applicationContext

        val settingsStore =
            SettingsStore(appContext)

        if (!settingsStore.isAutoConnectEnabled()) {
            return
        }

        val activeProfileStore =
            ActiveProfileStore(appContext)

        val activeProfileId =
            activeProfileStore.getActiveProfileId()

        if (activeProfileId == null) {
            AppLog.vpn(
                "Auto Connect: skipped after boot " +
                    "(no active profile)"
            )
            return
        }

        val repository =
            ProfileRepository(
                ProfileLocalStorage(
                    appContext
                )
            )

        if (
            repository.findProfile(
                activeProfileId
            ) == null
        ) {
            activeProfileStore.clearActiveProfile()

            AppLog.vpn(
                "Auto Connect: skipped after boot " +
                    "(active profile not found)"
            )
            return
        }

        if (
            Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            AppLog.vpn(
                "Auto Connect: skipped after boot " +
                    "(notification permission required)"
            )
            return
        }

        if (
            VpnService.prepare(
                appContext
            ) != null
        ) {
            AppLog.vpn(
                "Auto Connect: skipped after boot " +
                    "(VPN permission required)"
            )
            return
        }

        val serviceIntent =
            Intent(
                appContext,
                XelvraVpnService::class.java
            ).apply {
                action =
                    XelvraVpnService.ACTION_START
            }

        try {
            AppLog.vpn(
                "Auto Connect: starting VPN after boot"
            )

            ContextCompat.startForegroundService(
                appContext,
                serviceIntent
            )
        } catch (error: Throwable) {
            AppLog.vpn(
                message =
                    "Auto Connect failed after boot (" +
                    error.javaClass.simpleName +
                    ")",
                level = AppLog.Level.WARNING
            )
        }
    }
}
