package com.simplecityapps.shuttle.sorting

import com.simplecityapps.mediaprovider.repository.genres.GenreComparator
import com.simplecityapps.mediaprovider.repository.songs.SongComparator
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class LetterIndexTest {

    @Test
    fun aSectionStartsWhereverTheFirstLetterChangesInListOrder() {
        val keys = listOf("abba", "ace", "beatles", "blur", "cure")

        letterSections(keys) { it } shouldBe listOf(
            LetterSection("A", 0),
            LetterSection("B", 2),
            LetterSection("C", 4),
        )
    }

    @Test
    fun digitsSymbolsBlanksAndMissingKeysShareTheOtherSection() {
        val keys = listOf("10cc", "!!!", "", null, "  ", "air")

        letterSections(keys) { it } shouldBe listOf(LetterSection("#", 0), LetterSection("A", 5))
    }

    @Test
    fun accentedLettersFoldIntoTheirBaseLetter() {
        val keys = listOf("eels", "élan", "Émilie", "zz")

        letterSections(keys) { it } shouldBe listOf(LetterSection("E", 0), LetterSection("Z", 3))
    }

    @Test
    fun ligaturesCollatedAsTwoLettersStayInTheirFirstLettersRun() {
        val songs = listOf("Œuvre", "Æther", "apple", "Azure", "Lemon", "zebra", "Oslo", "Odd").map { song(name = it) }

        val sorted = songs.sortedWith(SongComparator.songNameComparator)

        letterSections(sorted, songLetterKey(SongSortOrder.SongName)!!).map { it.letter } shouldBe listOf("A", "L", "O", "Z")
    }

    @Test
    fun scriptsWithoutAShortAlphabetGoUnderOther() {
        letterLabel("坂本龍一") shouldBe "#"
        letterLabel("방탄소년단") shouldBe "#"
        letterLabel("Россия") shouldBe "Р"
        letterLabel("Ωmega") shouldBe "Ω"
    }

    @Test
    fun leadingSpacesAreSkipped() {
        letterLabel("  moby") shouldBe "M"
    }

    @Test
    fun anEighteenThousandSongLibraryIndexesInOnePass() {
        val keys = (0 until 18_000).map { ('a' + it * 26 / 18_000) + "song $it" }

        val sections = letterSections(keys) { it }

        sections.size shouldBe 26
        sections.last() shouldBe LetterSection("Z", keys.indexOfFirst { it.startsWith("z") })
    }

    // The index labels what the shared sort put in order, so each letter must come out as one run.

    @Test
    fun songsSortedByNameGiveEachLetterOneSection() {
        val songs = listOf("zebra", "Émilie", "10cc", "apple", "Beta", "élan", "Ágape").map { song(name = it) }

        val sorted = songs.sortedWith(SongComparator.songNameComparator)

        letterSections(sorted, songLetterKey(SongSortOrder.SongName)!!).map { it.letter } shouldBe listOf("#", "A", "B", "E", "Z")
    }

    @Test
    fun albumSectionsFollowTheGroupKeyWhichDropsALeadingArticle() {
        val songs = listOf("The Wall", "Abbey Road", "A Night at the Opera", "Blue Lines").map { song(name = it, album = it) }

        val sorted = songs.sortedWith(SongComparator.albumGroupKeyComparator)

        sorted.map { it.album } shouldBe listOf("Abbey Road", "Blue Lines", "A Night at the Opera", "The Wall")
        letterSections(sorted, songLetterKey(SongSortOrder.AlbumGroupKey)!!) shouldBe listOf(
            LetterSection("A", 0),
            LetterSection("B", 1),
            LetterSection("N", 2),
            LetterSection("W", 3),
        )
    }

    @Test
    fun genresSortedByNameCollateCaseTogether() {
        val genres = listOf("rock", "Électro", "Beta", "alpha").map { Genre(it, songCount = 1, duration = 0, mediaProviders = emptyList()) }

        val sorted = genres.sortedWith(GenreComparator.defaultComparator)

        sorted.map { it.name } shouldBe listOf("alpha", "Beta", "Électro", "rock")
        letterSections(sorted, genreLetterKey(GenreSortOrder.Default)!!).map { it.letter } shouldBe listOf("A", "B", "E", "R")
    }

    @Test
    fun sortsThatArentByNameHaveNoIndex() {
        songLetterKey(SongSortOrder.Year) shouldBe null
        songLetterKey(SongSortOrder.PlayCount) shouldBe null
        songLetterKey(SongSortOrder.LastModified) shouldBe null
        songLetterKey(SongSortOrder.DateAdded) shouldBe null
        songLetterIndex(listOf(song(name = "a")), SongSortOrder.DateAdded) shouldBe null
        albumLetterKey(AlbumSortOrder.Year) shouldBe null
        albumLetterKey(AlbumSortOrder.PlayCount) shouldBe null
        albumLetterKey(AlbumSortOrder.Random) shouldBe null
        genreLetterKey(GenreSortOrder.SongCount) shouldBe null
    }

    private fun song(name: String, album: String = "Album") = Song(
        id = 0,
        name = name,
        albumArtist = "Artist",
        artists = listOf("Artist"),
        album = album,
        track = 1,
        disc = 1,
        duration = 180_000,
        date = null,
        genres = emptyList(),
        path = "/music/$name.mp3",
        size = 0,
        mimeType = "audio/mpeg",
        lastModified = null,
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        mediaProvider = MediaProviderType.Shuttle,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )
}
