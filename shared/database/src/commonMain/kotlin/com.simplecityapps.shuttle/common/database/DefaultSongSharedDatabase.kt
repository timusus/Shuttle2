package com.simplecityapps.shuttle.common.database

import com.simplecityapps.shuttle.database.SongDatabase
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import comsimplecityappsshuttle.common.database.SongEntity
import kotlinx.coroutines.flow.Flow

class DefaultSongSharedDatabase(private val driver: SqlDriver) : SongSharedDatabase {

    private val database = SongDatabase(driver)

    override fun observeAll(): Flow<List<SongEntity>> {
        return database.songDatabaseQueries.selectAll().asFlow().mapToList()
    }

    override suspend fun insertOrUpdate(songs: List<SongEntity>) {
        database.songDatabaseQueries.transaction {
            songs.forEach { songEntity ->
                database.songDatabaseQueries.insert(
                    id = null,
                    path = songEntity.path,
                    name = songEntity.name,
                    artists = songEntity.artists,
                    album = songEntity.album,
                    albumArtist = songEntity.albumArtist,
                    trackNumber = songEntity.trackNumber, discNumber = songEntity.discNumber,
                    duration = songEntity.duration,
                    date = songEntity.date,
                    genres = songEntity.genres,
                    size = songEntity.size,
                    mimeType = songEntity.mimeType,
                    playbackPosition = songEntity.playbackPosition,
                    playCount = songEntity.playCount,
                    lastPlayed = songEntity.lastPlayed,
                    lastCompleted = songEntity.lastCompleted,
                    excluded = songEntity.excluded,
                    mediaProvider = songEntity.mediaProvider,
                    externalId = songEntity.externalId,
                    replayGainTrack = songEntity.replayGainTrack,
                    replayGainAlbum = songEntity.replayGainAlbum,
                    lyrics = songEntity.lyrics,
                    grouping = songEntity.grouping,
                    composer = songEntity.composer,
                    bitRate = songEntity.bitRate,
                    sampleRate = songEntity.sampleRate,
                    channelCount = songEntity.channelCount,
                    dateModified = songEntity.dateModified,
                    dateAdded = songEntity.dateAdded
                )
                database.songDatabaseQueries.update(
                    name = songEntity.name,
                    artists = songEntity.artists,
                    album = songEntity.album,
                    albumArtist = songEntity.albumArtist,
                    trackNumber = songEntity.trackNumber,
                    discNumber = songEntity.discNumber,
                    duration = songEntity.duration,
                    date = songEntity.date,
                    genres = songEntity.genres,
                    size = songEntity.size,
                    mimeType = songEntity.mimeType,
                    externalId = songEntity.externalId,
                    replayGainTrack = songEntity.replayGainTrack,
                    replayGainAlbum = songEntity.replayGainAlbum,
                    lyrics = songEntity.lyrics,
                    grouping = songEntity.grouping,
                    composer = songEntity.composer,
                    bitRate = songEntity.bitRate,
                    sampleRate = songEntity.sampleRate,
                    channelCount = songEntity.channelCount,
                    dateModified = songEntity.dateModified,
                    path = songEntity.path
                )
            }
        }
    }
}