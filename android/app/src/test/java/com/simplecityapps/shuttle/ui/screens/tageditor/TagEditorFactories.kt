package com.simplecityapps.shuttle.ui.screens.tageditor

import com.simplecityapps.mediaprovider.model.AudioFile
import com.simplecityapps.shuttle.model.Song

fun createAudioFile(
    title: String? = "Title",
    artists: List<String> = listOf("Artist"),
    album: String? = "Album",
    albumArtist: String? = "Album Artist",
    year: String? = "2024",
    track: Int? = 1,
    trackTotal: Int? = 10,
    disc: Int? = 1,
    discTotal: Int? = 1,
    genres: List<String> = listOf("Jazz"),
    lyrics: String? = null,
) = AudioFile(
    path = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2Fsong.mp3",
    size = 1,
    lastModified = 0,
    mimeType = "audio/mpeg",
    title = title,
    albumArtist = albumArtist,
    artists = artists,
    album = album,
    track = track,
    trackTotal = trackTotal,
    disc = disc,
    discTotal = discTotal,
    duration = 1000,
    year = year,
    genres = genres,
    replayGainTrack = null,
    replayGainAlbum = null,
    lyrics = lyrics,
    grouping = null,
    bitRate = null,
    bitDepth = null,
    sampleRate = null,
    channelCount = null,
)

/** Tag access over an in-memory map of song id to file tags; a song without an entry can't be read. */
class FakeTagFileAccess(
    var files: Map<Long, AudioFile> = emptyMap(),
) : TagFileAccess {
    /** Songs whose writes fail. */
    var failingSongIds: Set<Long> = emptySet()

    /** Every write as (song id, metadata), in order. */
    val writes = mutableListOf<Pair<Long, Map<String, List<String>>>>()

    override suspend fun read(song: Song): AudioFile? = files[song.id]

    override suspend fun write(
        song: Song,
        metadata: Map<String, List<String>>,
    ): Boolean {
        writes += song.id to metadata
        return song.id !in failingSongIds
    }
}
