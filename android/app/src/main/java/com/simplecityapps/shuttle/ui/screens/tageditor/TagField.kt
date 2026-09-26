package com.simplecityapps.shuttle.ui.screens.tageditor

import androidx.annotation.StringRes
import com.simplecityapps.mediaprovider.model.AudioFile
import com.simplecityapps.shuttle.R

/** The tags the editor offers. [TagSection] lays them out. */
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
 * The editor's sections, in order, and the rows of fields in each. Numbers and their totals share a row, as on a disc
 * sleeve: "3 of 12".
 */
enum class TagSection(
    @StringRes val title: Int,
    val rows: List<List<TagField>>,
) {
    Song(R.string.edit_tags_section_song, listOf(listOf(TagField.Title), listOf(TagField.Artists), listOf(TagField.Genres))),
    Album(R.string.edit_tags_section_album, listOf(listOf(TagField.Album), listOf(TagField.AlbumArtist), listOf(TagField.Year))),
    Numbering(R.string.edit_tags_section_numbering, listOf(listOf(TagField.Track, TagField.TrackTotal), listOf(TagField.Disc, TagField.DiscTotal))),
    Lyrics(R.string.edit_tags_section_lyrics, listOf(listOf(TagField.Lyrics))),
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
