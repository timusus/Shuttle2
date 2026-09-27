package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow

/** The songs in [genre] matching [songQuery], the whole genre by default; re-emits as they change. */
@Inject
class ObserveSongsForGenre(
    private val genreRepository: GenreRepository,
) {
    operator fun invoke(genre: String, songQuery: SongQuery = SongQuery.All()): Flow<List<Song>> = genreRepository.getSongsForGenre(genre, songQuery)
}
