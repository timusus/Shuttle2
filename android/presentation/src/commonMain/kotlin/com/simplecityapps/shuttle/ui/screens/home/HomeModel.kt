package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.SmartPlaylistId
import com.simplecityapps.shuttle.model.playContext
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection

/** One of Home's sections (#633), in the order Home shows them. */
enum class HomeSectionId {
    JumpBackIn,
    AroundThisTime,
    OnRepeat,
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
    OnRepeat,
    Rediscover,
    RecentlyAdded,
    GenrePicks,
    ShuffleAll,
}

data class HomeSection(
    val id: HomeSectionId,
    val title: HomeSectionTitle,
    val items: List<HomeItem>,
)

/** Something a Home section suggests: what it shows, and what tapping play on it plays. */
sealed interface HomeItem {
    /** What the item plays from, as the play history records it. */
    val playContext: PlayContext

    /** Stable across reloads and unique within Home, for list identity. */
    val key: String get() = "${playContext.type}:${playContext.id}"

    /** Plays the item from its start, or for a genre, shuffles it. */
    fun playAction(): MediaAction

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
