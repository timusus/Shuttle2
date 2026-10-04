package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.mediaprovider.repository.songs.SongStatsRestore
import com.simplecityapps.mediaprovider.repository.songs.SongStatsStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

@Inject
@ContributesBinding(AppScope::class)
class RoomSongStatsStore(
    private val database: MediaDatabase
) : SongStatsStore {
    override suspend fun restoreStats(restores: List<SongStatsRestore>) {
        database.songDataDao().restoreStats(restores)
    }
}
