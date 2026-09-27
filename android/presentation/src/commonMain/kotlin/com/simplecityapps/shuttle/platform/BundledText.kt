package com.simplecityapps.shuttle.platform

/**
 * Text files bundled with the app (the changelog, the licences metadata), read by name: Android's assets, the iOS
 * app bundle. Null when the platform bundles no file of that name.
 */
fun interface BundledText {
    suspend fun read(name: String): String?
}
