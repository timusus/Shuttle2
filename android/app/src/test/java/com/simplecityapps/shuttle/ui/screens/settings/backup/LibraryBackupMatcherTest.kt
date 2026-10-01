package com.simplecityapps.shuttle.ui.screens.settings.backup

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.time.Instant

class LibraryBackupMatcherTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun song(
        id: Long = 1,
        path: String = "/storage/emulated/0/Music/song.mp3",
        provider: MediaProviderType = MediaProviderType.Shuttle,
        externalId: String? = null,
        name: String? = "Song",
        album: String? = "Album",
        artists: List<String> = listOf("Artist"),
        duration: Int = 200,
        size: Long = 1000,
        playCount: Int = 0,
        lastPlayed: Instant? = null,
        lastCompleted: Instant? = null,
        playbackPosition: Int = 0,
        blacklisted: Boolean = false,
        favouritedAt: Instant? = null,
        dateAdded: Instant? = null
    ) = Song(
        id = id,
        name = name,
        albumArtist = null,
        artists = artists,
        album = album,
        track = 1,
        disc = 1,
        duration = duration,
        date = null,
        genres = emptyList(),
        path = path,
        size = size,
        mimeType = "audio/mpeg",
        lastModified = null,
        lastPlayed = lastPlayed,
        lastCompleted = lastCompleted,
        playCount = playCount,
        playbackPosition = playbackPosition,
        blacklisted = blacklisted,
        externalId = externalId,
        mediaProvider = provider,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null,
        dateAdded = dateAdded,
        favouritedAt = favouritedAt
    )

    private fun identityOf(song: Song) = SongIdentity(
        provider = song.mediaProvider.name,
        path = song.path,
        externalId = song.externalId,
        title = song.name,
        album = song.album,
        artist = song.artists.joinToString(", "),
        duration = song.duration,
        size = song.size
    )

    @Test
    fun `exact path match wins`() {
        val library = listOf(song())
        val identity = identityOf(song())
        val match = LibraryBackupMatcher.matchAll(listOf(identity), library)[identity]
        match.shouldNotBeNull()
        match.kind shouldBe LibraryBackupMatcher.MatchKind.ExactPath
    }

    @Test
    fun `sd-card volume change matches by relative path`() {
        val library = listOf(song(path = "/storage/ABCD-1234/Music/song.mp3"))
        val identity = identityOf(song(path = "/storage/emulated/0/Music/song.mp3"))
        val match = LibraryBackupMatcher.matchAll(listOf(identity), library)[identity]
        match.shouldNotBeNull()
        match.kind shouldBe LibraryBackupMatcher.MatchKind.RelativePath
    }

    @Test
    fun `remote items match by external id when paths differ`() {
        val library = listOf(
            song(
                provider = MediaProviderType.Jellyfin,
                path = "jellyfin://item/aaa",
                externalId = "aaa",
                name = "Renamed Since"
            )
        )
        val identity = identityOf(
            song(provider = MediaProviderType.Jellyfin, path = "jellyfin://item/changed", externalId = "aaa", name = "Original")
        )
        val match = LibraryBackupMatcher.matchAll(listOf(identity), library)[identity]
        match.shouldNotBeNull()
        match.kind shouldBe LibraryBackupMatcher.MatchKind.ExternalId
    }

    @Test
    fun `retagged file matches fuzzily within duration tolerance`() {
        val library = listOf(song(path = "/storage/emulated/0/Other/renamed.mp3"))
        val identity = identityOf(song(path = "/storage/emulated/0/Music/song.mp3", duration = 201))
        val match = LibraryBackupMatcher.matchAll(listOf(identity), library)[identity]
        match.shouldNotBeNull()
        match.kind shouldBe LibraryBackupMatcher.MatchKind.Fuzzy
    }

    @Test
    fun `unknown file does not match`() {
        val library = listOf(song())
        val identity = identityOf(song(name = "Something Else", album = "Other", path = "/x/y.mp3"))
        LibraryBackupMatcher.matchAll(listOf(identity), library)[identity].shouldBeNull()
    }

    @Test
    fun `restore overwrites with backup snapshot`() {
        val playedLater = Instant.fromEpochMilliseconds(2_000_000)
        val current = song(playCount = 3, lastPlayed = playedLater, playbackPosition = 10, blacklisted = false)
        val backup = BackedUpSong(
            identity = identityOf(current),
            playCount = 1,
            lastPlayed = 1_000_000,
            playbackPosition = 99,
            excluded = true,
            favouritedAt = 500_000
        )
        val merged = LibraryBackupMatcher.mergeStats(current, backup)
        // Backup wins even when the device has played more since: 1 overwrites 2.
        merged.playCount shouldBe 1
        merged.lastPlayed shouldBe Instant.fromEpochMilliseconds(1_000_000)
        merged.playbackPosition shouldBe 99
        merged.excluded shouldBe true
        merged.favouritedAt shouldBe Instant.fromEpochMilliseconds(500_000)
    }

    @Test
    fun `restore clears values missing from backup`() {
        val current = song(
            playCount = 2,
            lastPlayed = Instant.fromEpochMilliseconds(2_000_000),
            playbackPosition = 10,
            blacklisted = true,
            favouritedAt = Instant.fromEpochMilliseconds(2_000_000)
        )
        val backup = BackedUpSong(identity = identityOf(current), playCount = 0)
        val merged = LibraryBackupMatcher.mergeStats(current, backup)
        merged.playCount shouldBe 0
        merged.lastPlayed.shouldBeNull()
        merged.playbackPosition shouldBe 0
        merged.excluded shouldBe false
        merged.favouritedAt.shouldBeNull()
        LibraryBackupMatcher.statsEqual(current, merged) shouldBe false
    }

    @Test
    fun `backup round-trips through json`() {
        val backup = LibraryBackup(
            exportedAt = 1_700_000_000_000,
            songs = listOf(
                BackedUpSong(identityOf(song()), playCount = 7, favouritedAt = 123_456)
            ),
            playlists = listOf(
                BackedUpPlaylist(name = "Mix", provider = "Shuttle", members = listOf(identityOf(song())))
            )
        )
        val decoded = json.decodeFromString<LibraryBackup>(json.encodeToString(LibraryBackup.serializer(), backup))
        decoded.songs.single().playCount shouldBe 7
        decoded.playlists.single().members.single().path shouldBe "/storage/emulated/0/Music/song.mp3"
        decoded.schemaVersion shouldBe LibraryBackup.CURRENT_SCHEMA_VERSION
    }
}
