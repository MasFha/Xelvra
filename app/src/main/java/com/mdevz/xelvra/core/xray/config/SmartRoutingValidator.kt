package com.mdevz.xelvra.core.xray.config

import java.net.Inet6Address
import java.net.InetAddress

/**
 * Validator bersama untuk editor dan codec Smart Routing.
 *
 * Tidak mengaktifkan aturan pada VPN.
 */
internal object SmartRoutingValidator {

    fun isValidDomain(value: String): Boolean {
        if (value.length !in 1..253) return false
        if (value.contains("://") || value.contains("/")) {
            return false
        }

        val labels = value.split(".")
        if (labels.size < 2) return false

        return labels.all { label ->
            label.length in 1..63 &&
                label.firstOrNull()?.isLetterOrDigit() == true &&
                label.lastOrNull()?.isLetterOrDigit() == true &&
                label.all {
                    it.isLetterOrDigit() || it == '-'
                }
        }
    }

    fun isValidIpOrCidr(value: String): Boolean {
        val parts = value.split("/")
        if (parts.size !in 1..2) return false

        val address = parts[0]
        if (address.isBlank()) return false

        val ipv4 = address.matches(
            Regex("""([0-9]{1,3}\.){3}[0-9]{1,3}""")
        ) && address.split(".").all {
            (it.toIntOrNull() ?: 256) in 0..255
        }

        val ipv6 = !ipv4 &&
            address.contains(":") &&
            address.matches(Regex("[0-9a-fA-F:.]+")) &&
            runCatching {
                InetAddress.getByName(address) is Inet6Address
            }.getOrDefault(false)

        if (!ipv4 && !ipv6) return false

        if (parts.size == 1) return true

        val prefix = parts[1].toIntOrNull() ?: return false
        return prefix in 0..(if (ipv4) 32 else 128)
    }
}
