package com.simplecityapps.mediaprovider

/** The progress and error messages [MediaImporter] reports, in the user's language; each platform reads them from its own resources. */
interface MediaImportStrings {
    /** Signing in to [provider] (Jellyfin, Emby or Plex). */
    fun connecting(provider: String): String

    /** Asking the provider for its songs, before it has said how many there are. */
    val fetching: String

    /** Asking the provider for its songs: [count] of its [total] so far. */
    fun fetchingSongs(
        count: Int,
        total: Int
    ): String

    /** Writing the [count] songs the provider found to the library. */
    fun saving(count: Int): String

    /** A provider's import failed. */
    val importError: String
}

/** [progress] in the user's words: the provider's own [MessageProgress.detail] if it has one, else its phase and count. */
fun MediaImportStrings.describe(
    progress: MessageProgress,
    provider: String
): String = progress.detail ?: when (val phase = progress.phase) {
    ImportPhase.Connecting -> connecting(provider)
    ImportPhase.Fetching -> progress.progress?.let { fetchingSongs(it.progress, it.total) } ?: fetching
    is ImportPhase.Saving -> saving(phase.songCount)
}
