package com.simplecityapps.shuttle.ui.screens.tageditor

import androidx.annotation.StringRes
import com.simplecityapps.mediaprovider.model.AudioFile
import com.simplecityapps.shuttle.R

/** The tags the editor offers, in the order it shows them. */
enum class TagField(
    @StringRes val hint: Int,
    /** Whether the field is offered when more than one song is being edited. */
    val batch: Boolean = true,
) {
    Title(R.string.edit_tags_hint_title, batch = false),
    Artists(R.string.edit_tags_hint_artist),
    Album(R.string.edit_tags_hint_album),
    AlbumArtist(R.string.edit_tags_hint_album_artist),
    Year(R.string.edit_tags_hint_year),
    Track(R.string.edit_tags_hint_track, batch = false),
    TrackTotal(R.string.edit_tags_hint_track_total),
    Disc(R.string.edit_tags_hint_disc),
    DiscTotal(R.string.edit_tags_hint_disc_total),
    Genres(R.string.edit_tags_hint_genres),
    Lyrics(R.string.edit_tags_hint_lyrics),
    ;

    val numeric: Boolean get() = this == Year || this == Track || this == TrackTotal || this == Disc || this == DiscTotal

    /** This field's value in one file, as the editor shows it: lists joined with ", ". */
    fun valueOf(file: AudioFile): String? = when (this) {
        Title -> file.title
        Artists -> file.artists.takeIf { it.isNotEmpty() }?.joinToString(", ")
        Album -> file.album
        AlbumArtist -> file.albumArtist
        Year -> file.year
        Track -> file.track?.toString()
        TrackTotal -> file.trackTotal?.toString()
        Disc -> file.disc?.toString()
        DiscTotal -> file.discTotal?.toString()
        Genres -> file.genres.takeIf { it.isNotEmpty() }?.joinToString(", ")
        Lyrics -> file.lyrics
    }
}

/**
 * One field as the editor shows it. A field whose files disagree is [mixed]: it starts empty and shows "Multiple
 * values", and it's only written if the user types something into it.
 */
data class TagFieldState(
    val field: TagField,
    val initial: String,
    val text: String = initial,
    val mixed: Boolean = false,
) {
    val changed: Boolean get() = text != initial
}

/** The editor's fields for [files]: every field for one song, the batch fields for several. */
fun tagFields(files: List<AudioFile>): List<TagFieldState> = TagField.entries
    .filter { files.size <= 1 || it.batch }
    .map { field ->
        val values = files.map(field::valueOf).distinct()
        val mixed = values.size > 1
        TagFieldState(field = field, initial = if (mixed) "" else values.firstOrNull().orEmpty(), mixed = mixed)
    }

/** The fields the user changed and their new text: what a save writes, and nothing else. */
fun List<TagFieldState>.edits(): Map<TagField, String> = filter { it.changed }.associate { it.field to it.text }
