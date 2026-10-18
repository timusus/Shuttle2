package com.simplecityapps.mediaprovider

/**
 * Scheme of a `Song.path` without building a Uri, so provider matching stays testable on plain JVM.
 */
internal fun schemeOf(path: String): String? = if (path.startsWith("/")) {
    "file"
} else {
    path.substringBefore("://", missingDelimiterValue = "").takeIf { it.isNotEmpty() }
}
