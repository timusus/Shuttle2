package com.simplecityapps.mediaprovider

import com.simplecityapps.mediaprovider.MediaImporter.Companion.songTagsOutdated
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.runBlocking

class MediaImporterRemovalTest : MediaImporterTestBase() {
    @Test
    fun `a listing that came up short deletes nothing and makes the next sync full - which deletes as usual`() = runBlocking<Unit> {
        importer.mediaProviders -= provider
        importer.mediaProviders += server
        songRepository.stored = listOf(song())
        server.found = listOf(song().copy(id = 2, path = "/added"))
        server.missing = 1

        importer.sync(SyncTrigger.Periodic)

        songRepository.changes shouldBe Triple(1, 0, 0)
        preferences.lastSyncStart(server.type.name) shouldBe clock.time
        preferences.lastFullSyncStart(server.type.name) shouldBe null
        preferences.songTagsOutdated(server.type) shouldBe true

        server.missing = 0
        server.held = listOf("/added")
        importer.sync(SyncTrigger.Periodic)

        server.requests shouldBe listOf(null, null)
        songRepository.changes shouldBe Triple(1, 0, 1)
        preferences.lastFullSyncStart(server.type.name) shouldBe clock.time
        preferences.songTagsOutdated(server.type) shouldBe false
    }

    @Test
    fun `a server short by as many songs every full sync deletes from the second - and keeps its full-sync time`() = runBlocking<Unit> {
        importer.mediaProviders -= provider
        importer.mediaProviders += server
        songRepository.stored = listOf(song())
        server.found = listOf(song().copy(id = 2, path = "/added"))
        server.missing = 2

        importer.sync(SyncTrigger.Periodic)

        songRepository.changes shouldBe Triple(1, 0, 0)
        preferences.lastFullSyncStart(server.type.name) shouldBe null

        server.held = listOf("/added")
        server.heldMissing = 2
        importer.sync(SyncTrigger.Periodic)

        server.requests shouldBe listOf(null, null)
        songRepository.changes shouldBe Triple(1, 0, 1)
        preferences.lastFullSyncStart(server.type.name) shouldBe clock.time
        preferences.songTagsOutdated(server.type) shouldBe false
    }

    @Test
    fun `an incremental sync that came up short leaves the next full sync where it was`() = runBlocking<Unit> {
        importer.mediaProviders -= provider
        importer.mediaProviders += server
        songRepository.stored = listOf(song())
        preferences.setSongTagsVersion(server.type.name, MediaImporter.SONG_TAGS_VERSION)
        preferences.setLastSyncStart(server.type.name, clock.time - 1.hours)
        preferences.setLastFullSyncStart(server.type.name, clock.time - 1.days)
        server.found = listOf(song().copy(id = 2, path = "/added"))
        server.missing = 1

        importer.sync(SyncTrigger.Foreground)

        server.requests shouldBe listOf(clock.time - 1.hours - SyncPolicy.OVERLAP)
        preferences.lastSyncStart(server.type.name) shouldBe clock.time
        preferences.lastFullSyncStart(server.type.name) shouldBe clock.time - 1.days
    }

    /** A server with songs 1 to 3 stored, last synced an hour ago and in full a day ago, that an incremental sync finds nothing new on. */
    private suspend fun incrementalServer() {
        importer.mediaProviders -= provider
        importer.mediaProviders += server
        songRepository.stored = listOf(song(1, "/1"), song(2, "/2"), song(3, "/3"))
        preferences.setSongTagsVersion(server.type.name, MediaImporter.SONG_TAGS_VERSION)
        preferences.setLastSyncStart(server.type.name, clock.time - 1.hours)
        preferences.setLastFullSyncStart(server.type.name, clock.time - 1.days)
    }

    @Test
    fun `an incremental sync removes the songs the server no longer lists`() = runBlocking<Unit> {
        incrementalServer()
        server.count = 2
        server.held = listOf("/1", "/3")

        importer.sync(SyncTrigger.Foreground)

        songRepository.deleted[server.type]?.map { it.path } shouldBe listOf("/2")
        preferences.lastSyncStart(server.type.name) shouldBe clock.time
        preferences.lastFullSyncStart(server.type.name) shouldBe clock.time - 1.days
    }

    @Test
    fun `an incremental sync keeps a song a shifted listing page skipped - and removes the one the server did drop`() = runBlocking<Unit> {
        incrementalServer()
        server.count = 2
        // Song 1 went mid-listing, so a page shifted and skipped song 2; the second listing has it
        server.earlierListings += listOf("/3")
        server.held = listOf("/2", "/3")

        importer.sync(SyncTrigger.Foreground)

        songRepository.deleted[server.type]?.map { it.path } shouldBe listOf("/1")
    }

    @Test
    fun `an incremental sync whose confirming listing fails removes nothing`() = runBlocking<Unit> {
        incrementalServer()
        server.count = 2
        server.earlierListings += listOf("/1", "/3")
        server.held = null

        importer.sync(SyncTrigger.Foreground)

        songRepository.deleted[server.type].orEmpty() shouldBe emptyList()
    }

