package com.simplecityapps.shuttle.repository

import com.simplecityapps.shuttle.model.Genre
import kotlinx.coroutines.flow.map

class ArtistRepository(
    songRepository: SongRepository
) {

    val artists = songRepository
        .songs
        .map { songs ->
            songs
                .flatMap { song -> song.artists }
                .distinct()
                .sorted()
                .map { genreString ->
                    Genre(
                        name = genreString,
                        songs = songs.filter { song -> song.genres.contains(genreString) })
                }
        }
}