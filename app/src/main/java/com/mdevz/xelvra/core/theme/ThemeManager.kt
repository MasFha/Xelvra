package com.mdevz.xelvra.core.theme

import androidx.appcompat.app.AppCompatDelegate
import com.mdevz.xelvra.data.local.AppTheme

object ThemeManager {

    fun apply(theme: AppTheme) {
        AppCompatDelegate.setDefaultNightMode(
            when (theme) {
                AppTheme.SYSTEM ->
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM

                AppTheme.LIGHT ->
                    AppCompatDelegate.MODE_NIGHT_NO

                AppTheme.DARK ->
                    AppCompatDelegate.MODE_NIGHT_YES
            }
        )
    }
}
