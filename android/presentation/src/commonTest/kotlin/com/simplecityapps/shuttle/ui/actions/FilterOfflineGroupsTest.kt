package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createGenre
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeOfflineSongs
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class FilterOfflineGroupsTest {
    private val offline = FakeOfflineSongs()
    private val library = listOf(
        createSong(id = 1, album = "Local LP", albumArtist = "Homebody", path = "/music/1.mp3").copy(genres = listOf("Folk")),
        createSong(id = 2, album = "Saved LP", albumArtist = "Cached", path = "remote://2", mediaProvider = MediaProviderType.Jellyfin).copy(genres = listOf("Jazz")),
        createSong(id = 3, album = "Streamed LP", albumArtist = "Streamer", path = "remote://3", mediaProvider = MediaProviderType.Jellyfin).copy(genres = listOf("Techno")),
    )
    private val songRepository = FakeSongRepository().apply { setSongs(library) }
    private val filter = offline.groups(songRepository)

    // Keyed the way the repositories key them: from the songs.
    private val albums = library.map { createAlbum(name = it.album!!, albumArtist = it.albumArtist, groupKey = it.albumGroupKey) }
    private val artists = library.map { createAlbumArtist(name = it.albumArtist!!, groupKey = it.albumArtistGroupKey) }
    private val genres = listOf("Folk", "Jazz", "Techno").map { createGenre(name = it) }

    private fun goOffline() {
        offline.offlineMode.value = true
        offline.statuses.value = DownloadStatuses(mapOf("remote://2" to SongDownloadStatus.Downloaded))
    }

    @Test
    fun `with Offline mode off every group is listed`() = runTest {
        filter.albums(flowOf(albums)).first().map { it.name } shouldBe listOf("Local LP", "Saved LP", "Streamed LP")
        filter.albumArtists(flowOf(artists)).first().map { it.name } shouldBe listOf("Homebody", "Cached", "Streamer")
        filter.genres(flowOf(genres)).first().map { it.name } shouldBe listOf("Folk", "Jazz", "Techno")
    }

    @Test
    fun `with Offline mode on groups with no playable song are hidden`() = runTest {
        goOffline()

        filter.albums(flowOf(albums)).first().map { it.name } shouldBe listOf("Local LP", "Saved LP")
        filter.albumArtists(flowOf(artists)).first().map { it.name } shouldBe listOf("Homebody", "Cached")
        filter.genres(flowOf(genres)).first().map { it.name } shouldBe listOf("Folk", "Jazz")
    }
}
