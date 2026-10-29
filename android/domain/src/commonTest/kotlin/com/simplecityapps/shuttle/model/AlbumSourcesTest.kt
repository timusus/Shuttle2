package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.smartplaylist.song
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class AlbumSourcesTest {
    private fun copy(id: Long, provider: MediaProviderType) = song(id = id, path = "$provider://$id", mediaProvider = provider).copy(serverAlbumId = "$provider-album")

    private fun merged(vararg copies: Song) = copies.first().copy(copies = copies.toList())

    private val songs = listOf(
        merged(copy(1, MediaProviderType.Shuttle), copy(2, MediaProviderType.Emby)),
        merged(copy(3, MediaProviderType.Emby), copy(4, MediaProviderType.Plex)),
        merged(copy(5, MediaProviderType.Emby)),
    )

    @Test
    fun `sources count the songs they hold and the busiest comes first`() {
        val sources = albumSources(songs)

        sources.songCount shouldBe 3
        sources.sources.map { it.provider to it.songCount } shouldBe listOf(
            MediaProviderType.Emby to 3,
            MediaProviderType.Shuttle to 1,
            MediaProviderType.Plex to 1,
        )
        sources.hasChoice shouldBe true
        sources.automatic shouldBe true
    }

    @Test
    fun `a source whose album key is primary is marked and the choice is no longer automatic`() {
        val sources = albumSources(songs, primaryAlbums = setOf(copy(4, MediaProviderType.Plex).copyAlbumKey))

        sources.sources.filter { it.primary }.map { it.provider } shouldBe listOf(MediaProviderType.Plex)
        sources.automatic shouldBe false
    }

    @Test
    fun `an album in one source offers no choice`() {
        albumSources(listOf(copy(1, MediaProviderType.Shuttle))).hasChoice shouldBe false
    }
}
