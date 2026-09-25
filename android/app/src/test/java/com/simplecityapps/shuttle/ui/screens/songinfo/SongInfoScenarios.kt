package com.simplecityapps.shuttle.ui.screens.songinfo

import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.preview.sampleSongs

/** A sample song as a TagLib-scanned file: a document path, file details and ReplayGain. */
fun sampleSongWithFileDetails(): Song {
    val song = sampleSongs(1).single()
    return song.copy(
        path = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2F${song.albumArtist}%2F${song.album}%2F01%20${song.name}.flac"
            .replace(" ", "%20"),
        mimeType = "audio/flac",
        size = 31_457_280,
        playCount = 12,
        bitRate = 1024,
        bitDepth = 24,
        sampleRate = 96000,
        channelCount = 2,
        replayGainTrack = -7.25,
        replayGainAlbum = -6.8,
        lyrics = "",
    )
}

fun songInfoReady(song: Song = sampleSongWithFileDetails()) = SongInfoUiState(song = song, loading = false)

val songInfoNotFound = SongInfoUiState(song = null, loading = false)
