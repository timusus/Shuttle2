package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.shuttle.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Lyrics found away from the song's own tags. Implementations decide whether they may look (the user's setting). */
interface OnlineLyricsSource {
    /** Whether looking is on, so a screen can look again when the user turns it on. */
    val enabled: Flow<Boolean>

    /** Plain lyrics for [song], or null when none were found or looking is off. */
    suspend fun lookup(song: Song): String?
}

/** For a build or test with no online source. */
object NoOnlineLyrics : OnlineLyricsSource {
    override val enabled: Flow<Boolean> = flowOf(false)

    override suspend fun lookup(song: Song): String? = null
}
