package com.simplecityapps.playback.mediasession

import com.simplecityapps.playback.chromecast.FakeSongRepository
import com.simplecityapps.playback.fakes.FakePlaylistRepository
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.mediasession.VoiceSearch.Focus
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** RS-60 and RS-61 per focus: what a voice search resolves to, from the query and the parts the assistant parses out. */
class VoiceSearchResolverTest {
    private val airbag = song(1, "Airbag", "Radiohead", "OK Computer", 1, "Alternative")
    private val paranoidAndroid = song(2, "Paranoid Android", "Radiohead", "OK Computer", 2, "Alternative")
    private val karmaPolice = song(3, "Karma Police", "Radiohead", "OK Computer", 3, "Alternative")
    private val you = song(4, "You", "Radiohead", "Pablo Honey", 1, "Alternative")
    private val creep = song(5, "Creep", "Radiohead", "Pablo Honey", 2, "Alternative")
    private val comeTogether = song(6, "Come Together", "The Beatles", "Abbey Road", 1, "Rock")
    private val something = song(7, "Something", "The Beatles", "Abbey Road", 2, "Rock")
    private val joga = song(8, "Jóga", "Björk", "Homogenic", 1, "Electronic")
    private val looseTrack = testSong(9, name = "Loose Track").copy(artists = listOf("Nobody"))

    private val okComputer = listOf(airbag, paranoidAndroid, karmaPolice)
    private val pabloHoney = listOf(you, creep)
    private val abbeyRoad = listOf(comeTogether, something)
    private val library = okComputer + pabloHoney + abbeyRoad + joga + looseTrack

    private val roadTrip = playlist(1, "Road Trip")

    private fun resolver(
        songs: List<Song> = library,
        playlists: Map<Playlist, List<Song>> = mapOf(roadTrip to listOf(karmaPolice, comeTogether))
    ) = VoiceSearchResolver(FakeSongRepository(songs), FakePlaylistRepository(playlists))

    private suspend fun resolve(search: VoiceSearch, resolver: VoiceSearchResolver = resolver()) = resolver.resolve(search)

    @Test
    fun `RS-60 an artist focus plays the artist's songs`() = runTest {
        resolve(VoiceSearch("radiohead", Focus.Artist, artist = "Radiohead")) shouldBe VoiceSearchResult.Songs(okComputer + pabloHoney, 0)
        // "The" is optional, as it's often left out when spoken; so is the artist part, when the words say it.
        resolve(VoiceSearch("beatles", Focus.Artist, artist = "Beatles")) shouldBe VoiceSearchResult.Songs(abbeyRoad, 0)
        resolve(VoiceSearch("the beatles", Focus.Artist)) shouldBe VoiceSearchResult.Songs(abbeyRoad, 0)
        // Accents don't count, and a misheard name finds the closest.
        resolve(VoiceSearch("bjork", Focus.Artist, artist = "Bjork")) shouldBe VoiceSearchResult.Songs(listOf(joga), 0)
        resolve(VoiceSearch("radio head", Focus.Artist, artist = "Radio Head")) shouldBe VoiceSearchResult.Songs(okComputer + pabloHoney, 0)
    }

    @Test
    fun `RS-60 an album focus plays the album in order`() = runTest {
        resolve(VoiceSearch("ok computer", Focus.Album, album = "OK Computer")) shouldBe VoiceSearchResult.Songs(okComputer, 0)
        resolve(VoiceSearch("abbey road by the beatles", Focus.Album, album = "Abbey Road", artist = "The Beatles")) shouldBe VoiceSearchResult.Songs(abbeyRoad, 0)
        resolve(VoiceSearch("pablo honey", Focus.Album)) shouldBe VoiceSearchResult.Songs(pabloHoney, 0)
    }

    @Test
    fun `RS-60 a song focus plays the song, then the rest of its album`() = runTest {
        resolve(VoiceSearch("creep by radiohead", Focus.Song, title = "Creep", artist = "Radiohead")) shouldBe VoiceSearchResult.Songs(pabloHoney, 1)
        resolve(VoiceSearch("karma police", Focus.Song)) shouldBe VoiceSearchResult.Songs(okComputer, 2)
        // A song that isn't on an album plays on its own.
        resolve(VoiceSearch("loose track", Focus.Song, title = "Loose Track")) shouldBe VoiceSearchResult.Songs(listOf(looseTrack), 0)
    }

