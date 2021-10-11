package com.simplecityapps.shuttle.common.database

import com.simplecityapps.shuttle.database.SongDatabase
import com.simplecityapps.shuttle.model.SongData
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import comsimplecityappsshuttle.common.database.SongEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

class DefaultSongSharedDatabase(
    driver: SqlDriver
) : SongSharedDatabase {

    private val database = SongDatabase(driver)

    override fun getSongs(): Flow<List<SongEntity>> {
        return database.songDatabaseQueries.selectAll().asFlow().mapToList()
    }

    override suspend fun insertOrUpdate(songs: List<SongData>, insertDate: Instant) {
        database.songDatabaseQueries.transaction {
            songs.forEach { songData ->
                database.songDatabaseQueries.insert(
                    id = null,
                    path = songData.path,
                    name = songData.name,
                    artists = songData.artists.joinToString(";"),
                    album = songData.album,
                    albumArtist = songData.albumArtist,
                    trackNumber = songData.track,
                    discNumber = songData.disc,
                    duration = songData.duration,
                    date = songData.date?.toString(),
                    genres = songData.genres.joinToString(";"),
                    size = songData.size,
                    mimeType = songData.mimeType,
                    playbackPosition = null,
                    playCount = 0,
                    lastPlayed = songData.lastPlayed?.toString(),
                    lastCompleted = songData.lastCompleted?.toString(),
                    excluded = false,
                    mediaProvider = songData.mediaProvider.name,
                    externalId = songData.externalId,
                    replayGainTrack = songData.replayGainTrack,
                    replayGainAlbum = songData.replayGainAlbum,
                    lyrics = songData.lyrics,
                    grouping = songData.grouping,
                    composer = songData.composer,
                    bitRate = songData.bitRate,
                    sampleRate = songData.sampleRate,
                    channelCount = songData.channelCount,
                    dateModified = songData.dateModified?.toString(),
                    dateAdded = insertDate.toString()
                )
                database.songDatabaseQueries.update(
                    name = songData.name,
                    artists = songData.artists.joinToString(";"),
                    album = songData.album,
                    albumArtist = songData.albumArtist,
                    trackNumber = songData.track,
                    discNumber = songData.disc,
                    duration = songData.duration,
                    date = songData.date?.toString(),
                    genres = songData.genres.joinToString(";"),
                    size = songData.size,
                    mimeType = songData.mimeType,
                    externalId = songData.externalId,
                    replayGainTrack = songData.replayGainTrack,
                    replayGainAlbum = songData.replayGainAlbum,
                    lyrics = songData.lyrics,
                    grouping = songData.grouping,
                    composer = songData.composer,
                    bitRate = songData.bitRate,
                    sampleRate = songData.sampleRate,
                    channelCount = songData.channelCount,
                    dateModified = songData.dateModified?.toString(),
                    path = songData.path
                )
            }
        }
    }
}