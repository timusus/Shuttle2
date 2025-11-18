package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.SongDataDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.toFtsQuery
import com.simplecityapps.localmediaprovider.local.data.room.dao.toSong
import com.simplecityapps.localmediaprovider.local.data.room.entity.toSongData
import com.simplecityapps.localmediaprovider.local.data.room.entity.toSongDataUpdate
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

    override fun getSongs(query: SongQuery): Flow<List<Song>?> = songsRelay
        .map { songs ->
            var result = songs

            if (!query.includeExcluded) {
                result = songs?.filterNot { it.blacklisted }
            }

            query.providerType?.let { providerType ->
                result = songs?.filter { song -> song.mediaProvider == providerType }
            }

            result
                ?.filter(query.predicate)
                ?.sortedWith(query.sortOrder.comparator)
        }

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

    override suspend fun searchSongsFts(query: String, limit: Int): List<Song> {
        val ftsQuery = query.toFtsQuery()
        val ftsResults = songDataDao.searchSongsFts(ftsQuery, limit).map { it.toSong() }

        // If FTS returns no results and query is long enough, fall back to full scan
        // This allows fuzzy matching on typos that FTS misses
        if (ftsResults.isEmpty() && query.length >= 3) {
            Timber.d("FTS returned zero results for '$query', falling back to full scan for fuzzy matching")
            // Get all non-blacklisted songs, limit to 5000 for performance
            return songsRelay.value
                ?.filterNot { it.blacklisted }
                ?.take(5000)
                ?: emptyList()
        }

        return ftsResults
    }
}
