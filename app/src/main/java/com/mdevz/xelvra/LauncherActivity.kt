package com.mdevz.xelvra

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.mdevz.xelvra.core.crash.CrashReportStore

class LauncherActivity : AppCompatActivity() {

    private val startedAt =
        SystemClock.elapsedRealtime()

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        if (
            !MainActivity.isAliveInProcess &&
            CrashReportStore.pending(this) != null
        ) {
            startActivity(
                Intent(
                    this,
                    CrashReportActivity::class.java
                )
            )
            finish()
            return
        }

        if (MainActivity.isAliveInProcess) {
            startActivity(
                Intent(
                    this,
                    MainActivity::class.java
                ).addFlags(
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
            )
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(
            R.layout.activity_launcher
        )

        val packageInfo =
            packageManager.getPackageInfo(
                packageName,
                0
            )

        val versionName =
            packageInfo.versionName
                ?: "0.0.0"

        val versionCode =
            packageInfo.versionCode.toLong()

        findViewById<TextView>(
            R.id.launcher_developer
        ).text =
            getString(
                R.string.launcher_version_format,
                developerName(),
                versionName,
                versionCode
            )

        val root =
            findViewById<android.view.View>(
                R.id.launcher_root
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

        root.post {
            openMainWhenReady(
                root
            )
        }
    }

    private fun developerName(): String {
        val key = 0x2D

        val encoded =
            intArrayOf(
                0x60,
                0x69,
                0x48,
                0x5B,
                0x57
            )

        return buildString(
            encoded.size
        ) {
            encoded.forEach { value ->
                append(
                    (value xor key)
                        .toChar()
                )
            }
        }
    }

    private fun openMainWhenReady(
        root: android.view.View
    ) {
        val elapsed =
            SystemClock.elapsedRealtime() -
                startedAt

        val remaining =
            (MINIMUM_STARTUP_MS - elapsed)
                .coerceAtLeast(0L)

        root.postDelayed(
            {
                if (
                    isFinishing ||
                    isDestroyed
                ) {
                    return@postDelayed
                }

                startActivity(
                    Intent(
                        this,
                        MainActivity::class.java
                    )
                )

                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    overrideActivityTransition(
                        android.app.Activity.OVERRIDE_TRANSITION_OPEN,
                        android.R.anim.fade_in,
                        android.R.anim.fade_out
                    )
                } else {
                    @Suppress("DEPRECATION")
                    overridePendingTransition(
                        android.R.anim.fade_in,
                        android.R.anim.fade_out
                    )
                }

                finish()
            },
            remaining
        )
    }

    companion object {
        private const val MINIMUM_STARTUP_MS =
            1_500L
    }
}
