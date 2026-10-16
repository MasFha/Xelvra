package com.mdevz.xelvra.ui.settings.routing
import com.mdevz.xelvra.core.xray.config.SmartRoutingValidator

import android.os.Bundle
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import android.text.InputType
import android.text.TextUtils
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.Toast
import java.net.InetAddress
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mdevz.xelvra.R
import com.mdevz.xelvra.core.xray.config.SmartRoutingDriveTest

class SmartRoutingActivity : AppCompatActivity() {

    private data class RoutingRule(
        val type: Int,
        val value: String,
        val action: Int
    )

    private val storageName = "xelvra_smart_routing_drafts"
    private val storageKey = "rules_v1"
    private val maxRules = 100
    private lateinit var rulesContainer: LinearLayout

    private fun readRules(): List<RoutingRule> {
        val raw = getSharedPreferences(
            storageName, Context.MODE_PRIVATE
        ).getString(storageKey, "[]") ?: "[]"

        return runCatching {
            val array = JSONArray(raw)
            val result = mutableListOf<RoutingRule>()
            for (i in 0 until minOf(array.length(), maxRules)) {
                val obj = array.getJSONObject(i)
                val type = obj.getInt("type")
                val value = obj.getString("value")
                val action = obj.getInt("action")
                if (type in 0..1 && action in 0..2 &&
                    value.isNotBlank()
                ) {
                    result.add(RoutingRule(type, value, action))
                }
            }
            result
        }.getOrElse {
            Toast.makeText(
                this, R.string.smart_rule_storage_error,
                Toast.LENGTH_LONG
            ).show()
            emptyList()
        }
    }

    private fun writeRules(rules: List<RoutingRule>): Boolean {
        val array = JSONArray()
        rules.forEach { rule ->
            array.put(JSONObject().apply {
                put("type", rule.type)
                put("value", rule.value)
                put("action", rule.action)
            })
        }
        return getSharedPreferences(
            storageName, Context.MODE_PRIVATE
        ).edit().putString(storageKey, array.toString()).commit()
    }

