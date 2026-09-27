package com.simplecityapps.shuttle.platform

/**
 * What this platform can do, so shared lists hide what it can't instead of binding failing stubs
 * (docs/architecture/ios-port/phase-4-platform-seams.md, S0). Android binds all true.
 */
data class PlatformFeatures(
    val homeScreenWidgets: Boolean,
    val artworkPrefetch: Boolean,
    val scheduledRescan: Boolean,
    val cast: Boolean,
    val offlineDownloads: Boolean,
)
