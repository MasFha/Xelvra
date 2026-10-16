package com.mdevz.xelvra.core.crash

object CrashSanitizer {

    private const val REDACTED =
        "[REDACTED]"

    private val proxyUri =
        Regex(
            """(?i)\b(vless|vmess|trojan|ss)://[^\s]+"""
        )

    private val urlCredentials =
        Regex(
            """(?i)(https?://)[^@\s/:]+:[^@\s/]+@"""
        )

    private val bearerToken =
        Regex(
            """(?i)\bBearer\s+[A-Za-z0-9._~+/=-]+"""
        )

    private val uuid =
        Regex(
            """(?i)\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\b"""
        )

    private val sensitiveAssignment =
        Regex(
            """(?i)\b(password|passwd|token|secret|uuid|authorization)\s*[:=]\s*[^\s,;]+"""
        )

    fun sanitize(
        value: String?
    ): String {
        if (value.isNullOrBlank()) {
            return ""
        }

        return value
            .replace(
                proxyUri,
                REDACTED
            )
            .replace(
                urlCredentials,
                "$1$REDACTED@"
            )
            .replace(
                bearerToken,
                "Bearer $REDACTED"
            )
            .replace(
                uuid,
                REDACTED
            )
            .replace(
                sensitiveAssignment
            ) { match ->
                val key =
                    match.groupValues[1]

                "$key=$REDACTED"
            }
            .take(MAX_TEXT_LENGTH)
    }

    private const val MAX_TEXT_LENGTH =
        128_000
}
