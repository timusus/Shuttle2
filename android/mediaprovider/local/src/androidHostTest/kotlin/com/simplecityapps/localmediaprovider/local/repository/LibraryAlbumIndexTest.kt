package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.entity.SongIdentityData
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The library's one album index is built once, and again only when the songs table changes. */
class LibraryAlbumIndexTest {
    private fun song(
        id: Long,
        album: String
    ) = SongIdentityData(id, album, "Artist", null, listOf("Artist"), null, null, null, MediaProviderType.Shuttle, "/music/$album/$id.mp3")

    @Test
    fun `the index is rebuilt only when the songs table changes`() = runTest {
        val songsChanged = MutableSharedFlow<Set<String>>()
        var rows = listOf(song(1, "Blue"))
        var reads = 0
        val index = LibraryAlbumIndex(backgroundScope, songsChanged) {
            reads++
            rows
        }

        val first = index.albumIndex()
        index.albumIndex() shouldBeSameInstanceAs first
        index.albumIndex() shouldBeSameInstanceAs first
        reads shouldBe 1

        rows = rows + song(2, "Low")
        songsChanged.emit(setOf("songs"))
        runCurrent()

        val second = index.albumIndex()
        reads shouldBe 2
        second.songIds(AlbumGroupKey("low", AlbumArtistGroupKey("artist"))) shouldBe listOf(2L)
        index.albumIndex() shouldBeSameInstanceAs second
        reads shouldBe 2
    }
}
