package com.simplecityapps.shuttle.model

/**
 * What a queue was started from: the album, artist, playlist, smart playlist or genre the user chose to play (#633).
 * The queue remembers it, and each play event records it, so Home can suggest a way back into what was played.
 * [None] is a queue that wasn't started from one of those: a list of songs, a folder, a play request from outside.
 *
 * Stored as a [type] and an [id] ([decode] reads them back): the id is what finds the context again, so an album and
 * an artist are stored by their group key, not a row id a reimport would change.
 */
sealed interface PlayContext {
    val type: String
    val id: String?

    data class Album(val groupKey: AlbumGroupKey) : PlayContext {
        override val type: String get() = TYPE_ALBUM
        override val id: String get() = groupKey.encode()
    }

    data class AlbumArtist(val groupKey: AlbumArtistGroupKey) : PlayContext {
        override val type: String get() = TYPE_ALBUM_ARTIST
        override val id: String get() = AlbumGroupKey.encodeParts(groupKey.key)
    }

    data class Playlist(val playlistId: Long) : PlayContext {
        override val type: String get() = TYPE_PLAYLIST
        override val id: String get() = playlistId.toString()
    }

    /** A built-in smart playlist, by its [SmartPlaylistId.id] slug. */
    data class SmartPlaylist(val smartPlaylistId: SmartPlaylistId) : PlayContext {
        override val type: String get() = TYPE_SMART_PLAYLIST
        override val id: String get() = smartPlaylistId.id
    }

    /** A smart playlist the user defined ([com.simplecityapps.shuttle.model.UserSmartPlaylist]). */
    data class UserSmartPlaylist(val smartPlaylistId: Long) : PlayContext {
        override val type: String get() = TYPE_USER_SMART_PLAYLIST
        override val id: String get() = smartPlaylistId.toString()
    }

    data class Genre(val name: String) : PlayContext {
        override val type: String get() = TYPE_GENRE
        override val id: String get() = name
    }

    data object None : PlayContext {
        override val type: String get() = TYPE_NONE
        override val id: String? get() = null
    }

    companion object {
        const val TYPE_ALBUM = "album"
        const val TYPE_ALBUM_ARTIST = "album_artist"
        const val TYPE_PLAYLIST = "playlist"
        const val TYPE_SMART_PLAYLIST = "smart_playlist"
        const val TYPE_USER_SMART_PLAYLIST = "user_smart_playlist"
        const val TYPE_GENRE = "genre"
        const val TYPE_NONE = "none"

        /** The context stored as [type] and [id]; [None] for one that can't be read back (an unknown type, a bad id). */
        fun decode(
            type: String?,
            id: String?
        ): PlayContext = when (type) {
            TYPE_ALBUM -> id?.let(AlbumGroupKey::decode)?.let(::Album)
            TYPE_ALBUM_ARTIST -> id?.let(AlbumGroupKey::decodeParts)?.takeIf { it.size == 1 }?.let { (artistKey) -> AlbumArtist(AlbumArtistGroupKey(artistKey)) }
            TYPE_PLAYLIST -> id?.toLongOrNull()?.let(::Playlist)
            TYPE_SMART_PLAYLIST -> id?.let(SmartPlaylistId::fromId)?.let(::SmartPlaylist)
            TYPE_USER_SMART_PLAYLIST -> id?.toLongOrNull()?.let(::UserSmartPlaylist)
            TYPE_GENRE -> id?.let(::Genre)
            else -> null
        } ?: None
    }
}

/** Playing the album; [PlayContext.None] for one without a group key. */
val Album.playContext: PlayContext get() = groupKey?.let { PlayContext.Album(it) } ?: PlayContext.None

val AlbumArtist.playContext: PlayContext get() = PlayContext.AlbumArtist(groupKey)

val Genre.playContext: PlayContext get() = PlayContext.Genre(name)

val Playlist.playContext: PlayContext get() = PlayContext.Playlist(id)

val SmartPlaylist.playContext: PlayContext get() = PlayContext.SmartPlaylist(id)
