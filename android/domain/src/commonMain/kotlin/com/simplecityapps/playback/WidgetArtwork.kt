package com.simplecityapps.playback

import com.simplecityapps.shuttle.model.Song

/** Artwork saved to disk for the home screen widgets, so the widget state only carries a path. */
interface WidgetArtwork {
    /** The saved artwork's path for [song], loading and saving it first if needed. Null when the song has no art. */
    suspend fun artworkPath(song: Song): String?

    /** Deletes every saved file except those for [keep]. */
    suspend fun prune(keep: Collection<Song>)
}
