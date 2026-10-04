package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.mediaprovider.repository.playhistory.ResumePoint
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.SmartPlaylistId
import com.simplecityapps.shuttle.model.playContext
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.text.StringKey
import kotlin.time.Instant

/** One of Home's sections (#633), in the order Home shows them. */
enum class HomeSectionId {
    JumpBackIn,
    AroundThisTime,
    HeavyRotation,
    Rediscover,
    RecentlyAdded,
    GenrePicks,

    /** The cold-start prompt to shuffle the whole library; it holds no items. */
    ShuffleAll,
}

/** Which title a section shows; each platform maps it to its own string. Around this time's depends on the hour. */
enum class HomeSectionTitle {
    JumpBackIn,
    ThisMorning,
    ThisAfternoon,
    Tonight,
    HeavyRotation,
    Rediscover,
    RecentlyAdded,
    GenrePicks,
    ShuffleAll,
}

/**
 * A section of Home: its [title], a one-line [subtitle] under it saying why these items (#671), and its items. Titles
 * are mapped per platform, for each one's casing; subtitles are shared strings, so both apps say the same.
 */
data class HomeSection(
    val id: HomeSectionId,
    val title: HomeSectionTitle,
    val subtitle: StringKey?,
    val items: List<HomeItem>,
    /** Where each item's queue was left, by [HomeItem.key]: Jump back in's items played from before (#670). */
    val progress: Map<String, HomeItemProgress> = emptyMap(),
) {
    /**
     * Whether the section's header offers to play the whole shelf: not the Jump back in grid, whose tiles each resume
     * their own queue, nor Genre picks, whose tiles shuffle and would otherwise queue every song of every genre.
     */
    val playable: Boolean
        get() = items.isNotEmpty() && id != HomeSectionId.JumpBackIn && id != HomeSectionId.ShuffleAll && items.none { it is HomeItem.GenreItem }
}

/**
 * Where an item's queue was left (#670, #706): on the song [songName] (null once it's no longer in the library),
 * [positionMs] into it, [fraction] of the way through the queue (0 to 1, in the shuffled order when [shuffled]), at
 * [updatedAt]; or [finished], its last song played through, so playing it starts over.
 */
data class HomeItemProgress(
    val songName: String?,
    val positionMs: Long,
    val fraction: Float,
    val shuffled: Boolean,
    val finished: Boolean,
    val updatedAt: Instant,
) {
    companion object {
        /** [point]'s progress: its song's place in the queue plus how far into the song it was left, over the queue's length. */
        fun of(point: ResumePoint): HomeItemProgress {
            val fraction = when {
                point.finished -> 1f

                point.trackCount <= 0 -> 0f

                else -> {
                    val intoSong = point.songDurationMs?.takeIf { it > 0 }?.let { (point.positionMs.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
                    (point.track.coerceIn(0, point.trackCount - 1) + intoSong) / point.trackCount
                }
            }
            return HomeItemProgress(point.songName, point.positionMs, fraction, point.shuffled, point.finished, point.updatedAt)
        }
    }
}

/** Something a Home section suggests: what it shows, and what tapping play on it plays. */
sealed interface HomeItem {
    /** What the item plays from, as the play history records it. */
    val playContext: PlayContext

    /** Stable across reloads and unique within Home, for list identity. */
    val key: String get() = "${playContext.type}:${playContext.id}"

    /** Plays the item from its start, or for a genre, shuffles it. */
    fun playAction(): MediaAction

    /** Carries on the item where its queue was left, as a Jump back in tile's play does (#670), else [playAction]. */
    fun resumeAction(): MediaAction = MediaAction.Resume(playAction(), playContext)

    data class AlbumItem(val album: Album) : HomeItem {
        override val playContext: PlayContext get() = album.playContext

        override fun playAction(): MediaAction = MediaAction.Play(MediaSelection.Albums(album))
    }

    data class ArtistItem(val albumArtist: AlbumArtist) : HomeItem {
        override val playContext: PlayContext get() = albumArtist.playContext

        override fun playAction(): MediaAction = MediaAction.Play(MediaSelection.AlbumArtists(albumArtist))
    }

    data class PlaylistItem(val playlist: Playlist) : HomeItem {
        override val playContext: PlayContext get() = playlist.playContext

        override fun playAction(): MediaAction = MediaAction.Play(MediaSelection.Playlists(playlist))
    }

    data class SmartPlaylistItem(val smartPlaylistId: SmartPlaylistId) : HomeItem {
        override val playContext: PlayContext get() = PlayContext.SmartPlaylist(smartPlaylistId)

        override fun playAction(): MediaAction = MediaAction.Play(MediaSelection.SongsMatching(smartPlaylistId.songQuery), context = playContext)
    }

    /** A genre tile shuffles the genre. */
    data class GenreItem(val genre: Genre) : HomeItem {
        override val playContext: PlayContext get() = genre.playContext

        override fun playAction(): MediaAction = MediaAction.Shuffle(MediaSelection.Genres(genre))
    }
}
