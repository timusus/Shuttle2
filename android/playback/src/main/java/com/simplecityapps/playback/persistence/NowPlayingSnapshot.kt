package com.simplecityapps.playback.persistence

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.squareup.moshi.JsonClass

/**
 * The song the saved queue position names, saved with the queue: enough to show it, and to offer it to the system's
 * resumption controls, before the saved queue has been read back. [positionMs] is where it resumes from, which is saved
 * on its own (see [PlaybackPreferenceManager.nowPlaying]).
 */
@JsonClass(generateAdapter = true)
data class NowPlayingSnapshot(
    val songId: Long,
    val title: String?,
    val artists: List<String>,
    val albumArtist: String?,
    val album: String?,
    val durationMs: Int,
    val path: String,
    val mimeType: String,
    val mediaProvider: MediaProviderType,
    val externalId: String?,
    val artworkVersion: String?,
    @Transient val positionMs: Int = 0
) {
    /** A stand-in for the song, with what the snapshot keeps: enough to show it and load its artwork. */
    fun toSong(): Song = Song(
        id = songId,
        name = title,
        albumArtist = albumArtist,
        artists = artists,
        album = album,
        track = null,
        disc = null,
        duration = durationMs,
        date = null,
        genres = emptyList(),
        path = path,
        size = 0,
        mimeType = mimeType,
        lastModified = null,
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = positionMs,
        blacklisted = false,
        externalId = externalId,
        mediaProvider = mediaProvider,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null,
        artworkVersion = artworkVersion
    )

    companion object {
        fun of(song: Song) = NowPlayingSnapshot(
            songId = song.id,
            title = song.name,
            artists = song.artists,
            albumArtist = song.albumArtist,
            album = song.album,
            durationMs = song.duration,
            path = song.path,
            mimeType = song.mimeType,
            mediaProvider = song.mediaProvider,
            externalId = song.externalId,
            artworkVersion = song.artworkVersion
        )
    }
}
