package com.simplecityapps.shuttle.model

private val albumOrder = compareBy<Song>({ song -> song.disc ?: Int.MAX_VALUE }, { song -> song.track ?: Int.MAX_VALUE }, { song -> song.path })

/**
 * The song that stands in for an album when asking a media server for its artwork: the first, by disc, track and path,
 * with an image version, since a server may have art for only some tracks; otherwise the first. The order doesn't depend
 * on the list's, so every caller (and every sync) picks the same song.
 */
fun List<Song>.albumArtworkSong(): Song? = filter { song -> song.artworkVersion != null }.minWithOrNull(albumOrder) ?: minWithOrNull(albumOrder)
