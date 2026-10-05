package com.simplecityapps.shuttle.downloads

import com.simplecityapps.mediaprovider.SongPathRemap
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

internal fun testSong(
    path: String,
    mimeType: String = "audio/flac",
    audioCodec: String? = null,
    mediaProvider: MediaProviderType = MediaProviderType.Jellyfin
) = Song(
    id = 0,
    name = "Song",
    albumArtist = "Artist",
    artists = listOf("Artist"),
    album = "Album",
    track = null,
    disc = null,
    duration = 180_000,
    date = null,
    genres = emptyList(),
    path = path,
    size = 0,
    mimeType = mimeType,
    lastModified = null,
    lastPlayed = null,
    lastCompleted = null,
    playCount = 0,
    playbackPosition = 0,
    blacklisted = false,
    externalId = null,
    mediaProvider = mediaProvider,
    lyrics = null,
    grouping = null,
    bitRate = null,
    bitDepth = null,
    sampleRate = null,
    channelCount = null,
    audioCodec = audioCodec
)

/** A [SongRepository] holding [songs], of which tests read only [loadProviderSongs]. */
internal class FakeSongRepository(private val songs: List<Song>) : SongRepository {
    override fun getSongs(query: SongQuery): Flow<List<Song>?> = flowOf(songs)

    override fun countSongs(): Flow<Int> = flowOf(songs.size)

    override suspend fun loadLyrics(songId: Long): String? = null

    override suspend fun loadProviderSongs(mediaProviderType: MediaProviderType): List<Song> = songs.filter { it.mediaProvider == mediaProviderType }

    override val updatedSongIds: Flow<Set<Long>> = emptyFlow()

    override suspend fun insert(songs: List<Song>, mediaProviderType: MediaProviderType) = error("not called")

    override suspend fun update(song: Song): Int = error("not called")

    override suspend fun update(songs: List<Song>) = error("not called")

    override suspend fun remove(song: Song) = error("not called")

    override suspend fun removeAll(mediaProviderType: MediaProviderType) = error("not called")

    override suspend fun insertUpdateAndDelete(inserts: List<Song>, updates: List<Song>, deletes: List<Song>, mediaProviderType: MediaProviderType): Triple<Int, Int, Int> = error("not called")

    override suspend fun remapPaths(remaps: List<SongPathRemap>, mediaProviderType: MediaProviderType): List<SongPathRemap> = error("not called")

    override suspend fun setPlaybackPosition(song: Song, playbackPosition: Int) = error("not called")

    override suspend fun recordPlayedThrough(song: Song) = error("not called")

    override suspend fun setExcluded(songs: List<Song>, excluded: Boolean) = error("not called")

    override suspend fun clearExcludeList() = error("not called")

    override suspend fun setFavourite(songs: List<Song>, favourite: Boolean) = error("not called")
}
