package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.smartplaylist.SmartRules
import com.simplecityapps.shuttle.smartplaylist.SmartRulesContext
import com.simplecityapps.shuttle.smartplaylist.sortAndLimit
import com.simplecityapps.shuttle.smartplaylist.usesFavourites
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The songs a user smart playlist's [SmartRules] pick from the library (excluded songs aside), sorted and limited, and
 * again whenever the library (or, for a rule on them, the favourites) changes. Null until the library has loaded.
 * [seed] fixes a random order across those emissions.
 */
class EvaluateSmartPlaylist @Inject constructor(
    private val songRepository: SongRepository,
    private val observeFavouriteSongIds: ObserveFavouriteSongIds,
) {
    operator fun invoke(
        rules: SmartRules,
        seed: Long = Random.nextLong(),
    ): Flow<List<Song>?> {
        val favouriteSongIds = if (rules.usesFavourites) observeFavouriteSongIds() else flowOf(emptySet())
        return favouriteSongIds
            .flatMapLatest { ids -> songRepository.getSongs(SongQuery.Rules(rules, SmartRulesContext(favouriteSongIds = ids))) }
            .map { songs -> songs?.let { rules.sortAndLimit(it, seed) } }
    }
}
