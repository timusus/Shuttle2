package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.shuttle.model.Song

/** Lyrics found away from the song's own tags. Implementations decide whether they may look (the user's setting). */
interface OnlineLyricsSource {
    /** Plain lyrics for [song], or null when none were found or looking is off. */
    suspend fun lookup(song: Song): String?
}

/** For a build or test with no online source. */
object NoOnlineLyrics : OnlineLyricsSource {
    override suspend fun lookup(song: Song): String? = null
}
