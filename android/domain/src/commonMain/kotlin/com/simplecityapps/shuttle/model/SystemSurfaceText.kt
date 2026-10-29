package com.simplecityapps.shuttle.model

/** What a system surface (lock screen, Now Playing, widgets) shows for one queued song. */
data class SystemSurfaceText(val title: String, val artist: String?, val album: String?)

/**
 * The text for the song at [index] of [queue], or null when there is none. Songs in a work are reworded while
 * [classicalDisplay] is on; a lone classical song and everything else stays plain, as on Android's media session.
 */
fun systemSurfaceText(queue: List<Song>, index: Int, classicalDisplay: Boolean): SystemSurfaceText? {
    val song = queue.getOrNull(index) ?: return null
    if (classicalDisplay && workProgress(queue, index) != null) {
        lockScreenText(queue, index)?.let { return SystemSurfaceText(it.title, it.artist ?: song.friendlyArtistName, song.album) }
    }
    return SystemSurfaceText(song.name.orEmpty(), song.friendlyArtistName, song.album)
}
