package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.SmartPlaylistDao
import com.simplecityapps.localmediaprovider.local.data.room.entity.SmartPlaylistData
import com.simplecityapps.mediaprovider.repository.smartplaylists.SmartPlaylistRepository
import com.simplecityapps.shuttle.model.UserSmartPlaylist
import com.simplecityapps.shuttle.smartplaylist.SmartRules
import com.simplecityapps.shuttle.smartplaylist.SmartRulesCodec
import java.util.Date
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber

class LocalSmartPlaylistRepository(
    private val smartPlaylistDao: SmartPlaylistDao,
    private val clock: Clock = Clock.System
) : SmartPlaylistRepository {
    override fun getSmartPlaylists(): Flow<List<UserSmartPlaylist>> = smartPlaylistDao.getAll().map { list -> list.mapNotNull { data -> data.toSmartPlaylist() } }

    override fun getSmartPlaylist(id: Long): Flow<UserSmartPlaylist?> = smartPlaylistDao.get(id).map { data -> data?.toSmartPlaylist() }

    override suspend fun create(
        name: String,
        rules: SmartRules
    ): UserSmartPlaylist {
        // Truncated to the millisecond, as it's stored
        val createdAt = Instant.fromEpochMilliseconds(clock.now().toEpochMilliseconds())
        val id = smartPlaylistDao.insert(SmartPlaylistData(name = name, rulesJson = SmartRulesCodec.encode(rules), createdAt = Date(createdAt.toEpochMilliseconds())))
        return UserSmartPlaylist(id = id, name = name, rules = rules, createdAt = createdAt)
    }

    override suspend fun update(smartPlaylist: UserSmartPlaylist) {
        smartPlaylistDao.update(smartPlaylist.id, smartPlaylist.name, SmartRulesCodec.encode(smartPlaylist.rules))
    }

    override suspend fun delete(id: Long) {
        smartPlaylistDao.delete(id)
    }

    private fun SmartPlaylistData.toSmartPlaylist(): UserSmartPlaylist? {
        val rules =
            try {
                SmartRulesCodec.decode(rulesJson)
            } catch (e: IllegalArgumentException) {
                Timber.w(e, "Leaving out smart playlist $id: its rules don't decode")
                return null
            }
        return UserSmartPlaylist(id = id, name = name, rules = rules, createdAt = Instant.fromEpochMilliseconds(createdAt.time))
    }
}