    @Test
    fun `an incremental sync whose server count is what is stored lists nothing`() = runBlocking<Unit> {
        incrementalServer()
        server.count = 3

        importer.sync(SyncTrigger.Foreground)

        server.pathListings shouldBe 0
        songRepository.deleted[server.type].orEmpty() shouldBe emptyList()
    }

    @Test
    fun `an incremental sync counts the songs it added as held`() = runBlocking<Unit> {
        incrementalServer()
        server.found = listOf(song(4, "/4"))
        server.count = 4

        importer.sync(SyncTrigger.Foreground)

        server.pathListings shouldBe 0
        songRepository.changes shouldBe Triple(1, 0, 0)
    }

    @Test
    fun `an incremental sync adding a song while another is gone still removes it`() = runBlocking<Unit> {
        incrementalServer()
        server.found = listOf(song(4, "/4"))
        server.count = 3
        server.held = listOf("/1", "/2", "/4")

        importer.sync(SyncTrigger.Foreground)

        songRepository.changes shouldBe Triple(1, 0, 1)
        songRepository.deleted[server.type]?.map { it.path } shouldBe listOf("/3")
    }

    @Test
    fun `an incremental sync with a count it cannot read lists the songs`() = runBlocking<Unit> {
        incrementalServer()
        server.count = null
        server.held = listOf("/1", "/2")

        importer.sync(SyncTrigger.Foreground)

        server.pathListings shouldBe 2
        songRepository.deleted[server.type]?.map { it.path } shouldBe listOf("/3")
    }

    @Test
    fun `an incremental sync whose path listing fails removes nothing - and still stores what it found`() = runBlocking<Unit> {
        incrementalServer()
        server.found = listOf(song(4, "/4"))
        server.count = 2
        server.held = null

        importer.sync(SyncTrigger.Foreground)

        songRepository.changes shouldBe Triple(1, 0, 0)
        preferences.lastSyncStart(server.type.name) shouldBe clock.time
    }

    @Test
    fun `an incremental sync whose path listing came up short removes nothing`() = runBlocking<Unit> {
        incrementalServer()
        server.count = 2
        server.held = listOf("/1")
        server.heldMissing = 1

        importer.sync(SyncTrigger.Foreground)

        songRepository.deleted[server.type].orEmpty() shouldBe emptyList()
        // Brought forward, so the count stops disagreeing rather than listing the paths every sync
        preferences.lastFullSyncStart(server.type.name) shouldBe null
        preferences.songTagsOutdated(server.type) shouldBe false
    }

    @Test
    fun `an incremental sync that would remove every song holds the removal back - until the full sync it brings forward finds them gone too`() = runBlocking<Unit> {
        incrementalServer()
        server.count = 0
        server.held = emptyList()

        importer.sync(SyncTrigger.Foreground)

        songRepository.deleted[server.type].orEmpty() shouldBe emptyList()
        preferences.lastFullSyncStart(server.type.name) shouldBe null

        importer.sync(SyncTrigger.Periodic)

        server.requests.last() shouldBe null
        songRepository.deleted[server.type]?.map { it.path } shouldBe listOf("/1", "/2", "/3")
        // The incremental sync's two, and the full sync's confirmation
        server.pathListings shouldBe 3
    }

    @Test
    fun `songs an incremental sync finds out of reach go once - through the full sync it brings forward when there are many`() = runBlocking<Unit> {
        // 30 audiobooks stored before the sync left their library out, and three songs
        val audiobooks = (10L..39L).map { id -> song(id, "/book/$id") }
        importer.mediaProviders -= provider
        importer.mediaProviders += server
        songRepository.stored = listOf(song(1, "/1"), song(2, "/2"), song(3, "/3")) + audiobooks
        preferences.setSongTagsVersion(server.type.name, MediaImporter.SONG_TAGS_VERSION)
        preferences.setLastSyncStart(server.type.name, clock.time - 1.hours)
        preferences.setLastFullSyncStart(server.type.name, clock.time - 1.days)
        server.count = 3
        server.held = listOf("/1", "/2", "/3")
        server.found = listOf(song(1, "/1"), song(2, "/2"), song(3, "/3"))

        importer.sync(SyncTrigger.Foreground)

        songRepository.deleted[server.type].orEmpty() shouldBe emptyList()
        preferences.lastFullSyncStart(server.type.name) shouldBe null

        importer.sync(SyncTrigger.Periodic)

        server.requests.last() shouldBe null
        songRepository.deleted[server.type]?.map { it.path } shouldBe audiobooks.map { it.path }
        preferences.lastFullSyncStart(server.type.name) shouldBe clock.time

        // The audiobooks gone, the count agrees, so the next incremental sync lists nothing
        songRepository.stored = server.found
        importer.sync(SyncTrigger.Periodic)

        server.requests.last() shouldBe clock.time - SyncPolicy.OVERLAP
        // The incremental sync's two, and the full sync's confirmation
        server.pathListings shouldBe 3
    }

