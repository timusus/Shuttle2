package com.simplecityapps.mediaprovider

/**
 * The scheme a [MediaInfoProvider] or [RemoteArtworkProvider] matches [path] (a `Song.path`) against, without building
 * a Uri: "file" for a raw file path, the part before "://" otherwise, null for a path with neither. Keeps provider
 * matching in common code, and off `Uri.parse`/`Uri.fromFile` in plain-JVM tests, where they aren't mocked (#552).
 */
internal fun schemeOf(path: String): String? = if (path.startsWith("/")) {
    "file"
} else {
    path.substringBefore("://", missingDelimiterValue = "").takeIf { it.isNotEmpty() }
}
