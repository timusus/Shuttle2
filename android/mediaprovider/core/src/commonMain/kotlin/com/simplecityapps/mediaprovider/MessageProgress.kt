package com.simplecityapps.mediaprovider

sealed class FlowEvent<out T, out U> {
    class Progress<T, U>(val data: U) : FlowEvent<T, U>()

    class Success<T>(val result: T) : FlowEvent<T, Nothing>()

    class Failure(val message: String?) : FlowEvent<Nothing, Nothing>()
}

/** The stage an import has reached, which [MediaImporter] describes to the user in its own words. */
sealed interface ImportPhase {
    /** Signing in to a server. */
    data object Connecting : ImportPhase

    /** Asking the provider for its songs or playlists. */
    data object Fetching : ImportPhase

    /** Writing the [songCount] songs the provider found to the library. */
    data class Saving(val songCount: Int) : ImportPhase
}

/**
 * How far a provider's import has got: its [phase], how many items of how many it has been through when it knows,
 * and a [detail] of its own to show instead, such as the song a local scan has just read.
 */
data class MessageProgress(
    val phase: ImportPhase,
    val progress: Progress?,
    val detail: String? = null
)
