package com.simplecityapps.shuttle.model

/**
 * The song that stands in for an album when asking a media server for its artwork: the first with an image version,
 * since a server may have art for only some tracks; otherwise the first.
 */
fun List<Song>.albumArtworkSong(): Song? = firstOrNull { song -> song.artworkVersion != null } ?: firstOrNull()