    @Test
    fun `RS-60 a genre focus plays the genre's songs`() = runTest {
        resolve(VoiceSearch("rock", Focus.Genre, genre = "Rock")) shouldBe VoiceSearchResult.Songs(abbeyRoad, 0)
        resolve(VoiceSearch("electronica", Focus.Genre, genre = "Electronica")) shouldBe VoiceSearchResult.Songs(listOf(joga), 0)
    }

    @Test
    fun `RS-60 a playlist focus plays the playlist in order`() = runTest {
        resolve(VoiceSearch("road trip", Focus.Playlist, playlist = "Road Trip")) shouldBe VoiceSearchResult.Songs(listOf(karmaPolice, comeTogether), 0)
        // With no playlists, the words are searched for anything.
        resolve(VoiceSearch("karma police", Focus.Playlist, playlist = "Karma Police"), resolver(playlists = emptyMap())) shouldBe VoiceSearchResult.Songs(okComputer, 2)
        // Nor with only empty ones, as a new library's Favorites is.
        resolve(VoiceSearch("karma police", Focus.Playlist), resolver(playlists = mapOf(playlist(2, "Favorites") to emptyList()))) shouldBe VoiceSearchResult.Songs(okComputer, 2)
    }

    @Test
    fun `RS-60 an unstructured search plays the best match of any kind`() = runTest {
        resolve(VoiceSearch("Radiohead")) shouldBe VoiceSearchResult.Songs(okComputer + pabloHoney, 0)
        resolve(VoiceSearch("Abbey Road")) shouldBe VoiceSearchResult.Songs(abbeyRoad, 0)
        resolve(VoiceSearch("Karma Police")) shouldBe VoiceSearchResult.Songs(okComputer, 2)
        resolve(VoiceSearch("Creep by Radiohead")) shouldBe VoiceSearchResult.Songs(pabloHoney, 1)
        resolve(VoiceSearch("radiohead creep")) shouldBe VoiceSearchResult.Songs(pabloHoney, 1)
        resolve(VoiceSearch("Road Trip")) shouldBe VoiceSearchResult.Songs(listOf(karmaPolice, comeTogether), 0)
        resolve(VoiceSearch("rock")) shouldBe VoiceSearchResult.Songs(abbeyRoad, 0)
        // Words added around a name still find it.
        resolve(VoiceSearch("songs by radiohead")) shouldBe VoiceSearchResult.Songs(okComputer + pabloHoney, 0)
    }

    @Test
    fun `RS-60 a search that matches nothing well plays the closest match`() = runTest {
        resolve(VoiceSearch("karma polis")) shouldBe VoiceSearchResult.Songs(okComputer, 2)
        resolve(VoiceSearch("xyzzy")).shouldBeInstanceOf<VoiceSearchResult.Songs>().songs.isNotEmpty() shouldBe true
    }

    @Test
    fun `RS-61 a search for nothing in particular plays anything, and an empty library nothing`() = runTest {
        resolve(VoiceSearch(null)) shouldBe VoiceSearchResult.Anything
        resolve(VoiceSearch("  ", Focus.Artist)) shouldBe VoiceSearchResult.Anything
        resolve(VoiceSearch("radiohead"), resolver(songs = emptyList(), playlists = emptyMap())) shouldBe VoiceSearchResult.Empty
    }

    @Test
    fun `search keys ignore case, accents, punctuation and a leading article`() {
        "The Beatles".searchKey() shouldBe "beatles"
        "Björk".searchKey() shouldBe "bjork"
        "Kestrel's Theme".searchKey() shouldBe "kestrels theme"
        "Nightjar & the Loom".searchKey() shouldBe "nightjar and the loom"
        "Route 29, Outbound".searchKey() shouldBe "route 29 outbound"
        "The".searchKey() shouldBe "the"
        matchScore("ok computer", "ok computer oknotok") shouldBeGreaterThan matchScore("ok computer", "ok komputer")
    }

    private fun song(
        id: Long,
        name: String,
        artist: String,
        album: String,
        track: Int,
        genre: String
    ): Song = testSong(id, name = name).copy(albumArtist = artist, artists = listOf(artist), album = album, track = track, genres = listOf(genre))

    private fun playlist(
        id: Long,
        name: String
    ) = Playlist(id = id, name = name, songCount = 0, duration = 0, sortOrder = PlaylistSongSortOrder.Position, mediaProvider = MediaProviderType.Shuttle, externalId = null)
}
