package com.simplecityapps.mediaprovider

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.runBlocking

class MediaImporterPlaylistsTest : MediaImporterTestBase() {
    @Test
    fun `the daily sync fetches the playlists of a server synced in the last few minutes`() = runBlocking<Unit> {
        importer.mediaProviders -= provider
        importer.mediaProviders += server
        songRepository.stored = listOf(song())
        preferences.setLastSyncStart(server.type.name, clock.time - 5.minutes)
        preferences.setLastFullSyncStart(server.type.name, clock.time - 1.days)

        importer.sync(SyncTrigger.Periodic)

        server.playlistRequests shouldBe 1
    }

    @Test
    fun `an incremental sync that added no songs passes the playlist versions of the playlists still stored - and keeps the new ones`() = runBlocking<Unit> {
        importer.mediaProviders -= provider
        importer.mediaProviders += server
        songRepository.stored = listOf(song())
        playlistStore.storedIds = setOf("1")
        preferences.setSongTagsVersion(server.type.name, MediaImporter.SONG_TAGS_VERSION)
        preferences.setPlaylistVersions(server.type.name, mapOf("1" to "v1", "gone" to "v9"))
        server.playlistListing = MediaImporter.PlaylistListing(emptyList(), unchanged = setOf("1"), versions = mapOf("1" to "v1", "2" to "v2"))
        preferences.setLastSyncStart(server.type.name, clock.time - 5.minutes)
        preferences.setLastFullSyncStart(server.type.name, clock.time - 1.days)

        importer.sync(SyncTrigger.Periodic)

        // "gone" was deleted (or emptied) since: its version isn't passed, so it's read again
        server.knownVersionRequests shouldBe listOf(mapOf("1" to "v1"))
        playlistStore.reconciled.single().versions shouldBe mapOf("1" to "v1", "2" to "v2")
        preferences.playlistVersions(server.type.name) shouldBe mapOf("1" to "v1", "2" to "v2")
    }

    @Test
    fun `a sync that added songs reads every playlist again`() = runBlocking<Unit> {
        importer.mediaProviders -= provider
        importer.mediaProviders += server
        songRepository.stored = listOf(song())
        server.found = listOf(song(id = 0, path = "jellyfin://item/2"))
        playlistStore.storedIds = setOf("1")
        preferences.setSongTagsVersion(server.type.name, MediaImporter.SONG_TAGS_VERSION)
        preferences.setPlaylistVersions(server.type.name, mapOf("1" to "v1"))
        preferences.setLastSyncStart(server.type.name, clock.time - 5.minutes)
        preferences.setLastFullSyncStart(server.type.name, clock.time - 1.days)

        importer.sync(SyncTrigger.Periodic)

        server.knownVersionRequests shouldBe listOf(emptyMap())
    }

    @Test
    fun `a full sync reads every playlist again`() = runBlocking<Unit> {
        importer.mediaProviders -= provider
        importer.mediaProviders += server
        songRepository.stored = listOf(song())
        playlistStore.storedIds = setOf("1")
        preferences.setPlaylistVersions(server.type.name, mapOf("1" to "v1"))

        importer.sync(SyncTrigger.Foreground)

        server.playlistRequests shouldBe 1
        server.knownVersionRequests shouldBe listOf(emptyMap())
    }
}
