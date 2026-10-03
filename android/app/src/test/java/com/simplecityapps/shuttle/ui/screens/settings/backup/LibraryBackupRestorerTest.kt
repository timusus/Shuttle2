package com.simplecityapps.shuttle.ui.screens.settings.backup

import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.localmediaprovider.local.data.room.dao.SongStatsRestore
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LibraryBackupRestorerTest {
    /** A [FakePlaylistRepository] whose created playlists show up in later lookups, like the real one's. */
    private class StatefulPlaylistRepository : FakePlaylistRepository() {
        override suspend fun createPlaylist(
            name: String,
            mediaProviderType: MediaProviderType,
            songs: List<Song>?,
            externalId: String?
        ): Playlist {
            val playlist = super.createPlaylist(name, mediaProviderType, songs, externalId).copy(mediaProvider = mediaProviderType, externalId = externalId)
            setPlaylists(currentPlaylists + playlist)
            setSongsForPlaylist(playlist, songs.orEmpty())
            return playlist
        }
    }

    private val playlists = StatefulPlaylistRepository()
    private val written = mutableListOf<List<SongStatsRestore>>()
    private val restorer = LibraryBackupRestorer(playlists) { written += it }

    private fun song(id: Long) = createSong(id = id, path = "/Music/$id.mp3", name = "Song $id", lastPlayed = null, lastCompleted = null)

    private fun identity(song: Song) = SongIdentity(provider = song.mediaProvider.name, path = song.path, title = song.name, album = song.album, artist = LibraryBackupMatcher.fingerprintArtist(song), duration = song.duration)

    private fun backupOf(
        library: List<Song>,
        vararg lists: BackedUpPlaylist
    ) = LibraryBackup(exportedAt = 0, songs = library.map { BackedUpSong(identity(it), playCount = 5) }, playlists = lists.toList())

    @Test
    fun `stats are written in one batch and only for songs that change`() = runTest {
        val changed = song(1)
        val same = song(2).copy(playCount = 9)
        val report = restorer.restore(backupOf(listOf(changed, same)), listOf(changed, same))

        written.size shouldBe 1
        written.single().map { it.song.id } shouldBe listOf(1L)
        report.statsWritten shouldBe 1
        report.songsUnmatched shouldBe 0
    }

    @Test
    fun `unmatched songs are counted`() = runTest {
        val report = restorer.restore(backupOf(listOf(song(1), song(2))), listOf(song(1)))
        report.songsMatched shouldBe 1
        report.songsUnmatched shouldBe 1
    }

    @Test
    fun `an externalId miss falls back to the playlist name`() = runTest {
        val s = song(1)
        val existing = createPlaylist(id = 7, name = "Mix")
        playlists.setPlaylists(listOf(existing))
        val backup = backupOf(listOf(s), BackedUpPlaylist("mix", "Shuttle", externalId = "gone", members = listOf(identity(s))))

        restorer.restore(backup, listOf(s))

        playlists.created shouldBe emptyList()
        playlists.addedToPlaylist.map { it.first.id } shouldBe listOf(7L)
    }

    @Test
    fun `a new playlist gets its sort order restored`() = runTest {
        val s = song(1)
        val backup = backupOf(listOf(s), BackedUpPlaylist("New", "Shuttle", sortOrder = "SongName", sortDescending = true, members = listOf(identity(s))))

        restorer.restore(backup, listOf(s))

        val created = playlists.currentPlaylists.single()
        created.sortOrder shouldBe PlaylistSongSortOrder.SongName
        created.sortDescending shouldBe true
    }

    @Test
    fun `an existing playlist keeps its songs and gains the missing ones`() = runTest {
        val a = song(1)
        val b = song(2)
        val onDevice = song(3)
        val existing = createPlaylist(id = 7, name = "Mix")
        playlists.setPlaylists(listOf(existing))
        playlists.setSongsForPlaylist(existing, listOf(onDevice, a))
        val backup = backupOf(listOf(a, b), BackedUpPlaylist("Mix", "Shuttle", members = listOf(identity(a), identity(b))))

        restorer.restore(backup, listOf(a, b, onDevice))

        playlists.addedToPlaylist.single().second shouldBe listOf(b)
        playlists.removedFromPlaylist shouldBe emptyList()
    }

    @Test
    fun `restoring twice creates no duplicate playlists or members`() = runTest {
        val a = song(1)
        val b = song(2)
        val backup = backupOf(listOf(a, b), BackedUpPlaylist("Mix", "Shuttle", members = listOf(identity(a), identity(b))))

        restorer.restore(backup, listOf(a, b))
        restorer.restore(backup, listOf(a, b))

        playlists.created.size shouldBe 1
        playlists.addedToPlaylist shouldBe emptyList()
        playlists.currentPlaylists.size shouldBe 1
    }

    @Test
    fun `a playlist with no resolvable members is reported, not created`() = runTest {
        val a = song(1)
        val backup = backupOf(emptyList(), BackedUpPlaylist("Lost", "Shuttle", members = listOf(identity(a))))
        val report = restorer.restore(backup, emptyList())
        report.playlistsUnresolved shouldBe listOf("Lost")
        playlists.created shouldBe emptyList()
    }
}
