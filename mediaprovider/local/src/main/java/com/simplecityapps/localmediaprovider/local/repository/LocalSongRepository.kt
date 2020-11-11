package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.SongDataDao
import com.simplecityapps.localmediaprovider.local.data.room.entity.toSongData
import com.simplecityapps.localmediaprovider.local.data.room.entity.toSongDataUpdate
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber

class LocalSongRepository(
    private val songDataDao: SongDataDao
) : SongRepository {

    private val songsRelay: Flow<List<Song>> = run {
        val time = System.currentTimeMillis()
        Timber.i("Initialising songs relay")
        ConflatedBroadcastChannel<List<Song>?>(null)
            .apply {
                CoroutineScope(Dispatchers.IO)
                    .launch {
                        songDataDao
                            .getAll()
                            .collect { songs ->
                                send(songs)
                            }
                    }
            }
            .asFlow()
            .filterNotNull()
            .flowOn(Dispatchers.IO)
    }

    override fun getSongs(query: SongQuery): Flow<List<Song>> {
        return songsRelay
            .map { songs ->
                var result = songs

                if (!query.includeExcluded) {
                    result = songs.filterNot { it.blacklisted }
                }

                result
                    .filter(query.predicate)
                    .sortedWith(query.sortOrder.comparator)
            }
    }

    override suspend fun insert(songs: List<Song>) {
        songDataDao.insert(songs.toSongData())
    }

    override suspend fun update(songs: List<Song>) {
        songDataDao.update(songs.toSongDataUpdate())
    }

    override suspend fun update(song: Song): Int {
        return songDataDao.update(song.toSongData())
    }

    override suspend fun remove(songs: List<Song>) {
        songDataDao.delete(songs.toSongData())
    }

    override suspend fun remove(song: Song) {
        Timber.v("Deleting song")
        songDataDao.delete(song.id)
    }

    override suspend fun insertUpdateAndDelete(inserts: List<Song>, updates: List<Song>, deletes: List<Song>): Triple<Int, Int, Int> {
        return songDataDao.insertUpdateAndDelete(inserts.toSongData(), updates.toSongDataUpdate(), deletes.toSongData())
    }

    override suspend fun incrementPlayCount(song: Song) {
        Timber.v("Incrementing play count for song: ${song.name}")
        songDataDao.incrementPlayCount(song.id)
    }

    override suspend fun setPlaybackPosition(song: Song, playbackPosition: Int) {
        Timber.v("Setting playback position to $playbackPosition for song: ${song.name}")
        songDataDao.updatePlaybackPosition(song.id, playbackPosition)
    }

    override suspend fun setExcluded(songs: List<Song>, excluded: Boolean) {
        val count = songDataDao.setExcluded(songs.map { it.id }, excluded)
        Timber.v("$count song(s) excluded")
    }

    override suspend fun clearExcludeList() {
        Timber.v("Clearing excluded")
        songDataDao.clearExcludeList()
    }
}