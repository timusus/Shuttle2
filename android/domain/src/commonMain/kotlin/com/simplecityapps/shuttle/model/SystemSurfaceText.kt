package com.simplecityapps.shuttle.model

/** What a system surface (lock screen, Now Playing, widgets) shows for one queued song. */
data class SystemSurfaceText(val title: String, val artist: String?, val album: String?)

/**
 * The reworded text for the song at [index] of [queue] when it sits in a work block, else null: [lockScreenText]
 * alone would also reword a lone classical song, which Classical display leaves as it is.
 */
fun classicalSurfaceText(queue: List<Song>, index: Int): SystemSurfaceText? {
    val song = queue.getOrNull(index) ?: return null
    if (workProgress(queue, index) == null) return null
    val text = lockScreenText(queue, index) ?: return null
    return SystemSurfaceText(text.title, text.artist ?: song.friendlyArtistName, song.album)
}

/** The text for the song at [index] of [queue], or null when there is none; reworded only while [classicalDisplay] is on. */
fun systemSurfaceText(queue: List<Song>, index: Int, classicalDisplay: Boolean): SystemSurfaceText? {
    val song = queue.getOrNull(index) ?: return null
    if (classicalDisplay) classicalSurfaceText(queue, index)?.let { return it }
    return SystemSurfaceText(song.name.orEmpty(), song.friendlyArtistName, song.album)
}

/** A view, not a copy: queue scans only read the songs around an index. */
fun <T> List<T>.songsView(song: (T) -> Song): List<Song> = object : AbstractList<Song>() {
    override val size get() = this@songsView.size

    override fun get(index: Int) = song(this@songsView[index])
}
