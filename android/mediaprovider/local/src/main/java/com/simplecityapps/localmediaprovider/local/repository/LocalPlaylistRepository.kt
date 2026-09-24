package com.simplecityapps.localmediaprovider.local.repository

import android.content.Context
import android.net.Uri
import com.simplecityapps.localmediaprovider.local.data.room.dao.PlaylistDataDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.PlaylistSongJoinDao
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistData
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistSongJoin
import com.simplecityapps.mediaprovider.M3uWriter
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.playlists.comparator
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.PlaylistSong
import com.simplecityapps.shuttle.model.SmartPlaylist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import com.simplecityapps.shuttle.sorting.SongSortOrder
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * True for playlists imported from a local .m3u file (their [Playlist.externalId] is the file's
 * SAF document URI, set by `TaglibMediaProvider.findPlaylists`) - the only ones with a file to
 * keep in sync when their songs change.
 */
internal fun Playlist.isM3uSynced(): Boolean = mediaProvider == MediaProviderType.Shuttle && externalId != null

class LocalPlaylistRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val playlistDataDao: PlaylistDataDao,
    private val playlistSongJoinDao: PlaylistSongJoinDao
) : PlaylistRepository {
    private val m3uWriter = M3uWriter()

    private val playlistsRelay: StateFlow<List<Playlist>?> by lazy {
        playlistDataDao
            .getAll()
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Lazily, null)
    }

    override fun getPlaylists(query: PlaylistQuery): Flow<List<Playlist>> = playlistsRelay
        .filterNotNull()
        .map { playlists ->
            playlists
                .filter(query.predicate)
                .toMutableList()
                .sortedWith(query.sortOrder.comparator)
        }

    override fun getSmartPlaylists(): Flow<List<SmartPlaylist>> = flow {
        emit(
            listOf(
                SmartPlaylist(com.simplecityapps.mediaprovider.R.string.playlist_title_recently_added, SongQuery.RecentlyAdded()),
                SmartPlaylist(com.simplecityapps.mediaprovider.R.string.playlist_title_most_played, SongQuery.PlayCount(2, SongSortOrder.PlayCount))
            )
        )
    }

    override suspend fun getFavoritesPlaylist(): Playlist {
        val favoritesName = context.getString(com.simplecityapps.mediaprovider.R.string.playlist_title_favorites)
        return withContext(Dispatchers.IO) {
            playlistsRelay
                .filterNotNull()
                .firstOrNull()
                ?.firstOrNull { it.name == favoritesName }
                ?: createPlaylist(
                    name = favoritesName,
                    mediaProviderType = MediaProviderType.Shuttle,
                    songs = null,
                    externalId = null
                )
        }
    }

    override suspend fun createPlaylist(
        name: String,
        mediaProviderType: MediaProviderType,
        songs: List<Song>?,
        externalId: String?
    ): Playlist {
        val playlistId =
            playlistDataDao.insert(
                PlaylistData(
                    name = name,
                    sortOrder = PlaylistSongSortOrder.Position,
                    mediaProviderType = mediaProviderType,
                    externalId = externalId
                ),
                songIds = songs.orEmpty().inLibrary().map { song -> song.id }
            )
        val playlist = playlistDataDao.getPlaylist(playlistId)
        Timber.v("Created playlist: ${playlist.name} with ${playlist.songCount} songs}")
        return playlist
    }

    override suspend fun addToPlaylist(
        playlist: Playlist,
        songs: List<Song>
    ) {
        playlistSongJoinDao.insert(
            songs.inLibrary().mapIndexed { i, song ->
                PlaylistSongJoin(
                    playlistId = playlist.id,
                    songId = song.id,
                    sortOrder = (playlist.songCount + i).toLong()
                )
            }
        )
        syncM3uFile(playlist)
    }

    override suspend fun removeFromPlaylist(
        playlist: Playlist,
        playlistSongs: List<PlaylistSong>
    ) {
        playlistSongJoinDao.delete(
            playlistId = playlist.id,
            playlistSongIds = playlistSongs.map { playlistSong -> playlistSong.id }.toTypedArray()
        )
        syncM3uFile(playlist)
    }

    override suspend fun removeSongsFromPlaylist(
        playlist: Playlist,
        songs: List<Song>
    ) {
        playlistSongJoinDao.deleteSongs(
            playlistId = playlist.id,
            songIds = songs.map { it.id }.toTypedArray()
        )
        syncM3uFile(playlist)
    }

    override fun getSongsForPlaylist(playlist: Playlist): Flow<List<PlaylistSong>> = playlistSongJoinDao.getSongsForPlaylist(playlist.id)
        .map { playlistSong ->
            val comparator = playlist.sortOrder.comparator
            playlistSong.sortedWith(if (playlist.sortDescending) comparator.reversed() else comparator)
        }

    override suspend fun deletePlaylist(playlist: Playlist) = playlistDataDao.delete(playlist.id)

    override suspend fun deleteAll(mediaProviderType: MediaProviderType) = playlistDataDao.deleteAll(mediaProviderType)

    override suspend fun clearPlaylist(playlist: Playlist) {
        playlistDataDao.clear(playlist.id)
        syncM3uFile(playlist)
    }

    override suspend fun renamePlaylist(
        playlist: Playlist,
        name: String
    ) = playlistDataDao.update(
        PlaylistData(
            id = playlist.id,
            name = name,
            externalId = playlist.externalId,
            mediaProviderType = playlist.mediaProvider,
            sortOrder = playlist.sortOrder,
            sortDescending = playlist.sortDescending
        )
    )

    override suspend fun updatePlaylistSortOder(
        playlist: Playlist,
        sortOrder: PlaylistSongSortOrder,
        sortDescending: Boolean
    ) {
        playlistDataDao.update(
            PlaylistData(
                id = playlist.id,
                name = playlist.name,
                externalId = playlist.externalId,
                mediaProviderType = playlist.mediaProvider,
                sortOrder = sortOrder,
                sortDescending = sortDescending
            )
        )
    }

    override suspend fun updatePlaylistSongsSortOder(
        playlist: Playlist,
        playlistSongs: List<PlaylistSong>
    ) {
        playlistSongJoinDao.updateSortOrder(
            playlistSongs.map { playlistSong ->
                PlaylistSongJoin(
                    playlistId = playlist.id,
                    songId = playlistSong.song.id,
                    sortOrder = playlistSong.sortOrder
                ).apply {
                    id = playlistSong.id
                }
            }
        )
        syncM3uFile(playlist)
    }

    override suspend fun updatePlaylistMediaProviderType(
        playlist: Playlist,
        mediaProviderType: MediaProviderType
    ) {
        playlistDataDao.update(
            PlaylistData(
                id = playlist.id,
                name = playlist.name,
                sortOrder = playlist.sortOrder,
                sortDescending = playlist.sortDescending,
                mediaProviderType = mediaProviderType,
                externalId = playlist.externalId
            )
        )
    }

    override suspend fun updatePlaylistExternalId(
        playlist: Playlist,
        externalId: String?
    ) {
        playlistDataDao.update(
            PlaylistData(
                id = playlist.id,
                name = playlist.name,
                sortOrder = playlist.sortOrder,
                sortDescending = playlist.sortDescending,
                mediaProviderType = playlist.mediaProvider,
                externalId = externalId
            )
        )
    }

    /**
     * Rewrites the source .m3u file for an m3u-imported [playlist] after its songs change, so an
     * external player sees the same edit. Best-effort: the file may have moved or lost its SAF
     * grant since import, so failures are logged rather than surfaced - the in-app playlist is
     * still the source of truth.
     */
    private suspend fun syncM3uFile(playlist: Playlist) {
        if (!playlist.isM3uSynced()) {
            return
        }
        val uri = Uri.parse(playlist.externalId)
        try {
            val songs = getSongsForPlaylist(playlist).firstOrNull().orEmpty().map { it.song }
            val outputStream = context.contentResolver.openOutputStream(uri, "wt")
            if (outputStream == null) {
                Timber.w("Could not open output stream to sync m3u file for playlist '${playlist.name}' at $uri")
                return
            }
            outputStream.use { it.write(m3uWriter.write(songs).toByteArray(Charsets.UTF_8)) }
        } catch (e: IOException) {
            Timber.e(e, "Failed to sync m3u file for playlist '${playlist.name}' at $uri")
        } catch (e: SecurityException) {
            Timber.e(e, "Failed to sync m3u file for playlist '${playlist.name}' at $uri (permission denied)")
        }
    }
}

/**
 * The songs that can be saved to a playlist. A file opened from another app that isn't in the library (e.g. from
 * the queue) has no song row for a playlist to refer to, so it's left out rather than failing the whole write.
 */
private fun List<Song>.inLibrary(): List<Song> = filter { song -> song.isInLibrary }
