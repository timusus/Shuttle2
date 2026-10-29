package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.SongCopyAvailability
import com.simplecityapps.shuttle.model.SongCopyPreferences
import com.simplecityapps.shuttle.model.copyAlbumKey
import com.simplecityapps.shuttle.model.copyKey
import com.simplecityapps.shuttle.smartplaylist.song
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeCopyPreferencesStore(initial: SongCopyPreferences = SongCopyPreferences()) : CopyPreferencesStore {
    val state = MutableStateFlow(initial)
    override val mergeCopies: Flow<Boolean> = MutableStateFlow(true)
    override val preferences: Flow<SongCopyPreferences> = state

    override fun update(change: (SongCopyPreferences) -> SongCopyPreferences) {
        state.value = change(state.value)
    }
}

class PrimaryCopiesTest {
    private val local = song(id = 1, path = "/music/1.flac", mediaProvider = MediaProviderType.Shuttle)
    private val emby = song(id = 2, path = "emby://2", mediaProvider = MediaProviderType.Emby).copy(externalId = "e2", serverAlbumId = "emby-album")
    private val plex = song(id = 3, path = "plex://3", mediaProvider = MediaProviderType.Plex).copy(externalId = "p3", serverAlbumId = "plex-album")

    private fun merged(vararg copies: Song): Song = copies.first().copy(copies = copies.toList())

    @Test
    fun `making a copy primary adds its key and drops its siblings'`() {
        val store = FakeCopyPreferencesStore(SongCopyPreferences(primaryCopies = setOf(emby.copyKey, "Jellyfin:other")))

        SetPrimaryCopy(store)(merged(local, emby, plex), plex)

        store.state.value.primaryCopies shouldBe setOf(plex.copyKey, "Jellyfin:other")
    }

    @Test
    fun `clearing removes every copy's key and leaves other songs`() {
        val store = FakeCopyPreferencesStore(SongCopyPreferences(primaryCopies = setOf(emby.copyKey, "Jellyfin:other")))

        ClearPrimaryCopy(store)(merged(local, emby, plex))

        store.state.value.primaryCopies shouldBe setOf("Jellyfin:other")
    }

    @Test
    fun `an album source key is set and the other sources' keys go`() {
        val store = FakeCopyPreferencesStore(SongCopyPreferences(primaryAlbums = setOf(emby.copyAlbumKey, "Jellyfin:x")))

        SetAlbumPrimarySource(store)(listOf(merged(local, emby, plex)), MediaProviderType.Plex)

        store.state.value.primaryAlbums shouldBe setOf(plex.copyAlbumKey, "Jellyfin:x")
    }

    @Test
    fun `automatic clears every source of the album`() {
        val store = FakeCopyPreferencesStore(SongCopyPreferences(primaryAlbums = setOf(emby.copyAlbumKey, plex.copyAlbumKey)))

        SetAlbumPrimarySource(store)(listOf(merged(local, emby, plex)), null)

        store.state.value.primaryAlbums shouldBe emptySet()
    }

    @Test
    fun `a primary copy ranks first while it plays and an unavailable one gives way`() {
        val prefs = SongCopyPreferences(primaryCopies = setOf(emby.copyKey))
        val playable = SongCopyAvailability()
        val ranking = { availability: SongCopyAvailability -> availability.ranking(prefs.comparator(availability)) }

        listOf(local, emby, plex).sortedWith(ranking(playable)).map { it.id } shouldBe listOf(2L, 1L, 3L)
        listOf(local, emby, plex).sortedWith(ranking(SongCopyAvailability(missingSongIds = setOf(2L)))).map { it.id } shouldBe listOf(1L, 3L, 2L)
        listOf(local, emby, plex).sortedWith(ranking(SongCopyAvailability(offline = true))).map { it.id } shouldBe listOf(1L, 2L, 3L)
    }
}
