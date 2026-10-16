package com.mdevz.xelvra

import android.app.Application
import com.google.android.material.color.DynamicColors
import com.mdevz.xelvra.core.crash.GlobalCrashHandler
import com.mdevz.xelvra.core.theme.ThemeManager
import com.mdevz.xelvra.data.local.SettingsStore

class XelvraApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        GlobalCrashHandler.install(this)

        ThemeManager.apply(
            SettingsStore(this).getTheme()
        )

        DynamicColors.applyToActivitiesIfAvailable(
            this
        )
    }
}
