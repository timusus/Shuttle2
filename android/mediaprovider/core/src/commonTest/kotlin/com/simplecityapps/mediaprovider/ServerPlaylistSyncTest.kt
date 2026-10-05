package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class ServerPlaylistSyncTest {
    private val server = FakeServer()
    private val preferences = GeneralPreferenceManager(InMemoryKeyValueStore())
    private val source = MediaProviderType.Jellyfin.name

    private fun TestScope.sync() = ServerPlaylistSync(setOf(server), preferences, backgroundScope)

    @Test
    fun `edits are kept across a restart and sent in order - updating the songs the server holds`() = runTest {
        server.playlists["p1"] = server.playlist(A)
        preferences.setPlaylistServerSongs(source, mapOf("p1" to listOf(A)))
        server.offline = true
        sync().apply {
            enqueue(MediaProviderType.Jellyfin, PlaylistEdit.Add("p1", listOf(B)))
            enqueue(MediaProviderType.Jellyfin, PlaylistEdit.Remove("p1", listOf(A)))
            enqueue(MediaProviderType.Jellyfin, PlaylistEdit.Add("p1", listOf(C)))
        }
        runCurrent()

        server.offline = false
        val relaunched = sync()
        relaunched.pendingPlaylistIds(MediaProviderType.Jellyfin) shouldBe setOf("p1")
        relaunched.send(MediaProviderType.Jellyfin)

        server.calls shouldBe listOf("add [$B]", "remove [e0]", "add [$C]")
        server.songs("p1") shouldBe listOf(B, C)
        preferences.playlistServerSongs(source) shouldBe mapOf("p1" to listOf(B, C))
        relaunched.pendingPlaylistIds(MediaProviderType.Jellyfin) shouldBe emptySet()
    }

    @Test
    fun `an edit the server doesn't get stays queued - with those after it`() = runTest {
        server.playlists["p1"] = server.playlist(A)
        server.offline = true
        val sync = sync()

        sync.enqueue(MediaProviderType.Jellyfin, PlaylistEdit.Add("p1", listOf(B)))
        sync.enqueue(MediaProviderType.Jellyfin, PlaylistEdit.Add("p1", listOf(C)))
        runCurrent()

        server.songs("p1") shouldBe listOf(A)
        preferences.pendingPlaylistEdits(source)?.lines()?.size shouldBe 2

        server.offline = false
        sync.send(MediaProviderType.Jellyfin)
        server.songs("p1") shouldBe listOf(A, B, C)
    }

    @Test
    fun `an edit is sent as soon as it's queued`() = runTest {
        server.playlists["p1"] = server.playlist(A)

        sync().enqueue(MediaProviderType.Jellyfin, PlaylistEdit.Add("p1", listOf(B)))
        runCurrent()

        server.songs("p1") shouldBe listOf(A, B)
        preferences.pendingPlaylistEdits(source) shouldBe null
    }

    @Test
    fun `a playlist the server no longer has drops its edits - and only its`() = runTest {
        server.playlists["p2"] = server.playlist(A)
        server.offline = true
        val sync = sync()
        sync.enqueue(MediaProviderType.Jellyfin, PlaylistEdit.Add("gone", listOf(B)))
        sync.enqueue(MediaProviderType.Jellyfin, PlaylistEdit.Add("p2", listOf(B)))
        sync.enqueue(MediaProviderType.Jellyfin, PlaylistEdit.Remove("gone", listOf(A)))
        runCurrent()

        server.offline = false
        sync.send(MediaProviderType.Jellyfin)

        server.calls shouldBe listOf("add [$B]")
        server.songs("p2") shouldBe listOf(A, B)
        sync.pendingPlaylistIds(MediaProviderType.Jellyfin) shouldBe emptySet()
    }

    @Test
    fun `an edit the server refuses is dropped and the rest are sent`() = runTest {
        server.playlists["p1"] = server.playlist(A)
        server.refuse = true
        val sync = sync()
        sync.enqueue(MediaProviderType.Jellyfin, PlaylistEdit.Add("p1", listOf(B)))
        runCurrent()

        server.refuse = false
        sync.enqueue(MediaProviderType.Jellyfin, PlaylistEdit.Add("p1", listOf(C)))
        runCurrent()

        server.songs("p1") shouldBe listOf(A, C)
        sync.pendingPlaylistIds(MediaProviderType.Jellyfin) shouldBe emptySet()
    }

    @Test
    fun `a reorder moves the server's entries into the new order - leaving an entry S2 doesn't name where it is`() = runTest {
        server.playlists["p1"] = server.playlist(A, UNKNOWN, B, C)

        sync().enqueue(MediaProviderType.Jellyfin, PlaylistEdit.Reorder("p1", listOf(C, A, B)))
        runCurrent()

        server.songs("p1") shouldBe listOf(C, UNKNOWN, A, B)
    }

    @Test
    fun `a reorder matches a song's entries by occurrence and skips what's already in place`() = runTest {
        server.playlists["p1"] = server.playlist(A, B, A, C)

        sync().enqueue(MediaProviderType.Jellyfin, PlaylistEdit.Reorder("p1", listOf(A, A, B, C)))
        runCurrent()

        server.songs("p1") shouldBe listOf(A, A, B, C)
        server.calls shouldBe listOf("move e2 1")
    }

    @Test
    fun `removing one entry of a song leaves its other entries - removing every entry takes them all`() = runTest {
        server.playlists["p1"] = server.playlist(A, B, A)
        preferences.setPlaylistServerSongs(source, mapOf("p1" to listOf(A, B, A)))
        val sync = sync()

        sync.enqueue(MediaProviderType.Jellyfin, PlaylistEdit.Remove("p1", listOf(A)))
        runCurrent()
        server.songs("p1") shouldBe listOf(B, A)
        preferences.playlistServerSongs(source) shouldBe mapOf("p1" to listOf(B, A))

        server.playlists["p1"] = server.playlist(A, B, A)
        sync.enqueue(MediaProviderType.Jellyfin, PlaylistEdit.Remove("p1", listOf(A), everyEntry = true))
        runCurrent()
        server.songs("p1") shouldBe listOf(B)
    }

    @Test
    fun `an import runs once the queued edits are sent`() = runTest {
        server.playlists["p1"] = server.playlist(A)
        server.offline = true
        val sync = sync()
        sync.enqueue(MediaProviderType.Jellyfin, PlaylistEdit.Add("p1", listOf(B)))
        runCurrent()

        server.offline = false
        val seen = sync.withEditsSent(MediaProviderType.Jellyfin) { server.songs("p1") to sync.pendingPlaylistIds(MediaProviderType.Jellyfin) }

        seen shouldBe (listOf(A, B) to emptySet())
    }

    @Test
    fun `an edit to a server without a writer isn't queued`() = runTest {
        val sync = sync()
        sync.enqueue(MediaProviderType.Plex, PlaylistEdit.Add("p1", listOf(B)))

        sync.handles(MediaProviderType.Plex) shouldBe false
        sync.pendingPlaylistIds(MediaProviderType.Plex) shouldBe emptySet()
    }

    /** A server's playlists, as entries, edited as a Jellyfin server would edit them: a move puts the entry at its final index. */
    @Test
    fun `a rename is kept across a restart and sent - a name can't break the queue`() = runTest {
        server.playlists["p1"] = server.playlist(A)
        preferences.setPlaylistServerSongs(source, mapOf("p1" to listOf(A)))
        server.offline = true
        sync().enqueue(MediaProviderType.Jellyfin, PlaylistEdit.Rename("p1", "Road\ttrip\nmix"))
        runCurrent()

        server.offline = false
        val relaunched = sync()
        relaunched.pendingPlaylistIds(MediaProviderType.Jellyfin) shouldBe setOf("p1")
        relaunched.send(MediaProviderType.Jellyfin)

        server.names["p1"] shouldBe "Road trip mix"
        preferences.playlistServerSongs(source) shouldBe mapOf("p1" to listOf(A))
        relaunched.pendingPlaylistIds(MediaProviderType.Jellyfin) shouldBe emptySet()
    }

    @Test
    fun `deleting a playlist drops its queued edits and the songs it held on the server`() = runTest {
        server.playlists["p1"] = server.playlist(A)
        server.playlists["p2"] = server.playlist(A)
        preferences.setPlaylistServerSongs(source, mapOf("p1" to listOf(A), "p2" to listOf(A)))
        server.offline = true
        val sync = sync()
        sync.enqueue(MediaProviderType.Jellyfin, PlaylistEdit.Add("p1", listOf(B)))
        sync.enqueue(MediaProviderType.Jellyfin, PlaylistEdit.Add("p2", listOf(B)))
        sync.enqueue(MediaProviderType.Jellyfin, PlaylistEdit.Rename("p1", "Gone soon"))
        sync.enqueue(MediaProviderType.Jellyfin, PlaylistEdit.Delete("p1"))
        runCurrent()
        // Held back from the import until the server has deleted it, so the import can't bring it back
        sync.pendingPlaylistIds(MediaProviderType.Jellyfin) shouldBe setOf("p1", "p2")

        server.offline = false
        sync.send(MediaProviderType.Jellyfin)

        server.calls shouldBe listOf("add [$B]", "delete")
        server.playlists.keys shouldBe setOf("p2")
        preferences.playlistServerSongs(source) shouldBe mapOf("p2" to listOf(A, B))
        sync.pendingPlaylistIds(MediaProviderType.Jellyfin) shouldBe emptySet()
    }

    @Test
    fun `a delete the server refuses is dropped - the next import brings the playlist back`() = runTest {
        server.playlists["p1"] = server.playlist(A)
        preferences.setPlaylistServerSongs(source, mapOf("p1" to listOf(A)))
        server.refuse = true
        val sync = sync()

        sync.enqueue(MediaProviderType.Jellyfin, PlaylistEdit.Delete("p1"))
        runCurrent()

        server.playlists.keys shouldBe setOf("p1")
        preferences.playlistServerSongs(source) shouldBe mapOf("p1" to listOf(A))
        sync.pendingPlaylistIds(MediaProviderType.Jellyfin) shouldBe emptySet()
    }

    private class FakeServer : ServerPlaylistWriter {
        override val type = MediaProviderType.Jellyfin
        val playlists = mutableMapOf<String, MutableList<ServerPlaylistEntry>>()
        val names = mutableMapOf<String, String>()
        val calls = mutableListOf<String>()
        var offline = false
        var refuse = false
        private var nextEntry = 0

        fun playlist(vararg paths: String): MutableList<ServerPlaylistEntry> = paths.mapTo(mutableListOf()) { path -> ServerPlaylistEntry("e${nextEntry++}", path) }

        fun songs(playlistId: String): List<String> = playlists.getValue(playlistId).map { entry -> entry.songPath }

        override suspend fun entries(playlistId: String) = answer(playlistId) { entries -> entries.toList() }

        override suspend fun add(
            playlistId: String,
            songPaths: List<String>
        ): PlaylistWriteResult<Unit> = answer(playlistId, "add $songPaths") { entries ->
            entries += playlist(*songPaths.toTypedArray())
            Unit
        }

        override suspend fun remove(
            playlistId: String,
            entryIds: List<String>
        ): PlaylistWriteResult<Unit> = answer(playlistId, "remove $entryIds") { entries ->
            entries.removeAll { entry -> entry.entryId in entryIds }
            Unit
        }

        override suspend fun move(
            playlistId: String,
            entryId: String,
            index: Int,
            after: String?
        ): PlaylistWriteResult<Unit> = answer(playlistId, "move $entryId $index") { entries ->
            val entry = entries.first { it.entryId == entryId }
            entries.remove(entry)
            entries.add(index, entry)
            entries.getOrNull(index - 1)?.entryId shouldBe after
            Unit
        }

        override suspend fun rename(
            playlistId: String,
            name: String
        ): PlaylistWriteResult<Unit> = answer(playlistId, "rename $name") {
            names[playlistId] = name
        }

        override suspend fun delete(playlistId: String): PlaylistWriteResult<Unit> = answer(playlistId, "delete") {
            playlists.remove(playlistId)
            Unit
        }

        private fun <T> answer(
            playlistId: String,
            call: String? = null,
            block: (MutableList<ServerPlaylistEntry>) -> T
        ): PlaylistWriteResult<T> {
            val entries = playlists[playlistId]
            return when {
                offline -> PlaylistWriteResult.Failed
                refuse -> PlaylistWriteResult.Refused
                entries == null -> PlaylistWriteResult.PlaylistGone
                else -> PlaylistWriteResult.Success(block(entries)).also { call?.let(calls::add) }
            }
        }
    }

    private companion object {
        const val A = "jellyfin://item/a"
        const val B = "jellyfin://item/b"
        const val C = "jellyfin://item/c"
        const val UNKNOWN = "jellyfin://item/video"
    }
}
