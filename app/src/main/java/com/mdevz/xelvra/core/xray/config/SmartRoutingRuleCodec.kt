package com.mdevz.xelvra.core.xray.config

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Mengonversi aturan Smart Routing lokal menjadi aturan Xray.
 *
 * Digunakan saat membangun konfigurasi VPN aktif.
 */
internal object SmartRoutingRuleCodec {

    private const val STORAGE_NAME =
        "xelvra_smart_routing_drafts"

    private const val STORAGE_KEY = "rules_v1"

    private const val MAX_RULES = 100

    fun readAsXrayRules(context: Context): JSONArray {
        val raw = context.getSharedPreferences(
            STORAGE_NAME,
            Context.MODE_PRIVATE
        ).getString(STORAGE_KEY, "[]") ?: "[]"

        val stored = JSONArray(raw)

        require(stored.length() <= MAX_RULES) {
            "Too many Smart Routing rules"
        }

        val result = JSONArray()
        val seen = mutableSetOf<String>()

        for (index in 0 until stored.length()) {
            val item = stored.getJSONObject(index)

            val type = item.getInt("type")
            val value = item.getString("value").trim()
            val action = item.getInt("action")

            require(type in 0..1) {
                "Invalid Smart Routing rule type"
            }

            require(action in 0..2) {
                "Invalid Smart Routing rule action"
            }

            val valid = when (type) {
                0 -> SmartRoutingValidator.isValidDomain(value)
                1 -> SmartRoutingValidator.isValidIpOrCidr(value)
                else -> false
            }

            require(valid) {
                "Invalid Smart Routing rule value"
            }

            val key = "$type:${value.lowercase(java.util.Locale.ROOT)}"

            require(seen.add(key)) {
                "Duplicate Smart Routing rule"
            }

            val outboundTag = when (action) {
                0 -> "proxy"
                1 -> "direct"
                2 -> "block"
                else -> error("Invalid action")
            }

            val rule = JSONObject()
                .put("type", "field")
                .put("outboundTag", outboundTag)

            if (type == 0) {
                rule.put(
                    "domain",
                    JSONArray().put("domain:$value")
                )
            } else {
                rule.put(
                    "ip",
                    JSONArray().put(value)
                )
            }

            result.put(rule)
        }

        return result
    }

    

    
}
