package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.shuttle.model.Song

/**
 * An artwork version for a group of songs (an album or album artist): it changes whenever any song's
 * artworkVersion does, and is null when none of the songs has one.
 */
internal fun List<Song>.combinedArtworkVersion(): String? = mapNotNull { song -> song.artworkVersion }
    .distinct()
    .sorted()
    .takeIf { versions -> versions.isNotEmpty() }
    ?.let { versions -> Integer.toHexString(versions.joinToString("|").hashCode()) }
