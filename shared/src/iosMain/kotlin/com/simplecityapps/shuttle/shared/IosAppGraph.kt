package com.simplecityapps.shuttle.shared

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The iOS app's dependency graph, built once by Swift at launch (`AppGraph` in ios/S2/KMP).
 *
 * A placeholder that exposes an in-memory library, so the iOS shell can prove that a Kotlin `StateFlow` reaches
 * SwiftUI through SKIE's `Observing`. The Metro graph is [SharedAppGraph]; this gives way to it once Swift creates
 * that graph with its platform objects.
 */
class IosAppGraph {
    private val songs = MutableStateFlow(DemoLibrary.songs)

    /** The songs the Library tab lists. */
    val librarySongs: StateFlow<List<Song>> = songs.asStateFlow()
}

/** A few fixed songs standing in for a real library until the repositories move to commonMain (#584). */
internal object DemoLibrary {
    val songs: List<Song> = listOf(
        demoSong(1, "Paranoid Android", "Radiohead", "OK Computer", track = 2, duration = 386_000),
        demoSong(2, "Hyperballad", "Björk", "Post", track = 3, duration = 321_000),
        demoSong(3, "Teardrop", "Massive Attack", "Mezzanine", track = 3, duration = 330_000),
        demoSong(4, "Unfinished Sympathy", "Massive Attack", "Blue Lines", track = 7, duration = 308_000),
        demoSong(5, "Pyramid Song", "Radiohead", "Amnesiac", track = 2, duration = 289_000),
    )

    private fun demoSong(id: Long, name: String, artist: String, album: String, track: Int, duration: Int) = Song(
        id = id,
        name = name,
        albumArtist = artist,
        artists = listOf(artist),
        album = album,
        track = track,
        disc = 1,
        duration = duration,
        date = null,
        genres = emptyList(),
        path = "demo://$id",
        size = 0,
        mimeType = "audio/flac",
        lastModified = null,
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        mediaProvider = MediaProviderType.Shuttle,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null,
    )
}
