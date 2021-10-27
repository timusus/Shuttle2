package com.simplecityapps.shuttle.repository

import com.simplecityapps.shuttle.model.Genre
import kotlinx.coroutines.flow.map

class GenreRepository(
    songRepository: SongRepository
) {

    val genres = songRepository
        .songs
        .map { songs ->
            songs
                .flatMap { song -> song.genres }
                .distinct()
                .sorted()
                .map { genreString ->
                    Genre(
                        name = genreString,
                        songs = songs.filter { song -> song.genres.contains(genreString) }
                    )
                }
        }
}