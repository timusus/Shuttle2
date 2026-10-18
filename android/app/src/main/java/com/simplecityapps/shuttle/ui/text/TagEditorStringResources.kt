package com.simplecityapps.shuttle.ui.text

import androidx.annotation.StringRes
import com.simplecityapps.shuttle.R

@get:StringRes
val TagEditorString.resId: Int
    get() = when (this) {
        TagEditorString.EDIT_TAGS_HINT_TITLE -> R.string.edit_tags_hint_title
        TagEditorString.EDIT_TAGS_HINT_ARTIST -> R.string.edit_tags_hint_artist
        TagEditorString.EDIT_TAGS_HINT_ALBUM -> R.string.edit_tags_hint_album
        TagEditorString.EDIT_TAGS_HINT_ALBUM_ARTIST -> R.string.edit_tags_hint_album_artist
        TagEditorString.EDIT_TAGS_HINT_YEAR -> R.string.edit_tags_hint_year
        TagEditorString.EDIT_TAGS_HINT_TRACK -> R.string.edit_tags_hint_track
        TagEditorString.EDIT_TAGS_HINT_TRACK_TOTAL -> R.string.edit_tags_hint_track_total
        TagEditorString.EDIT_TAGS_HINT_DISC -> R.string.edit_tags_hint_disc
        TagEditorString.EDIT_TAGS_HINT_DISC_TOTAL -> R.string.edit_tags_hint_disc_total
        TagEditorString.EDIT_TAGS_HINT_GENRES -> R.string.edit_tags_hint_genres
        TagEditorString.EDIT_TAGS_HINT_LYRICS -> R.string.edit_tags_hint_lyrics
        TagEditorString.EDIT_TAGS_SECTION_SONG -> R.string.edit_tags_section_song
        TagEditorString.EDIT_TAGS_SECTION_ALBUM -> R.string.edit_tags_section_album
        TagEditorString.EDIT_TAGS_SECTION_NUMBERING -> R.string.edit_tags_section_numbering
        TagEditorString.EDIT_TAGS_SECTION_LYRICS -> R.string.edit_tags_section_lyrics
    }
