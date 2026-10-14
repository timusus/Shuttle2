package com.simplecityapps.mediaprovider.server.mediabrowser

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class SiblingArtworkTest {
    @Test
    fun `a song without an image takes the first sibling's item id and tag`() {
        val songs = listOf(song("a", tag = null), song("b", tag = "tag-b"), song("c", tag = "tag-c"))

        songs.withSiblingArtwork().map { it.artworkItemId to it.artworkVersion } shouldBe
            listOf("b" to "tag-b", null to "tag-b", null to "tag-c")
    }

    @Test
    fun `a song with its own image keeps it`() {
        val songs = listOf(song("a", tag = "tag-a"), song("b", tag = "tag-b"))

        songs.withSiblingArtwork() shouldBe songs
    }

    @Test
    fun `an album with no image anywhere leaves its songs as they are`() {
        val songs = listOf(song("a", tag = null), song("b", tag = null))

        songs.withSiblingArtwork() shouldBe songs
    }

    @Test
    fun `a song only takes the image of a sibling on its own album`() {
        val songs = listOf(song("a", tag = null, albumId = "album-1"), song("b", tag = "tag-b", albumId = "album-2"))

        songs.withSiblingArtwork() shouldBe songs
    }

    @Test
    fun `a stored sibling stands in when an incremental sync returned only the song without an image`() {
        val stored = listOf(song("b", tag = "tag-b"))

        listOf(song("a", tag = null)).withSiblingArtwork(stored).single().run { artworkItemId to artworkVersion } shouldBe ("b" to "tag-b")
    }

    @Test
    fun `a stored sibling the sync returned again is read from the new copy`() {
        val stored = listOf(song("b", tag = "old"))

        listOf(song("a", tag = null), song("b", tag = "new")).withSiblingArtwork(stored).first().artworkVersion shouldBe "new"
    }

    @Test
    fun `a stored sibling that itself borrowed an image passes on the item it borrowed from`() {
        val stored = listOf(song("b", tag = "tag-c", artworkItemId = "c"))

        listOf(song("a", tag = null)).withSiblingArtwork(stored).single().artworkItemId shouldBe "c"
    }

    @Test
    fun `a song with no server album is left alone`() {
        val songs = listOf(song("a", tag = null, albumId = null), song("b", tag = "tag-b", albumId = null))

        songs.withSiblingArtwork() shouldBe songs
    }

    private fun song(
        id: String,
        tag: String?,
        albumId: String? = "album-1",
        artworkItemId: String? = null
    ) = Song(
        id = 0,
        name = id,
        albumArtist = "Artist",
        artists = listOf("Artist"),
        album = "Album",
        track = null,
        disc = null,
        duration = 180_000,
        date = null,
        genres = emptyList(),
        path = "jellyfin://item/$id",
        size = 0,
        mimeType = "Audio/*",
        lastModified = null,
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        externalId = id,
        mediaProvider = MediaProviderType.Jellyfin,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null,
        artworkVersion = tag,
        artworkItemId = artworkItemId,
        serverAlbumId = albumId
    )
}
