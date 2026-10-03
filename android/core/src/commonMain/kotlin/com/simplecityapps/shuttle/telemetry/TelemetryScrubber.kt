package com.simplecityapps.shuttle.telemetry

/**
 * Takes what could name a user's server, network or files out of text bound for crash reports: the messages of Sentry
 * events and breadcrumbs, on Android (`SentryBreadcrumbTree`) and iOS (`beforeSend`, `beforeBreadcrumb`). Each match
 * becomes a placeholder saying what it was, so a report still reads.
 *
 * - URLs of any scheme (`https://`, `file://`, `content://`), whole
 * - credentials outside a URL (`user=`, `token=`, `api_key=`, `X-Plex-Token=` and the like)
 * - email addresses, IPv4 and IPv6 addresses
 * - host names (`music.example.com`, `nas.local`), leaving code names (`kotlin.`, `com.simplecityapps.`) be
 * - absolute file paths (`/var/mobile/...`, `/storage/emulated/0/...`)
 */
object TelemetryScrubber {
    fun scrub(text: String): String {
        if (text.isEmpty()) return text
        // Whole URLs first, query and all, then any credential left in plain text
        var result = URL.replace(text, "<url>")
        result = CREDENTIAL.replace(result) { "${it.groupValues[1]}=<redacted>" }
        result = EMAIL.replace(result, "<email>")
        result = IPV4.replace(result, "<ip>")
        result = IPV6.replace(result, "<ip>")
        // Paths before host names, so a file name (song.flac) goes with its folder rather than as a host
        result = PATH.replace(result, "<path>")
        result = HOST.replace(result) { match -> if (match.value.isCodeName()) match.value else "<host>" }
        return result
    }

    private val CREDENTIAL = Regex(
        """(?i)\b(user(?:name|_?id)?|token|access_?token|api_?key|x-(?:plex|emby|mediabrowser)-token|password|pw|auth(?:orization)?)=([^&\s"',;()\[\]{}<>]+)"""
    )
    private val URL = Regex("""\b[a-zA-Z][a-zA-Z0-9+.-]*://[^\s"'<>]+""")
    private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val IPV4 = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}(?::\d{1,5})?\b""")

    // A "::" or three colons at least, so a time (12:30:45) stays
    private val IPV6 = Regex("""(?i)(?<![\w:.])(?=[0-9a-f:]*::|(?:[0-9a-f]{1,4}:){3})(?:[0-9a-f]{0,4}:){2,7}[0-9a-f]{0,4}(?![\w:])""")

    // Lower-case labels ending in a letters-only one: a class name (IllegalStateException) has capitals
    private val HOST = Regex("""\b(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,24}(?::\d{1,5})?\b""")
    private val PATH = Regex("""(?<![\w.<>])(?:/[^\s/"'<>:]+){2,}/?""")

    private val CODE_PREFIXES = listOf(
        "kotlin.", "kotlinx.", "java.", "javax.", "android.", "androidx.", "dalvik.", "com.simplecityapps.", "io.ktor.",
        "okhttp3.", "okio.", "io.sentry.", "com.posthog.", "com.google.", "platform.", "sun.",
    )

    private fun String.isCodeName() = CODE_PREFIXES.any { startsWith(it) }
}
