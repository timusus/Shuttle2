package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull

/**
 * The songs matching [SongQuery], the whole library by default; re-emits as they change. Emits
 * nothing until the library has loaded, rather than the repository's interim null.
 */
class ObserveSongs @Inject constructor(
    private val songRepository: SongRepository,
) {
    operator fun invoke(query: SongQuery = SongQuery.All()): Flow<List<Song>> = songRepository.getSongs(query).filterNotNull()
}