    @Test
    fun `an incremental sync counts the songs the last full sync came up short by as held - and lists nothing`() = runBlocking<Unit> {
        incrementalServer()
        preferences.setListingShortfall(server.type.name, 2)
        server.count = 5

        importer.sync(SyncTrigger.Foreground)

        server.pathListings shouldBe 0
    }

    @Test
    fun `an incremental sync whose server holds songs it never fetched brings the full sync forward`() = runBlocking<Unit> {
        incrementalServer()
        server.count = 4
        server.held = listOf("/1", "/2", "/3", "/old-but-unseen")

        importer.sync(SyncTrigger.Foreground)

        songRepository.deleted[server.type].orEmpty() shouldBe emptyList()
        preferences.lastFullSyncStart(server.type.name) shouldBe null
    }

    @Test
    fun `a full import keeps a song a shifted listing page skipped - and removes the one the server did drop`() = runBlocking<Unit> {
        val songs = (1L..30L).map { id -> song(id = id, path = "jellyfin://item/$id") }
        songRepository.stored = songs
        // Song 1 went mid-listing, so a page shifted and skipped song 2: the listing lacks both, the confirming one has song 2
        server.found = songs.drop(2)
        server.held = songs.drop(1).map { it.path }

        serverImporter().import()

        songRepository.deleted[server.type].orEmpty().map { song -> song.id } shouldBe listOf(1L)
    }

    @Test
    fun `a full import whose confirming listing fails removes nothing`() = runBlocking<Unit> {
        val songs = (1L..30L).map { id -> song(id = id, path = "jellyfin://item/$id") }
        songRepository.stored = songs
        server.found = songs.drop(1)
        server.held = null

        serverImporter().import()

        songRepository.deleted[server.type].orEmpty().shouldBeEmpty()
    }

    @Test
    fun `a full import that would remove most of a source's songs keeps them until the next one finds them gone too`() = runBlocking<Unit> {
        val songs = (1L..30L).map { id -> song(id = id, path = "jellyfin://item/$id") }
        songRepository.stored = songs
        server.found = songs.take(5)
        server.held = server.found.map { it.path }

        serverImporter().import()
        songRepository.deleted[server.type].orEmpty().shouldBeEmpty()

        // The next import runs in a new process, as the periodic one usually does: only the preferences carry over
        serverImporter().import()
        songRepository.deleted[server.type].orEmpty().map { song -> song.id } shouldBe (6L..30L).toList()
    }

    @Test
    fun `an import after the user changed folders removes this device's songs at once - but keeps those under an unreadable root and a server's`() = runBlocking<Unit> {
        val device = ServerProvider(MediaProviderType.Shuttle)
        val deviceSongs = (1L..30L).map { id -> song(id = id, path = "/storage/emulated/0/Music/$id.mp3") }
        val cardSongs = (31L..32L).map { id -> song(id = id, path = "/storage/CARD/Music/$id.mp3") }
        songRepository.stored = deviceSongs + cardSongs
        device.found = deviceSongs.take(5)
        device.held = device.found.map { it.path }
        device.unreadableRoots = setOf("/storage/CARD/")
        server.found = emptyList()
        val importer = serverImporter().apply { mediaProviders += device }

        importer.import(foldersChanged = true)

        songRepository.deleted[device.type].orEmpty().map { song -> song.id } shouldBe (6L..30L).toList()
        songRepository.deleted[server.type].orEmpty().shouldBeEmpty()
    }

    @Test
    fun `a full sync that holds back a mass removal makes the next sync full - to apply it`() = runBlocking<Unit> {
        val songs = (1L..30L).map { id -> song(id = id, path = "jellyfin://item/$id") }
        songRepository.stored = songs
        server.found = songs.take(5)
        server.held = server.found.map { it.path }
        preferences.setSongTagsVersion(server.type.name, MediaImporter.SONG_TAGS_VERSION)
        preferences.setLastSyncStart(server.type.name, clock.time - 8.days)
        preferences.setLastFullSyncStart(server.type.name, clock.time - 8.days)
        val importer = serverImporter()

        importer.sync(SyncTrigger.Periodic)
        songRepository.deleted[server.type].orEmpty().shouldBeEmpty()
        preferences.lastSyncStart(server.type.name) shouldBe clock.time
        preferences.lastFullSyncStart(server.type.name) shouldBe null

        clock.time += 1.hours
        importer.sync(SyncTrigger.Foreground)
        server.requests shouldBe listOf(null, null)
        songRepository.deleted[server.type].orEmpty().map { song -> song.id } shouldBe (6L..30L).toList()
        preferences.lastFullSyncStart(server.type.name) shouldBe clock.time
    }

    @Test
    fun `a full import keeps the songs under a root the source couldn't read`() = runBlocking<Unit> {
        songRepository.stored = listOf(song(id = 1, path = "jellyfin://library/a/1"), song(id = 2, path = "jellyfin://library/b/2"), song(id = 3))
        server.found = listOf(song(id = 3))
        server.held = server.found.map { it.path }
        server.unreadableRoots = setOf("jellyfin://library/a/")

        serverImporter().import()

        songRepository.deleted[server.type].orEmpty().map { song -> song.id } shouldBe listOf(2L)
    }
}
