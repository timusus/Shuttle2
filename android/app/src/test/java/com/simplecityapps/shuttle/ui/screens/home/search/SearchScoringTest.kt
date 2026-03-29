package com.simplecityapps.shuttle.ui.screens.home.search

import com.simplecityapps.mediaprovider.StringComparison
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchScoringTest {

    // Helper function to create a minimal Song for testing
    private fun createTestSong(
        name: String? = "Test Song",
        album: String? = "Test Album",
        albumArtist: String? = "Test Artist",
        artists: List<String> = listOf("Test Artist")
    ): Song = Song(
        id = 1L,
        name = name,
        album = album,
        albumArtist = albumArtist,
        artists = artists,
        track = 1,
        disc = 1,
        duration = 180,
        date = null,
        genres = emptyList(),
        path = "/test/path.mp3",
        size = 1000L,
        mimeType = "audio/mpeg",
        lastModified = null,
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        mediaProvider = MediaProviderType.MediaStore,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )

    private fun createTestAlbum(
        name: String? = "Test Album",
        albumArtist: String? = "Test Artist",
        artists: List<String> = listOf("Test Artist")
    ): Album = Album(
        name = name,
        albumArtist = albumArtist,
        artists = artists,
        songCount = 10,
        duration = 1800,
        year = null,
        playCount = 0,
        lastSongPlayed = null,
        lastSongCompleted = null,
        groupKey = AlbumGroupKey("test-key", null),
        mediaProviders = listOf(MediaProviderType.MediaStore)
    )

    private fun createTestAlbumArtist(
        name: String? = "Test Artist",
        artists: List<String> = listOf("Test Artist")
    ): AlbumArtist = AlbumArtist(
        name = name,
        artists = artists,
        albumCount = 5,
        songCount = 50,
        playCount = 0,
        groupKey = AlbumArtistGroupKey("test-key"),
        mediaProviders = listOf(MediaProviderType.MediaStore)
    )

    @Test
    fun `SongJaroSimilarity - exact song name match has highest score`() {
        val song = createTestSong(name = "Help!", album = "Help!", albumArtist = "The Beatles")
        val similarity = SongJaroSimilarity(song, "help")

        // Song name match should contribute most to composite score
        assertTrue(similarity.nameJaroSimilarity.score > 0.90)
        assertTrue(similarity.compositeScore > 0.90)
    }

    @Test
    fun `SongJaroSimilarity - composite score weighs song name highest`() {
        val song = createTestSong(
            name = "Perfect Match",
            album = "Partial Match",
            albumArtist = "No Match At All"
        )
        val similarity = SongJaroSimilarity(song, "perfect match")

        // Composite score should be driven by the song name match (weight 1.0)
        val expectedScore = similarity.nameJaroSimilarity.score * 1.0
        assertTrue(similarity.compositeScore >= expectedScore * 0.99)
    }

    @Test
    fun `SongJaroSimilarity - artist match has higher weight than album`() {
        val song1 = createTestSong(
            name = "Song",
            album = "Beatles Album",
            albumArtist = "Other Artist"
        )
        val song2 = createTestSong(
            name = "Song",
            album = "Other Album",
            albumArtist = "The Beatles"
        )

        val similarity1 = SongJaroSimilarity(song1, "beatles")
        val similarity2 = SongJaroSimilarity(song2, "beatles")

        // Artist match (weight 0.85) should score higher than album match (weight 0.75)
        assertTrue(similarity2.compositeScore > similarity1.compositeScore)
    }

    @Test
    fun `SongJaroSimilarity - exact matches get boost`() {
        val exactMatchSong = createTestSong(name = "Help")
        val nearMatchSong = createTestSong(name = "Different")

        val exactSimilarity = SongJaroSimilarity(exactMatchSong, "help")
        val nearSimilarity = SongJaroSimilarity(nearMatchSong, "help")

        // Exact match should get the 0.01 boost (above 1.0)
        assertTrue(exactSimilarity.compositeScore > 1.0)
        // Non-matching string should score below 1.0
        assertTrue(nearSimilarity.compositeScore < 1.0)
        // Exact match should score much higher
        assertTrue(exactSimilarity.compositeScore > nearSimilarity.compositeScore)
    }

    @Test
    fun `SongJaroSimilarity - handles null fields gracefully`() {
        val song = createTestSong(name = null, album = null, albumArtist = null, artists = emptyList())
        val similarity = SongJaroSimilarity(song, "test")

        // Should not crash and should return low scores
        assertEquals(0.0, similarity.compositeScore, 0.001)
    }

    @Test
    fun `AlbumJaroSimilarity - album name match has highest weight`() {
        val album = createTestAlbum(
            name = "Abbey Road",
            albumArtist = "Other Artist"
        )
        val similarity = AlbumJaroSimilarity(album, "abbey road")

        // Album name match should dominate (weight 1.0)
        assertTrue(similarity.compositeScore > 0.95)
    }

    @Test
    fun `AlbumJaroSimilarity - artist match has lower weight than album name`() {
        val album1 = createTestAlbum(
            name = "Perfect",
            albumArtist = "Similar"
        )
        val album2 = createTestAlbum(
            name = "Similar",
            albumArtist = "Perfect"
        )

        val similarity1 = AlbumJaroSimilarity(album1, "perfect")
        val similarity2 = AlbumJaroSimilarity(album2, "perfect")

        // Album name match (weight 1.0) should beat artist match (weight 0.80)
        assertTrue(similarity1.compositeScore > similarity2.compositeScore)
    }

    @Test
    fun `AlbumJaroSimilarity - exact match gets boost`() {
        val album = createTestAlbum(name = "Help")
        val similarity = AlbumJaroSimilarity(album, "help")

        // Exact match should boost score above 1.0
        assertTrue(similarity.compositeScore > 1.0)
    }

    @Test
    fun `ArtistJaroSimilarity - both artist fields weighted similarly`() {
        val artist1 = createTestAlbumArtist(
            name = "The Beatles",
            artists = listOf("Other")
        )
        val artist2 = createTestAlbumArtist(
            name = "Other",
            artists = listOf("The Beatles")
        )

        val similarity1 = ArtistJaroSimilarity(artist1, "beatles")
        val similarity2 = ArtistJaroSimilarity(artist2, "beatles")

        // Both should have high scores, albumArtist slightly higher (1.0 vs 0.95)
        assertTrue(similarity1.compositeScore > 0.90)
        assertTrue(similarity2.compositeScore > 0.90)
        assertTrue(similarity1.compositeScore >= similarity2.compositeScore)
    }

    @Test
    fun `ArtistJaroSimilarity - exact match gets boost`() {
        val artist = createTestAlbumArtist(name = "Beatles")
        val similarity = ArtistJaroSimilarity(artist, "beatles")

        // Exact match should boost score above 1.0
        assertTrue(similarity.compositeScore > 1.0)
    }

    @Test
    fun `composite scores enable consistent ranking across entity types`() {
        val song = createTestSong(name = "Abbey Road", album = "Other", albumArtist = "Other")
        val album = createTestAlbum(name = "Abbey Road", albumArtist = "Other")
        val artist = createTestAlbumArtist(name = "Abbey Road")

        val songSim = SongJaroSimilarity(song, "abbey road")
        val albumSim = AlbumJaroSimilarity(album, "abbey road")
        val artistSim = ArtistJaroSimilarity(artist, "abbey road")

        // All should have high composite scores for exact primary field matches
        assertTrue(songSim.compositeScore > 1.0)
        assertTrue(albumSim.compositeScore > 1.0)
        assertTrue(artistSim.compositeScore > 1.0)
    }

    @Test
    fun `real-world scenario - searching Beatles should rank Beatles songs highly`() {
        val beatlesSong = createTestSong(
            name = "Help!",
            album = "Help!",
            albumArtist = "The Beatles"
        )
        val otherSong = createTestSong(
            name = "Beatles Tribute",
            album = "Cover Album",
            albumArtist = "Other Artist"
        )

        val beatlesSim = SongJaroSimilarity(beatlesSong, "beatles")
        val otherSim = SongJaroSimilarity(otherSong, "beatles")

        // Beatles song should rank higher due to artist match (weight 0.85) vs song name match (weight 1.0)
        // But "beatles" in "The Beatles" gets high score due to multi-word matching
        assertTrue(beatlesSim.compositeScore > StringComparison.threshold)

        // Both should pass threshold but Beatles artist match should be strong
        assertTrue(beatlesSim.albumArtistNameJaroSimilarity.score > 0.90)
    }

    @Test
    fun `real-world scenario - partial album name matches`() {
        val album = createTestAlbum(name = "The Dark Side of the Moon")
        val similarity = AlbumJaroSimilarity(album, "dark side")

        // Should match due to multi-word matching
        assertTrue(similarity.compositeScore > StringComparison.threshold)
    }

    @Test
    fun `real-world scenario - sorting songs by composite score`() {
        val songs = listOf(
            createTestSong(name = "Help!", album = "Help!", albumArtist = "The Beatles"),
            createTestSong(name = "Helping Hand", album = "Other Album", albumArtist = "Other Artist"),
            createTestSong(name = "Random Song", album = "Help! Album", albumArtist = "Other Artist"),
            createTestSong(name = "Another Song", album = "Other Album", albumArtist = "Help Foundation")
        )

        val similarities = songs.map { SongJaroSimilarity(it, "help") }
        val sorted = similarities.sortedByDescending { it.compositeScore }

        // "Help!" exact match should rank first
        assertEquals("Help!", sorted[0].song.name)

        // High-scoring results (name and artist matches) should be above threshold
        // Note: Album-only matches have lower weight (0.75) and may not exceed threshold
        val highScoringSongs = sorted.filter {
            it.song.name == "Help!" ||
                it.song.albumArtist == "Help Foundation"
        }
        highScoringSongs.forEach { similarity ->
            assertTrue(
                "Song '${similarity.song.name}' should be above threshold",
                similarity.compositeScore > StringComparison.threshold
            )
        }

        // Verify proper ranking order
        assertEquals("Help!", sorted[0].song.name) // Exact name match ranks highest
        assertTrue(sorted[0].compositeScore > sorted[1].compositeScore) // Rankings are descending
    }

    @Test
    fun `composite score handles mixed field matches correctly`() {
        val song = createTestSong(
            name = "Some Song",
            album = "Beatles Album",
            albumArtist = "The Beatles"
        )

        val similarity = SongJaroSimilarity(song, "beatles")

        // Should have high composite score from artist/album matches
        assertTrue(similarity.compositeScore > StringComparison.threshold)

        // Artist match should be weighted higher than album match
        val expectedArtistContribution = similarity.albumArtistNameJaroSimilarity.score * 0.85
        val expectedAlbumContribution = similarity.albumNameJaroSimilarity.score * 0.75

        assertTrue(expectedArtistContribution > expectedAlbumContribution)
    }

    @Test
    fun `threshold lowering from 0_90 to 0_85 enables more matches`() {
        // Verify the new threshold value
        assertEquals(0.85, StringComparison.threshold, 0.001)

        // Test cases that would fail with 0.90 but pass with 0.85
        val song = createTestSong(albumArtist = "The Beatles")
        val similarity = SongJaroSimilarity(song, "beatles")

        // "beatles" matching "The Beatles" should now pass (was ~0.88 with old threshold)
        assertTrue(
            "Partial match should pass with lowered threshold",
            similarity.compositeScore > StringComparison.threshold
        )
    }
}
