package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.SongDataDao
import com.simplecityapps.localmediaprovider.local.data.room.entity.toSongData
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

    private val songsRelay: Flow<List<Song>> by lazy {
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

    override suspend fun insert(songs: List<Song>) {
        songDataDao.insertAll(songs.toSongData())
    }

    override fun getSongs(query: SongQuery): Flow<List<Song>> {
        return songsRelay
            .map { songs ->
                var result = songs

                if (!query.includeBlacklisted) {
                    result = songs.filterNot { it.blacklisted }
                }

                result = result.filter(query.predicate)

                query.sortOrder?.let { sortOrder ->
                    result = result.sortedWith(sortOrder.comparator)
                }

                result
            }
    }

    override suspend fun incrementPlayCount(song: Song) {
        Timber.v("Incrementing play count for song: ${song.name}")
        songDataDao.incrementPlayCount(song.id)
    }

    override suspend fun setPlaybackPosition(song: Song, playbackPosition: Int) {
        Timber.v("Setting playback position to $playbackPosition for song: ${song.name}")
        songDataDao.updatePlaybackPosition(song.id, playbackPosition)
    }

    override suspend fun setBlacklisted(songs: List<Song>, blacklisted: Boolean) {
        val count = songDataDao.setBlacklisted(songs.map { it.id }, blacklisted)
        Timber.v("$count song(s) blacklisted")
    }

    override suspend fun clearBlacklist() {
        Timber.v("Clearing blacklist")
        songDataDao.clearBlacklist()
    }

    override suspend fun removeSong(song: Song) {
        Timber.v("Deleting song")
        songDataDao.delete(song.id)
    }
}