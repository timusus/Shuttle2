package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager

/**
 * Raised when the importer starts storing a tag it didn't before, so the songs stored already are read again once
 * ([songTagsOutdated]). 1: the raw artist and album tags and ids of #637. 3: the codec, bit rate,
 * sample rate and channel count of Emby and Jellyfin songs (#889). 4: a local combined album artist tag
 * ("A; B") reads as its first artist (#886). 5: a local multi-value ALBUMARTIST lists each value as an album artist.
 * 6: the classical fields of #1065. 7: the raw album artist tag (local files only). Local files whose path, size
 * and modified date are unchanged keep their stored tags, so only this re-import reads the new fields into them.
 */
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
fun GeneralPreferenceManager.songTagsOutdated(type: MediaProviderType): Boolean = songTagsVersion(type.name) < requiredSongTagsVersion(type)
