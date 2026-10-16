package com.simplecityapps.playback.mediasession

import androidx.media3.common.MediaMetadata
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.lockScreenText
import com.simplecityapps.shuttle.model.workProgress

/** The movement as title and "Composer: Work · lead performer" as artist, for one song of a work. */
internal class ClassicalText(val title: String, val artist: String?, val composer: String?)

/**
 * The text for the song at [index] of [queue] when it sits in a work block, else null: [lockScreenText] alone would
 * also reword a lone classical song, which Classical display leaves as it is.
 */
internal fun classicalText(
    queue: List<QueueItem>,
    index: Int
): ClassicalText? {
    // A view, not a copy: only the songs around [index] are read.
    val songs = object : AbstractList<Song>() {
        override val size get() = queue.size

        override fun get(index: Int) = queue[index].song
    }
    if (workProgress(songs, index) == null) return null
    val text = lockScreenText(songs, index) ?: return null
    return ClassicalText(text.title, text.artist, songs[index].composer)
}

internal fun MediaMetadata.withClassicalText(text: ClassicalText): MediaMetadata = buildUpon()
    .setTitle(text.title)
    .setArtist(text.artist ?: artist)
    .setComposer(text.composer)
    .build()
