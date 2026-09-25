package com.simplecityapps.shuttle.ui.screens.tageditor

import com.simplecityapps.mediaprovider.model.AudioFile
import com.simplecityapps.sampleSongs
import com.simplecityapps.shuttle.model.Song

/** The tags [song]'s file holds, matching what the library has for it. */
fun Song.audioFile(): AudioFile = createAudioFile(
    title = name,
    artists = artists,
    album = album,
    albumArtist = albumArtist,
    year = date?.year?.toString(),
    track = track,
    trackTotal = 10,
    disc = disc,
    discTotal = 1,
    genres = genres,
)

/** One sample song, every field shown. */
fun singleSongEditing(song: Song = sampleSongs(1).single()) = TagEditorUiState.Editing(songCount = 1, fields = tagFields(listOf(song.audioFile())))

/** Three sample songs from different albums: album, artists and year are mixed; one more song can't be edited. */
fun batchEditing(): TagEditorUiState.Editing {
    val songs = sampleSongs(4)
    return TagEditorUiState.Editing(songCount = 3, fields = tagFields(songs.take(3).map { it.audioFile() }), skipped = listOf(songs[3]))
}

val readingTags = TagEditorUiState.Reading(TagProgress(1, 3))

fun writingTags() = singleSongEditing().let { editing ->
    editing.copy(fields = editing.fields.map { if (it.field == TagField.Album) it.copy(text = "New Album") else it }, writing = TagProgress(0, 1))
}