    private fun addRule(rule: RoutingRule) {
        val rules = readRules()

        if (rules.size >= maxRules) {
            Toast.makeText(
                this, R.string.smart_rule_limit,
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (rules.any {
            it.type == rule.type &&
                it.value.equals(rule.value, ignoreCase = true)
        }) {
            Toast.makeText(
                this, R.string.smart_rule_duplicate,
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (!writeRules(rules + rule)) {
            Toast.makeText(
                this, R.string.smart_rule_storage_error,
                Toast.LENGTH_LONG
            ).show()
            return
        }

        renderRules()
        Toast.makeText(
            this, R.string.smart_rule_saved,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun deleteRule(index: Int) {
        val rules = readRules()
        if (index !in rules.indices) return

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.smart_rule_delete_title)
            .setMessage(
                getString(
                    R.string.smart_rule_delete_message,
                    rules[index].value
                )
            )
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.smart_rule_delete) { _, _ ->
                val current = readRules()
                if (index !in current.indices ||
                    current[index] != rules[index]
                ) return@setPositiveButton

                val updated = current.toMutableList().apply {
                    removeAt(index)
                }

                if (writeRules(updated)) {
                    renderRules()
                } else {
                    Toast.makeText(
                        this, R.string.smart_rule_storage_error,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .show()
    }


    private fun renderRules() {
        if (!::rulesContainer.isInitialized) return
        rulesContainer.removeAllViews()

        val rules = readRules()

        if (rules.isEmpty()) {
            rulesContainer.addView(TextView(this).apply {
                text = getString(R.string.smart_v3_empty)
                textSize = 14f
                setPadding(dp(8), dp(16), dp(8), dp(16))
            })
            return
        }

        val onSurface = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOnSurface,
            android.graphics.Color.WHITE
        )
        val secondary = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            android.graphics.Color.GRAY
        )
        val outline = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOutlineVariant,
            android.graphics.Color.GRAY
        )

        val locale = resources.configuration.locales[0].language
        val isIndonesian = locale == "id" || locale == "in"

        rules.forEachIndexed { index, rule ->
            val isDomain = rule.type == 0

            val actionName = when (rule.action) {
                0 -> getString(R.string.smart_rule_proxy)
                1 -> getString(R.string.smart_rule_direct)
                else -> getString(R.string.smart_rule_block)
            }

            val actionColor = when (rule.action) {
                0 -> android.graphics.Color.rgb(112, 85, 220)
                1 -> android.graphics.Color.rgb(166, 119, 28)
                else -> android.graphics.Color.rgb(185, 54, 72)
            }

            val card = MaterialCardView(this).apply {
                radius = dp(16).toFloat()
                cardElevation = 0f
                strokeWidth = dp(1)
                strokeColor = outline
                layoutParams = LinearLayout.LayoutParams(
                    -1, -2
                ).apply {
                    topMargin = dp(10)
                }
            }

            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(12), dp(8), dp(6))
            }

            row.addView(android.widget.ImageView(this).apply {
                setImageResource(
                    if (isDomain) R.drawable.ic_routing
                    else R.drawable.ic_health_network
                )
                imageTintList =
                    android.content.res.ColorStateList.valueOf(onSurface)
                contentDescription =
                    if (isDomain) "Domain" else "IP/CIDR"
                layoutParams = LinearLayout.LayoutParams(
                    dp(26), dp(26)
                ).apply {
                    marginEnd = dp(12)
                }
            })

            val details = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, -2, 1f
                )
            }

            details.addView(TextView(this).apply {
                text = rule.value
                textSize = 15f
                setTextColor(onSurface)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })

            details.addView(TextView(this).apply {
                text = if (isDomain) {
                    getString(R.string.smart_rule_domain)
                } else {
                    getString(R.string.smart_rule_ip)
                }
                textSize = 12f
                setTextColor(secondary)
                setPadding(0, dp(3), 0, 0)
            })

            row.addView(details)

            row.addView(TextView(this).apply {
                text = actionName
                textSize = 12f
                setTextColor(android.graphics.Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(6), dp(10), dp(6))
                background =
                    android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = dp(18).toFloat()
                        setColor(actionColor)
                    }
            })

            row.addView(android.widget.ImageButton(this).apply {
                setImageResource(R.drawable.ic_delete)
                imageTintList =
                    android.content.res.ColorStateList.valueOf(secondary)
                background = null
                contentDescription =
                    getString(R.string.smart_rule_delete)
                setOnClickListener {
                    deleteRule(index)
                }
                layoutParams = LinearLayout.LayoutParams(
                    dp(44), dp(44)
                ).apply {
                    marginStart = dp(4)
                }
            })

            content.addView(row)

            content.addView(TextView(this).apply {
                text = getString(
                    R.string.smart_v4_route_notice
                )
                textSize = 11f
                setTextColor(secondary)
                setPadding(dp(52), 0, dp(12), dp(12))
            })

            card.addView(content)
            rulesContainer.addView(card)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorSurface,
                    0
                )
            )
        }

        // Respect Android status bar in edge-to-edge mode.
        val originalLeft = root.paddingLeft
        val originalTop = root.paddingTop
        val originalRight = root.paddingRight
        val originalBottom = root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.statusBars()
            )
            view.setPadding(
                originalLeft,
                originalTop + bars.top,
                originalRight,
                originalBottom
            )
            insets
        }

        val toolbar = MaterialToolbar(this).apply {
            title = getString(R.string.smart_routing_title)
                        val toolbarIconColor = MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorOnSurface,
                android.graphics.Color.WHITE
            )
            val toolbarCircleColor = MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorSurfaceVariant,
                android.graphics.Color.GRAY
            )

            fun circularBackground(): android.graphics.drawable.Drawable {
                return android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(toolbarCircleColor)
                }
            }

            setNavigationIcon(R.drawable.ic_smart_arrow_back)
            navigationIcon?.setTint(toolbarIconColor)

            post {
                val navButton = (0 until childCount)
                    .map { getChildAt(it) }
                    .firstOrNull {
                        it is android.widget.ImageButton
                    } as? android.widget.ImageButton

                navButton?.apply {
                    background = circularBackground()
                    scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    layoutParams = (layoutParams as
                        androidx.appcompat.widget.Toolbar.LayoutParams).apply {
                        width = dp(48)
                        height = dp(48)
                        marginStart = dp(12)
                        marginEnd = dp(8)
                    }
                }
            }
            setNavigationOnClickListener { finish() }

            menu.add(getString(R.string.smart_routing_help)).apply {
                setIcon(R.drawable.ic_smart_help_circle)
                icon?.setTint(toolbarIconColor)
                setShowAsAction(
                    android.view.MenuItem.SHOW_AS_ACTION_ALWAYS
                )
                setOnMenuItemClickListener {
                    showGuide()
                    true
                }
            }
        }

        root.addView(
            toolbar,
            LinearLayout.LayoutParams(-1, dp(56))
        )

        toolbar.post {
            val menuView = (0 until toolbar.childCount)
                .map { toolbar.getChildAt(it) }
                .firstOrNull {
                    it is androidx.appcompat.widget.ActionMenuView
                } as? androidx.appcompat.widget.ActionMenuView

            if (menuView != null) {
                for (i in 0 until menuView.childCount) {
                    val button = menuView.getChildAt(i)
                    button.background =
                        android.graphics.drawable.GradientDrawable().apply {
                            shape =
                                android.graphics.drawable.GradientDrawable.OVAL
                            setColor(
                                MaterialColors.getColor(
                                    this@SmartRoutingActivity,
                                    com.google.android.material.R.attr.colorSurfaceVariant,
                                    android.graphics.Color.GRAY
                                )
                            )
                        }
                    button.setPadding(
                        dp(12), dp(12), dp(12), dp(12)
                    )
                    val lp = button.layoutParams
                    lp.width = dp(48)
                    lp.height = dp(48)
                    button.layoutParams = lp
                }
                menuView.setPadding(0, 0, dp(12), 0)
            }
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(24))
        }

        fun makeText(
            value: String,
            size: Float,
            attribute: Int,
            top: Int = 0
        ): TextView {
            return TextView(this).apply {
                text = value
                textSize = size
                setTextColor(
                    MaterialColors.getColor(this, attribute, 0)
                )
                layoutParams = LinearLayout.LayoutParams(
                    -1, -2
                ).apply {
                    topMargin = dp(top)
                }
            }
        }

        val primary =
            com.google.android.material.R.attr.colorOnSurface
        val secondary =
            com.google.android.material.R.attr.colorOnSurfaceVariant

        body.addView(
            makeText(
                getString(R.string.smart_routing_intro),
                14f, secondary
            )
        )


        val savedRules = readRules()
        val blockCount = savedRules.count { it.action == 2 }
        val otherCount = savedRules.size - blockCount

        val statusCard = MaterialCardView(this).apply {
            radius = dp(20).toFloat()
            strokeWidth = dp(1)
            strokeColor = MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorOutlineVariant,
                android.graphics.Color.GRAY
            )
            setCardBackgroundColor(
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorSurfaceVariant,
                    android.graphics.Color.GRAY
                )
            )
            layoutParams = LinearLayout.LayoutParams(
                -1, -2
            ).apply {
                topMargin = dp(18)
            }
        }

        val statusContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }

        statusContent.addView(
            makeText(
                getString(R.string.smart_v3_status_title),
                18f, primary
            ).apply {
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
        )

        statusContent.addView(
            makeText(
                getString(R.string.smart_v3_status_desc),
                13f, secondary, 8
            )
        )

        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                -1, -2
            ).apply {
                topMargin = dp(18)
            }
        }

        fun addStat(number: Int, label: String) {
            val stat = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(12), dp(4), dp(12))
                layoutParams = LinearLayout.LayoutParams(
                    0, -2, 1f
                ).apply {
                    marginEnd = dp(6)
                }
                background =
                    android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        setColor(
                            MaterialColors.getColor(
                                this@SmartRoutingActivity,
                                com.google.android.material.R.attr.colorSurface,
                                android.graphics.Color.WHITE
                            )
                        )
                    }
            }

            stat.addView(TextView(this).apply {
                text = number.toString()
                textSize = 23f
                gravity = Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(
                    MaterialColors.getColor(
                        this,
                        com.google.android.material.R.attr.colorOnSurface,
                        android.graphics.Color.BLACK
                    )
                )
            })

            stat.addView(TextView(this).apply {
                text = label
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(
                    MaterialColors.getColor(
                        this,
                        com.google.android.material.R.attr.colorOnSurfaceVariant,
                        android.graphics.Color.GRAY
                    )
                )
            })

            statsRow.addView(stat)
        }

        addStat(
            savedRules.size,
            getString(R.string.smart_v3_total)
        )
        addStat(
            blockCount,
            getString(R.string.smart_rule_block)
        )
        addStat(
            otherCount,
            getString(R.string.smart_v3_pending)
        )

        statusContent.addView(statsRow)
        statusCard.addView(statusContent)
        body.addView(statusCard)

        body.addView(
            makeText(
                getString(R.string.smart_routing_rules),
                20f, primary, 28
            )
        )
        rulesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                -1, -2
            ).apply {
                topMargin = dp(12)
            }
        }
        body.addView(rulesContainer)
        renderRules()

        val addButton = MaterialButton(this).apply {
            text = getString(R.string.smart_routing_add)
            setIconResource(R.drawable.ic_add)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconPadding = dp(8)
            isAllCaps = false
            setOnClickListener {
                showAddRuleDialog()
            }
            layoutParams = LinearLayout.LayoutParams(
                -1, dp(52)
            ).apply {
                topMargin = dp(24)
            }
        }

        body.addView(addButton)

        val driveTestButton = MaterialButton(this).apply {
            text = getString(R.string.smart_v3_drive_test)
            setIconResource(R.drawable.ic_health_shield)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconPadding = dp(8)
            setTextColor(
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    android.graphics.Color.GRAY
                )
            )
            backgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    MaterialColors.getColor(
                        this,
                        com.google.android.material.R.attr.colorSurfaceVariant,
                        android.graphics.Color.GRAY
                    )
                )
            isAllCaps = false

            setOnClickListener {
                showDriveTestResults()
            }

            layoutParams = LinearLayout.LayoutParams(
                -1, dp(52)
            ).apply {
                topMargin = dp(12)
            }
        }

        body.addView(driveTestButton)

        scroll.addView(body)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        setContentView(root)

        // XELVRA_DRIVE_TEST_AUTOMATION_V1
        // Debug-only internal test. Does not modify VPN state.
        if (applicationInfo.flags and
            android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            intent?.getBooleanExtra(
                "xelvra_run_drive_test", false
            ) == true
        ) {
            val report = runCatching {
                SmartRoutingDriveTest.run()
            }

            report.onSuccess { result ->
                android.util.Log.i(
                    "XelvraDriveTest",
                    "BEGIN total=${result.checks.size}"
                )

                result.checks.forEachIndexed { index, check ->
                    android.util.Log.i(
                        "XelvraDriveTest",
                        "CHECK ${index + 1} " +
                            "${if (check.passed) "PASS" else "FAIL"} " +
                            check.name
                    )
                }

                android.util.Log.i(
                    "XelvraDriveTest",
                    "RESULT passed=${result.passed} " +
                        "failed=${result.failed}"
                )
            }

            report.onFailure { error ->
                android.util.Log.e(
                    "XelvraDriveTest",
                    "RESULT ERROR",
                    error
                )
            }
        }
    }


    private fun showDriveTestResults() {
        val report = runCatching {
            SmartRoutingDriveTest.run()
        }.getOrElse { error ->
            MaterialAlertDialogBuilder(this)
                .setTitle("Drive Test Failed")
                .setMessage(
                    error.message ?: error.javaClass.simpleName
                )
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        val output = StringBuilder()

        output.append(
            "Passed: ${report.passed} / ${report.checks.size}"
        )
        output.append("\n")
        output.append("Failed: ${report.failed}")
        output.append("\n\n")

        report.checks.forEachIndexed { index, check ->
            val status = if (check.passed) "PASS" else "FAIL"

            output.append(
                "${index + 1}. [$status] ${check.name}"
            )
            output.append("\n")
            output.append(check.detail)
            output.append("\n\n")
        }

        output.append(
            "Validator test only. VPN routing was not changed."
        )

        val resultView = TextView(this).apply {
            text = output.toString()
            textSize = 14f
            setTextIsSelectable(true)
            setPadding(
                dp(20), dp(12), dp(20), dp(12)
            )
        }

        val scrollView = ScrollView(this).apply {
            addView(resultView)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(
                if (report.success) {
                    "Drive Test: PASS"
                } else {
                    "Drive Test: FAIL"
                }
            )
            .setView(scrollView)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }


    private fun showAddRuleDialog() {
        val context = this
        val onSurface = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOnSurface,
            android.graphics.Color.WHITE
        )
        val secondary = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            android.graphics.Color.GRAY
        )
        val surface = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSurfaceVariant,
            android.graphics.Color.DKGRAY
        )
        val outline = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOutlineVariant,
            android.graphics.Color.GRAY
        )

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(12))
        }

        fun heading(value: String, top: Int): TextView =
            TextView(context).apply {
                text = value
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(onSurface)
                layoutParams = LinearLayout.LayoutParams(
                    -1, -2
                ).apply { topMargin = dp(top) }
            }

        container.addView(
            TextView(context).apply {
                text = getString(R.string.smart_v4_intro)
                textSize = 13f
                setTextColor(secondary)
            }
        )

        container.addView(
            heading(getString(R.string.smart_v4_destinations), 18)
        )

        val input = android.widget.EditText(context).apply {
            hint = getString(R.string.smart_v4_hint)
            textSize = 14f
            minLines = 5
            maxLines = 9
            gravity = Gravity.TOP or Gravity.START
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine(false)
            setTextColor(onSurface)
            setHintTextColor(secondary)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background =
                android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(surface)
                    setStroke(dp(1), outline)
                }
            layoutParams = LinearLayout.LayoutParams(
                -1, -2
            ).apply { topMargin = dp(10) }
        }
        container.addView(input)

        container.addView(
            TextView(context).apply {
                text = getString(R.string.smart_v4_separator_hint)
                textSize = 12f
                setTextColor(secondary)
                layoutParams = LinearLayout.LayoutParams(
                    -1, -2
                ).apply { topMargin = dp(8) }
            }
        )

        container.addView(
            heading(getString(R.string.smart_rule_action), 20)
        )

        val routeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                -1, -2
            ).apply { topMargin = dp(10) }
        }

        val routeLabels = listOf(
            getString(R.string.smart_rule_proxy),
            getString(R.string.smart_rule_direct),
            getString(R.string.smart_rule_block)
        )
        val routeIcons = listOf(
            R.drawable.ic_vpn,
            R.drawable.ic_routing,
            R.drawable.ic_health_shield
        )
        val routeViews = mutableListOf<MaterialCardView>()
        var selectedAction = 2

        fun updateRouteSelection() {
            routeViews.forEachIndexed { index, card ->
                val selected = index == selectedAction
                card.strokeWidth = dp(if (selected) 2 else 1)
                card.strokeColor = if (selected) onSurface else outline
                card.setCardBackgroundColor(
                    if (selected) surface else
                        MaterialColors.getColor(
                            this,
                            com.google.android.material.R.attr.colorSurface,
                            android.graphics.Color.BLACK
                        )
                )
            }
        }

        routeLabels.forEachIndexed { index, label ->
            val card = MaterialCardView(context).apply {
                radius = dp(14).toFloat()
                cardElevation = 0f
                isClickable = true
                isFocusable = true
                contentDescription = label
                layoutParams = LinearLayout.LayoutParams(
                    0, dp(100), 1f
                ).apply {
                    if (index > 0) marginStart = dp(8)
                }
            }

            val inner = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(8), dp(4), dp(8))
            }

            inner.addView(android.widget.ImageView(context).apply {
                setImageResource(routeIcons[index])
                imageTintList =
                    android.content.res.ColorStateList.valueOf(onSurface)
                layoutParams = LinearLayout.LayoutParams(
                    dp(24), dp(24)
                )
            })

            inner.addView(TextView(context).apply {
                text = label
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(onSurface)
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    -1, -2
                ).apply { topMargin = dp(7) }
            })

            inner.addView(TextView(context).apply {
                text = getString(
                    R.string.smart_v4_available
                )
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(secondary)
            })

            card.addView(inner)
            card.setOnClickListener {
                selectedAction = index
                updateRouteSelection()
            }
            routeViews.add(card)
            routeRow.addView(card)
        }

        container.addView(routeRow)
        updateRouteSelection()

        val routeNotice = TextView(context).apply {
            text = getString(R.string.smart_v4_route_notice)
            textSize = 12f
            setTextColor(secondary)
            layoutParams = LinearLayout.LayoutParams(
                -1, -2
            ).apply { topMargin = dp(12) }
        }
        container.addView(routeNotice)

        container.addView(
            heading(getString(R.string.smart_v4_preview), 20)
        )

        val preview = TextView(context).apply {
            textSize = 13f
            setTextColor(onSurface)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background =
                android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(surface)
                }
            layoutParams = LinearLayout.LayoutParams(
                -1, -2
            ).apply { topMargin = dp(10) }
        }
        container.addView(preview)

        data class BatchResult(
            val rules: List<RoutingRule>,
            val duplicates: Int,
            val invalid: List<String>,
            val total: Int
        )

        fun inspectBatch(): BatchResult {
            val tokens = input.text.toString()
                .split(Regex("[\\n\\r,;]+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val existing = readRules()
            val seen = existing.map {
                "${it.type}:${it.value.lowercase(java.util.Locale.ROOT)}"
            }.toMutableSet()

            val accepted = mutableListOf<RoutingRule>()
            val invalid = mutableListOf<String>()
            var duplicates = 0

            tokens.forEach { value ->
                val type = when {
                    SmartRoutingValidator.isValidIpOrCidr(value) -> 1
                    SmartRoutingValidator.isValidDomain(value) -> 0
                    else -> -1
                }

                if (type == -1) {
                    invalid.add(value)
                } else {
                    val key = "$type:${value.lowercase(java.util.Locale.ROOT)}"
                    if (!seen.add(key)) {
                        duplicates++
                    } else {
                        accepted.add(
                            RoutingRule(type, value, selectedAction)
                        )
                    }
                }
            }

            return BatchResult(
                accepted, duplicates, invalid, tokens.size
            )
        }

        fun updatePreview() {
            val batch = inspectBatch()
            val remaining = maxRules - readRules().size
            preview.text = getString(
                R.string.smart_v4_preview_format,
                batch.rules.size,
                batch.duplicates,
                batch.invalid.size,
                remaining.coerceAtLeast(0)
            )
        }

        input.addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int,
                    count: Int, after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?, start: Int,
                    before: Int, count: Int
                ) {
                    updatePreview()
                }

                override fun afterTextChanged(
                    s: android.text.Editable?
                ) = Unit
            }
        )
        updatePreview()

        val scroll = ScrollView(context).apply {
            isFillViewport = false
            addView(container)
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.smart_routing_add)
            .setView(scroll)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.smart_rule_review, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(
                android.content.DialogInterface.BUTTON_POSITIVE
            ).setOnClickListener {
                val batch = inspectBatch()
                val current = readRules()

                val problem = when {
                    batch.total == 0 ->
                        getString(R.string.smart_v4_empty_input)
                    batch.invalid.isNotEmpty() ->
                        getString(
                            R.string.smart_v4_invalid_batch,
                            batch.invalid.take(3).joinToString(", ")
                        )
                    batch.duplicates > 0 ->
                        getString(R.string.smart_v4_duplicate_batch)
                    current.size + batch.rules.size > maxRules ->
                        getString(R.string.smart_rule_limit)
                    else -> null
                }

                if (problem != null) {
                    MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.smart_v4_cannot_save)
                        .setMessage(problem)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    return@setOnClickListener
                }

                val reviewMessage = getString(
                    R.string.smart_v4_review_message,
                    batch.rules.size,
                    routeLabels[selectedAction],
                    batch.rules.take(8).joinToString("\n") {
                        "• ${it.value}"
                    } + if (batch.rules.size > 8) "\n…" else ""
                )

                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.smart_rule_review)
                    .setMessage(reviewMessage)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.smart_rule_save) { _, _ ->
                        val latest = readRules()
                        val latestKeys = latest.map {
                            "${it.type}:${it.value.lowercase(java.util.Locale.ROOT)}"
                        }.toSet()
                        val batchKeys = batch.rules.map {
                            "${it.type}:${it.value.lowercase(java.util.Locale.ROOT)}"
                        }

                        if (latest.size + batch.rules.size > maxRules ||
                            batchKeys.any { it in latestKeys }
                        ) {
                            Toast.makeText(
                                context,
                                R.string.smart_v4_changed,
                                Toast.LENGTH_LONG
                            ).show()
                        } else if (writeRules(latest + batch.rules)) {
                            renderRules()
                            Toast.makeText(
                                context,
                                R.string.smart_rule_saved,
                                Toast.LENGTH_SHORT
                            ).show()
                            dialog.dismiss()
                        } else {
                            Toast.makeText(
                                context,
                                R.string.smart_rule_storage_error,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    .show()
            }
        }

        dialog.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams
                .SOFT_INPUT_ADJUST_RESIZE
        )
        dialog.show()
    }


    private fun showGuide() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.smart_routing_help)
            .setMessage(R.string.smart_routing_help_text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
