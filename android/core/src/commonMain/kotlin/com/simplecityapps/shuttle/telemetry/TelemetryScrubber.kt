package com.simplecityapps.shuttle.telemetry

/**
 * Takes what could name a user's server, network or files out of text bound for crash reports: the messages of Sentry
 * events and breadcrumbs, on Android (`SentryBreadcrumbTree`) and iOS (`beforeSend`, `beforeBreadcrumb`). Each match
 * becomes a placeholder saying what it was, so a report still reads.
 *
 * - URLs of any scheme (`https://`, `file://`, `content://`), whole
 * - credentials outside a URL (`user=`, `token=`, `api_key=`, `X-Plex-Token=` and the like, and Subsonic's `u`, `t`,
 *   `s` and `p` query parameters)
 * - email addresses, IPv4 and IPv6 addresses
 * - host names of any case (`music.example.com`, `Tims-NAS.local`), leaving code names (`kotlin.`, `com.simplecityapps.`)
 *   and source files (`Queue.kt`) be; a single-label one (`homeserver`) where the text marks it as a host: after
 *   `connect to`, `resolve`, `host:` or `//`, or before `/<ip>` or a port
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
        // A bare `//homeserver:8096` before paths, which would take all but its last letter on Kotlin/Native; with a
        // path after it (`//homeserver/music`), the path takes it whole
        result = HOST_AFTER_SLASHES.replace(result) { match ->
            if (match.groupValues[1].isOrdinaryWord()) match.value else "//<host>"
        }
        // Paths before host names, so a file name (song.flac) goes with its folder rather than as a host
        result = PATH.replace(result, "<path>")
        result = HOST.replace(result) { match -> if (match.value.isCodeName()) match.value else "<host>" }
        result = HOST_AFTER_KEYWORD.replace(result) { match ->
            if (match.groupValues[2].isOrdinaryWord()) match.value else "${match.groupValues[1]}<host>"
        }
        result = HOST_BEFORE_ADDRESS.replace(result) { match ->
            if (match.groupValues[1].isOrdinaryWord()) match.value else "<host>"
        }
        return result
    }

    private val CREDENTIAL = Regex(
        """(?i)\b(user(?:name|_?id)?|token|access_?token|api_?key|x-(?:plex|emby|mediabrowser)-token|password|pw|auth(?:orization)?|(?<=[?&])[utsp])=([^&\s"',;()\[\]{}<>]+)"""
    )
    private val URL = Regex("""\b[a-zA-Z][a-zA-Z0-9+.-]*://[^\s"'<>]+""")
    private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val IPV4 = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}(?::\d{1,5})?\b""")

    // A "::" or three colons at least, so a time (12:30:45) stays
    private val IPV6 = Regex("""(?i)(?<![\w:.])(?=[0-9a-f:]*::|(?:[0-9a-f]{1,4}:){3})(?:[0-9a-f]{0,4}:){2,7}[0-9a-f]{0,4}(?![\w:])""")

    // Labels of any case ending in a letters-only one written in one case (com, LOCAL): a member (Fragment.onCreate) is
    // camel case. Spelled out rather than (?i), which would let the last label be camel case too
    private val HOST = Regex(
        """\b(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+(?:[a-z]{2,24}|[A-Z]{2,24})(?::\d{1,5})?\b"""
    )

    // One label, port and all, where only the context says it's a host. Runs after HOST, so a dotted name is already
    // gone, and a label followed by a dot and more (`homeserver.lan`) is never cut short
    private const val LABEL = """[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"""
    private const val LABEL_END = """(?::\d{1,5})?(?![\w-]|\.\w)"""

    // `connect to homeserver`, `resolve host "homeserver"`, `host: homeserver`, `hostname=homeserver`
    private val HOST_AFTER_KEYWORD = Regex(
        """(?i)\b((?:connect(?:ed|ing)?\s+to|resolve)(?:\s+host(?:name)?)?\s+["']?|host(?:name)?\s*(?:[:=]\s*["']?|["']))($LABEL)$LABEL_END"""
    )

    // `//homeserver:8096`: what's left of a URL without a scheme
    private val HOST_AFTER_SLASHES = Regex("""(?<![\w:/])//($LABEL)(?::\d{1,5})?(?![\w/-]|\.\w)""")

    // `homeserver/<ip>` (Java's InetSocketAddress) or `homeserver:8096`; a letter first, so a time (12:30) stays, and
    // never after a dot, so a source location (Queue.kt:42) does too
    private val HOST_BEFORE_ADDRESS = Regex(
        """(?<![\w.<>/:-])([A-Za-z](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)(?:(?=/<ip>)|:\d{2,5}(?![\d:]))"""
    )
    private val PATH = Regex("""(?<![\w.<>])(?:/[^\s/"'<>:]+){2,}/?""")

    private val CODE_PREFIXES = listOf(
        "kotlin.", "kotlinx.", "java.", "javax.", "android.", "androidx.", "dalvik.", "com.simplecityapps.", "io.ktor.",
        "okhttp3.", "okio.", "io.sentry.", "com.posthog.", "com.google.", "platform.", "sun.",
    )

    private val SOURCE_EXTENSIONS = listOf(".kt", ".kts", ".java", ".swift")

    // Words that follow `connect to` or precede a colon in ordinary prose, and name no one's machine
    private val ORDINARY_WORDS = setOf(
        "a", "an", "the", "any", "server", "host", "hostname", "remote", "network", "internet", "proxy", "service",
        "localhost", "line", "code", "status", "error", "port", "attempt", "http", "https",
    )

    // Case-sensitive on purpose: packages are lower case, so a capitalised first label (Platform.example.com) is a host
    private fun String.isCodeName() = CODE_PREFIXES.any { startsWith(it) } || SOURCE_EXTENSIONS.any { substringBefore(':').endsWith(it) }

    private fun String.isOrdinaryWord() = lowercase() in ORDINARY_WORDS
}
