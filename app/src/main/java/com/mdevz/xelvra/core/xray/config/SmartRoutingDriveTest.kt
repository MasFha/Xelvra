package com.mdevz.xelvra.core.xray.config

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pengujian internal Smart Routing.
 *
 * Tidak mengubah SharedPreferences, VPN, atau konfigurasi Xray.
 */
internal object SmartRoutingDriveTest {

    data class Check(
        val name: String,
        val passed: Boolean,
        val detail: String
    )

    data class Report(
        val checks: List<Check>
    ) {
        val passed: Int
            get() = checks.count { it.passed }

        val failed: Int
            get() = checks.count { !it.passed }

        val success: Boolean
            get() = failed == 0

        fun toJson(): JSONObject {
            val entries = JSONArray()

            checks.forEach { check ->
                entries.put(
                    JSONObject()
                        .put("name", check.name)
                        .put("passed", check.passed)
                        .put("detail", check.detail)
                )
            }

            return JSONObject()
                .put("passed", passed)
                .put("failed", failed)
                .put("success", success)
                .put("checks", entries)
        }
    }

    fun run(): Report {
        val checks = mutableListOf<Check>()

        fun check(
            name: String,
            expected: Boolean,
            detail: String
        ) {
            checks += Check(name, expected, detail)
        }

        check(
            "Domain valid",
            SmartRoutingValidator.isValidDomain("example.com"),
            "example.com"
        )

        check(
            "Domain URL rejected",
            !SmartRoutingValidator.isValidDomain(
                "https://example.com"
            ),
            "URL tidak boleh menjadi domain rule"
        )

        check(
            "IPv4 valid",
            SmartRoutingValidator.isValidIpOrCidr("1.1.1.1"),
            "1.1.1.1"
        )

        check(
            "IPv4 invalid rejected",
            !SmartRoutingValidator.isValidIpOrCidr(
                "999.1.1.1"
            ),
            "999.1.1.1"
        )

        check(
            "IPv4 CIDR valid",
            SmartRoutingValidator.isValidIpOrCidr(
                "192.168.1.0/24"
            ),
            "192.168.1.0/24"
        )

        check(
            "CIDR invalid rejected",
            !SmartRoutingValidator.isValidIpOrCidr(
                "1.1.1.1/99"
            ),
            "1.1.1.1/99"
        )

        check(
            "IPv6 valid",
            SmartRoutingValidator.isValidIpOrCidr(
                "2001:db8::1"
            ),
            "2001:db8::1"
        )

        check(
            "IPv6 CIDR valid",
            SmartRoutingValidator.isValidIpOrCidr(
                "2001:db8::/32"
            ),
            "2001:db8::/32"
        )

        check(
            "IPv6 invalid rejected",
            !SmartRoutingValidator.isValidIpOrCidr(
                "2001:db8::zz"
            ),
            "2001:db8::zz"
        )

        return Report(checks)
    }
}
