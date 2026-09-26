package com.simplecityapps.shuttle.ui.shell.player

import com.simplecityapps.shuttle.model.Song

/** 1.0.10's `SkipButton`: seconds seeked per hold-repeat tick on the skip previous/next buttons. */
internal const val SkipHoldSeekSeconds = 15

/** 1.0.10's audiobook/podcast `seekBackwardButton`: seconds seeked per tap. */
internal const val SeekBackwardSeconds = 10

/** 1.0.10's audiobook/podcast `seekForwardButton`: seconds seeked per tap. */
internal const val SeekForwardSeconds = 30

/** Audiobook and podcast songs show seek-back/seek-forward buttons instead of skip (1.0.10 parity, #430). */
internal val Song.Type.isSeekable: Boolean get() = this != Song.Type.Audio

/** Seeks by [deltaSeconds] (negative to seek backward) from [progress]'s position, clamped to the song's duration. */
internal fun PlayerActions.seekBy(progress: PlayerProgress, deltaSeconds: Int) {
    val target = (progress.positionMs + deltaSeconds * 1000L).coerceIn(0L, progress.durationMs.coerceAtLeast(0L))
    seekTo(target)
}
