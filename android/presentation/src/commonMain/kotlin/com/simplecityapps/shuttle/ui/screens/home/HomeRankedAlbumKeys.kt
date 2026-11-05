package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.SmartPlaylistId
import com.simplecityapps.shuttle.ui.actions.RankedAlbumKeys
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/** The Rediscover and Heavy rotation playlists list what Home's shelves of the same name rank, so the two never disagree. */
@ContributesBinding(AppScope::class)
class HomeRankedAlbumKeys @Inject constructor(
    private val rediscover: Rediscover,
    private val heavyRotation: HeavyRotation,
    private val time: HomeTime,
) : RankedAlbumKeys {
    override suspend fun invoke(id: SmartPlaylistId): List<AlbumGroupKey> {
        val now = time.clock.now()
        return when (id) {
            SmartPlaylistId.Rediscover -> rediscover(now).filterIsInstance<HomeItem.AlbumItem>().mapNotNull { it.album.groupKey }

            // An artist stands for the albums of theirs played in the window, at the artist's rank; an album already listed is not listed twice
            SmartPlaylistId.HeavyRotation -> heavyRotation(now).flatMap { it.playedAlbums }

            else -> emptyList()
        }.distinct()
    }
}
