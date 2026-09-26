package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.smartplaylist.SmartRules
import com.simplecityapps.shuttle.smartplaylist.sortAndLimit
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The songs a user smart playlist's [SmartRules] pick from the library (excluded songs aside), sorted and limited, and
 * again whenever the library changes. Null until the library has loaded. [seed] fixes a random order across those
 * emissions.
 */
class EvaluateSmartPlaylist @Inject constructor(
    private val songRepository: SongRepository,
) {
    operator fun invoke(
        rules: SmartRules,
        seed: Long = Random.nextLong(),
    ): Flow<List<Song>?> = songRepository.getSongs(SongQuery.Rules(rules))
        .map { songs -> songs?.let { rules.sortAndLimit(it, seed) } }
}
