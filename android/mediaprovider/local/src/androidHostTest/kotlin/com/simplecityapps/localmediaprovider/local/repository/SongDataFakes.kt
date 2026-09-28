package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.SongDataDao
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.entity.PendingFavouriteData
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongDataUpdate
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongIdentityData
import com.simplecityapps.shuttle.model.AlbumIndex
import com.simplecityapps.shuttle.model.AlbumIndexProvider
import com.simplecityapps.shuttle.model.MediaProviderType
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

internal fun createSongData(
    album: String,
    albumArtist: String = "Artist",
    track: Int = 1,
    playCount: Int = 0
) = SongData(
    name = "Song",
    track = track,
    disc = 1,
    duration = 180_000,
    year = null,
    genres = emptyList(),
    path = "/music/$albumArtist/$album/$track.mp3",
    albumArtist = albumArtist,
    artists = listOf("Artist"),
    album = album,
    size = 0,
    mimeType = "audio/mpeg",
    lastModified = Instant.fromEpochMilliseconds(0),
    lyrics = null,
    grouping = null,
    bitRate = null,
    bitDepth = null,
    sampleRate = null,
    channelCount = null,
    playCount = playCount
)

/** An index built fresh from [database] each time it's asked for, for tests that don't exercise its caching. */
internal fun freshAlbumIndex(database: MediaDatabase) = AlbumIndexProvider { AlbumIndex(database.songDataDao().identityData().map { it.toTags() }) }

/** Serves [songs] as the library; anything else throws. */
internal class FakeSongDataDao(private val songs: Flow<List<SongData>>) : SongDataDao() {
    override fun getAllSongData(): Flow<List<SongData>> = songs

    override fun getSongDataByIds(ids: List<Long>): Flow<List<SongData>> = throw NotImplementedError()

    override fun getSongIdsForGenre(genre: String): Flow<List<Long>> = throw NotImplementedError()

    override suspend fun identityData(): List<SongIdentityData> = throw NotImplementedError()

    override suspend fun get(): List<SongData> = throw NotImplementedError()

    override suspend fun songDataByIds(ids: List<Long>): List<SongData> = throw NotImplementedError()

    override suspend fun insert(songData: List<SongData>): List<Long> = throw NotImplementedError()

    override suspend fun update(songData: List<SongDataUpdate>): Int = throw NotImplementedError()

    override suspend fun update(songData: SongDataUpdate): Int = throw NotImplementedError()

    override suspend fun delete(songData: List<SongData>): Int = throw NotImplementedError()

    override suspend fun idForPath(
        path: String,
        mediaProvider: MediaProviderType
    ): Long? = throw NotImplementedError()

    override suspend fun updatePath(
        id: Long,
        path: String
    ): Int = throw NotImplementedError()

    override suspend fun movePlaylistEntries(
        fromSongIds: List<Long>,
        songId: Long
    ) = throw NotImplementedError()

    override suspend fun updatePlaybackPosition(
        id: Long,
        playbackPosition: Int,
        lastPlayed: Instant
    ) = throw NotImplementedError()

    override suspend fun recordPlayedThrough(
        id: Long,
        playbackPosition: Int,
        now: Instant
    ) = throw NotImplementedError()

    override suspend fun setExcluded(
        ids: List<Long>,
        blacklisted: Boolean
    ): Int = throw NotImplementedError()

    override suspend fun clearExcludeList() = throw NotImplementedError()

    override suspend fun keepFavourite(
        fromSongIds: List<Long>,
        songId: Long
    ) = throw NotImplementedError()

    override suspend fun favourite(
        ids: List<Long>,
        now: Instant
    ): Int = throw NotImplementedError()

    override suspend fun favourite(
        id: Long,
        favouritedAt: Instant
    ): Int = throw NotImplementedError()

    override suspend fun unfavourite(ids: List<Long>): Int = throw NotImplementedError()

    override suspend fun enqueuePendingFavourite(pendingFavourite: PendingFavouriteData) = throw NotImplementedError()

    override suspend fun getPendingFavourites(): List<PendingFavouriteData> = throw NotImplementedError()

    override fun getFavouriteIds(): Flow<List<Long>> = throw NotImplementedError()

    override suspend fun deleteAll(mediaProviderType: MediaProviderType) = throw NotImplementedError()

    override suspend fun deleteAll(songData: List<SongData>): Int = throw NotImplementedError()

    override suspend fun delete(id: Long) = throw NotImplementedError()
}
