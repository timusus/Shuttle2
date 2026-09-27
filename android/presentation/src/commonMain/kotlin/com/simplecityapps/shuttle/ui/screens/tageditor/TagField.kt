package com.simplecityapps.shuttle.ui.screens.tageditor

import com.simplecityapps.mediaprovider.model.AudioFile
import com.simplecityapps.shuttle.ui.text.StringKey

/** The tags the editor offers. [TagSection] lays them out. */
enum class TagField(
    val hint: StringKey,
    /** Whether the field is offered when more than one song is being edited. */
    val batch: Boolean = true,
) {
    Title(StringKey.EDIT_TAGS_HINT_TITLE, batch = false),
    Artists(StringKey.EDIT_TAGS_HINT_ARTIST),
    Album(StringKey.EDIT_TAGS_HINT_ALBUM),
    AlbumArtist(StringKey.EDIT_TAGS_HINT_ALBUM_ARTIST),
    Year(StringKey.EDIT_TAGS_HINT_YEAR),
    Track(StringKey.EDIT_TAGS_HINT_TRACK, batch = false),
    TrackTotal(StringKey.EDIT_TAGS_HINT_TRACK_TOTAL),
    Disc(StringKey.EDIT_TAGS_HINT_DISC),
    DiscTotal(StringKey.EDIT_TAGS_HINT_DISC_TOTAL),
    Genres(StringKey.EDIT_TAGS_HINT_GENRES),
    Lyrics(StringKey.EDIT_TAGS_HINT_LYRICS),
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
    val title: StringKey,
    val rows: List<List<TagField>>,
) {
    Song(StringKey.EDIT_TAGS_SECTION_SONG, listOf(listOf(TagField.Title), listOf(TagField.Artists), listOf(TagField.Genres))),
    Album(StringKey.EDIT_TAGS_SECTION_ALBUM, listOf(listOf(TagField.Album), listOf(TagField.AlbumArtist), listOf(TagField.Year))),
    Numbering(StringKey.EDIT_TAGS_SECTION_NUMBERING, listOf(listOf(TagField.Track, TagField.TrackTotal), listOf(TagField.Disc, TagField.DiscTotal))),
    Lyrics(StringKey.EDIT_TAGS_SECTION_LYRICS, listOf(listOf(TagField.Lyrics))),
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
