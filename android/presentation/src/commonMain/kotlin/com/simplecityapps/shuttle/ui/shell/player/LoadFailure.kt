package com.simplecityapps.shuttle.ui.shell.player

import com.simplecityapps.playback.SongLoadFailure
import com.simplecityapps.shuttle.model.MediaProviderType

/** Why the current song can't play, which the player says in place of its seek bar. */
sealed interface LoadFailure {
    /** The song's server couldn't be reached; the message opens Sources. */
    data class ServerUnreachable(val provider: MediaProviderType) : LoadFailure

    data object CantPlay : LoadFailure
}

internal fun SongLoadFailure.toLoadFailure(): LoadFailure = if (serverUnreachable && song.mediaProvider.remote) LoadFailure.ServerUnreachable(song.mediaProvider) else LoadFailure.CantPlay
