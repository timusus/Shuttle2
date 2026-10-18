package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.SourceStateStore

/** Bump when the importer starts storing a tag it didn't before, so stored songs are read again ([songTagsOutdated]). */
const val SONG_TAGS_VERSION = 7

/**
 * The version [type]'s songs must have been imported under: only Subsonic fills the version 6 fields among the
 * remote providers, and only local files the version 7 ones.
 */
fun requiredSongTagsVersion(type: MediaProviderType): Int = when {
    !type.remote -> SONG_TAGS_VERSION
    type == MediaProviderType.Subsonic -> 6
    else -> 5
}

/** Whether [type]'s songs were last imported before [requiredSongTagsVersion]: recorded when its import is stored. */
fun SourceStateStore.songTagsOutdated(type: MediaProviderType): Boolean = songTagsVersion(type.name) < requiredSongTagsVersion(type)
