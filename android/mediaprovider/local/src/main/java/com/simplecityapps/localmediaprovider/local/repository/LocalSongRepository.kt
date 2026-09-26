package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.SongDataDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.toSong
import com.simplecityapps.localmediaprovider.local.data.room.entity.toSongData
import com.simplecityapps.localmediaprovider.local.data.room.entity.toSongDataUpdate
import com.simplecityapps.mediaprovider.SongPathRemap
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.mediaprovider.repository.songs.comparator
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import timber.log.Timber

class LocalSongRepository(
    val scope: CoroutineScope,
    private val songDataDao: SongDataDao
) : SongRepository {
    private val songsRelay: StateFlow<List<Song>?> by lazy {
        songDataDao
            .getAll()
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Lazily, null)
    }

    /**
     * Songs by id (a restored queue, a song a controller names) are read by id, so the cost is bounded by how many are
     * asked for rather than by the library, and come in no particular order, as their callers look them up by id;
     * every other query filters the whole library, in the query's sort order.
     */
    override fun getSongs(query: SongQuery): Flow<List<Song>?> {
        val songs: Flow<List<Song>?> =
            if (query is SongQuery.SongIds) {
                songDataDao.getByIds(query.songIds).flowOn(Dispatchers.IO)
            } else {
                songsRelay.map { songs -> songs?.filter(query.predicate)?.sortedWith(query.sortOrder.comparator) }
            }
        return songs.map { result -> result?.matching(query) }
    }

    /** Reads the database directly rather than the shared song list, whose requery after a write can take a while for a large library. */
    override suspend fun loadSongs(query: SongQuery): List<Song> = withContext(Dispatchers.IO) {
        songDataDao.get()
            .map { songData -> songData.toSong() }
            .filter(query.predicate)
            .sortedWith(query.sortOrder.comparator)
            .matching(query)
    }

    private fun List<Song>.matching(query: SongQuery): List<Song> = this
        .filter { song -> query.includeExcluded || !song.blacklisted }
        .filter { song -> query.providerType == null || song.mediaProvider == query.providerType }

    override suspend fun insert(
        songs: List<Song>,
        mediaProviderType: MediaProviderType
    ) {
        songDataDao.insert(songs.toSongData(mediaProviderType))
    }

    override suspend fun update(songs: List<Song>) {
        songDataDao.update(songs.toSongDataUpdate())
    }

    override suspend fun update(song: Song): Int = songDataDao.update(song.toSongDataUpdate())

    override suspend fun remove(song: Song) {
        Timber.v("Deleting song")
        songDataDao.delete(song.id)
    }

    override suspend fun removeAll(mediaProviderType: MediaProviderType) {
        songDataDao.deleteAll(mediaProviderType)
    }

    override suspend fun insertUpdateAndDelete(
        inserts: List<Song>,
        updates: List<Song>,
        deletes: List<Song>,
        mediaProviderType: MediaProviderType
    ): Triple<Int, Int, Int> = songDataDao.insertUpdateAndDelete(inserts.toSongData(mediaProviderType), updates.toSongDataUpdate(), deletes.toSongData(mediaProviderType))

    override suspend fun remapPaths(
        remaps: List<SongPathRemap>,
        mediaProviderType: MediaProviderType
    ): List<SongPathRemap> = songDataDao.remapPaths(remaps, mediaProviderType)

    override suspend fun incrementPlayCount(song: Song) {
        Timber.v("Incrementing play count for song: ${song.name}")
        songDataDao.incrementPlayCount(song.id)
    }

    override suspend fun setPlaybackPosition(
        song: Song,
        playbackPosition: Int
    ) {
        Timber.v("Setting playback position to $playbackPosition for song: ${song.name}")
        songDataDao.updatePlaybackPosition(song.id, playbackPosition)
    }

    override suspend fun setExcluded(
        songs: List<Song>,
        excluded: Boolean
    ) {
        val count = songDataDao.setExcluded(songs.map { it.id }, excluded)
        Timber.v("$count song(s) excluded")
    }

    override suspend fun clearExcludeList() {
        Timber.v("Clearing excluded")
        songDataDao.clearExcludeList()
    }
}
