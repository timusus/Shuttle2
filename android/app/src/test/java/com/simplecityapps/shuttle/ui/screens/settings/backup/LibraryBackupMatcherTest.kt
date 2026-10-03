package com.simplecityapps.shuttle.ui.screens.settings.backup

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import org.junit.Test

class LibraryBackupMatcherTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun song(
        id: Long = 1,
        path: String = "/storage/emulated/0/Music/song.mp3",
        provider: MediaProviderType = MediaProviderType.Shuttle,
        externalId: String? = null,
        name: String? = "Song",
        album: String? = "Album",
        albumArtist: String? = null,
        artists: List<String> = listOf("Artist"),
        duration: Int = 200_000,
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
        albumArtist = albumArtist,
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
        artist = LibraryBackupMatcher.fingerprintArtist(song),
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
        val identity = identityOf(song(path = "/storage/emulated/0/Music/song.mp3", duration = 201_000))
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

    private fun match(
        identity: SongIdentity,
        vararg library: Song
    ) = LibraryBackupMatcher.matchAll(listOf(identity), library.toList())[identity]

    @Test
    fun `exact path beats relative path, external id and fuzzy`() {
        val exact = song(id = 1, path = "/storage/emulated/0/Music/song.mp3")
        val relative = song(id = 2, path = "/storage/ABCD-1234/Music/song.mp3", externalId = "x")
        val identity = identityOf(exact).copy(externalId = "x")
        match(identity, relative, exact)?.song shouldBe exact
    }

    @Test
    fun `relative path beats external id and fuzzy`() {
        val relative = song(id = 2, path = "/storage/ABCD-1234/Music/song.mp3")
        val byId = song(id = 3, path = "/other/a.mp3", externalId = "x")
        val identity = identityOf(song(path = "/storage/emulated/0/Music/song.mp3", externalId = "x"))
        match(identity, byId, relative)?.let {
            it.song shouldBe relative
            it.kind shouldBe LibraryBackupMatcher.MatchKind.RelativePath
        }
    }

    @Test
    fun `external id beats fuzzy`() {
        val byId = song(id = 3, path = "/other/a.mp3", externalId = "x", name = "Different")
        val fuzzy = song(id = 4, path = "/other/b.mp3")
        val identity = identityOf(song(path = "/gone.mp3", externalId = "x"))
        match(identity, fuzzy, byId)?.let {
            it.song shouldBe byId
            it.kind shouldBe LibraryBackupMatcher.MatchKind.ExternalId
        }
    }

    @Test
    fun `duration tolerance is 2000 ms inclusive`() {
        val library = song(path = "/other/a.mp3", duration = 200_000)
        match(identityOf(song(path = "/x.mp3", duration = 202_000)), library).shouldNotBeNull()
        match(identityOf(song(path = "/x.mp3", duration = 198_000)), library).shouldNotBeNull()
        match(identityOf(song(path = "/x.mp3", duration = 202_001)), library).shouldBeNull()
    }

    @Test
    fun `album artist and multi-artist songs match their own fingerprint`() {
        val albumArtist = song(path = "/other/a.mp3", albumArtist = "Band", artists = listOf("Band", "Guest"))
        match(identityOf(song(path = "/x.mp3", albumArtist = "Band", artists = listOf("Band", "Guest"))), albumArtist)?.kind shouldBe
            LibraryBackupMatcher.MatchKind.Fuzzy
        val multi = song(path = "/other/b.mp3", artists = listOf("A", "B"))
        match(identityOf(song(path = "/y.mp3", artists = listOf("A", "B"))), multi)?.kind shouldBe LibraryBackupMatcher.MatchKind.Fuzzy
    }

    @Test
    fun `nothing matches across providers`() {
        val jellyfin = song(provider = MediaProviderType.Jellyfin, path = "jellyfin://a", externalId = "x")
        val identity = identityOf(song(provider = MediaProviderType.Emby, path = "jellyfin://a", externalId = "x"))
        match(identity, jellyfin).shouldBeNull()
        match(identityOf(song(provider = MediaProviderType.Emby, path = "/storage/emulated/0/Music/song.mp3")), song(path = "/storage/ABCD-1234/Music/song.mp3")).shouldBeNull()
    }

    @Test
    fun `blank tags never match fuzzily`() {
        val library = song(path = "/other/a.mp3", name = null, album = null, artists = emptyList())
        match(identityOf(song(path = "/x.mp3", name = null, album = null, artists = emptyList())), library).shouldBeNull()
    }

    @Test
    fun `size breaks ties between fuzzy candidates`() {
        val small = song(id = 1, path = "/a.mp3", size = 1000)
        val big = song(id = 2, path = "/b.mp3", size = 2000)
        match(identityOf(song(path = "/gone.mp3", size = 2000)), small, big)?.song shouldBe big
    }

    @Test
    fun `a library song is never claimed twice`() {
        val library = song(id = 1, path = "/other/a.mp3")
        val a = identityOf(song(path = "/gone1.mp3"))
        val b = identityOf(song(path = "/gone2.mp3"))
        val result = LibraryBackupMatcher.matchAll(listOf(a, b), listOf(library))
        result.values.count { it != null } shouldBe 1
    }

    @Test
    fun `merge keeps the larger count and the later times`() {
        val current = song(playCount = 3, lastPlayed = Instant.fromEpochMilliseconds(2_000), lastCompleted = Instant.fromEpochMilliseconds(5_000))
        val older = LibraryBackupMatcher.mergeStats(current, BackedUpSong(identityOf(current), playCount = 1, lastPlayed = 1_000, lastCompleted = 1_000))
        older.playCount shouldBe 3
        older.lastPlayed shouldBe Instant.fromEpochMilliseconds(2_000)
        older.lastCompleted shouldBe Instant.fromEpochMilliseconds(5_000)
        val newer = LibraryBackupMatcher.mergeStats(current, BackedUpSong(identityOf(current), playCount = 9, lastPlayed = 3_000, lastCompleted = 6_000))
        newer.playCount shouldBe 9
        newer.lastPlayed shouldBe Instant.fromEpochMilliseconds(3_000)
        newer.lastCompleted shouldBe Instant.fromEpochMilliseconds(6_000)
    }

    @Test
    fun `merge takes the backup position only when it was played more recently`() {
        val current = song(lastPlayed = Instant.fromEpochMilliseconds(2_000), playbackPosition = 10)
        LibraryBackupMatcher.mergeStats(current, BackedUpSong(identityOf(current), lastPlayed = 1_000, playbackPosition = 99)).playbackPosition shouldBe 10
        LibraryBackupMatcher.mergeStats(current, BackedUpSong(identityOf(current), lastPlayed = 3_000, playbackPosition = 99)).playbackPosition shouldBe 99
        LibraryBackupMatcher.mergeStats(current, BackedUpSong(identityOf(current), lastPlayed = null, playbackPosition = 99)).playbackPosition shouldBe 10
        LibraryBackupMatcher.mergeStats(song(), BackedUpSong(identityOf(song()), lastPlayed = 3_000, playbackPosition = 99)).playbackPosition shouldBe 99
    }

    @Test
    fun `merge keeps a favourite from either side`() {
        val favourite = Instant.fromEpochMilliseconds(7_000)
        val onDevice = song(favouritedAt = favourite)
        LibraryBackupMatcher.mergeStats(onDevice, BackedUpSong(identityOf(onDevice))).favouritedAt shouldBe favourite
        val plain = song()
        LibraryBackupMatcher.mergeStats(plain, BackedUpSong(identityOf(plain), favouritedAt = 8_000)).favouritedAt shouldBe Instant.fromEpochMilliseconds(8_000)
        LibraryBackupMatcher.mergeStats(plain, BackedUpSong(identityOf(plain))).favouritedAt.shouldBeNull()
    }

    @Test
    fun `merge keeps an exclusion from either side`() {
        LibraryBackupMatcher.mergeStats(song(blacklisted = true), BackedUpSong(identityOf(song()), excluded = false)).excluded shouldBe true
        LibraryBackupMatcher.mergeStats(song(), BackedUpSong(identityOf(song()), excluded = true)).excluded shouldBe true
        LibraryBackupMatcher.mergeStats(song(), BackedUpSong(identityOf(song()), excluded = false)).excluded shouldBe false
    }

    @Test
    fun `merge takes the earlier date added`() {
        val current = song(dateAdded = Instant.fromEpochMilliseconds(5_000))
        LibraryBackupMatcher.mergeStats(current, BackedUpSong(identityOf(current), dateAdded = 1_000)).dateAdded shouldBe Instant.fromEpochMilliseconds(1_000)
        LibraryBackupMatcher.mergeStats(current, BackedUpSong(identityOf(current), dateAdded = 9_000)).dateAdded shouldBe Instant.fromEpochMilliseconds(5_000)
        LibraryBackupMatcher.mergeStats(current, BackedUpSong(identityOf(current), dateAdded = null)).dateAdded shouldBe Instant.fromEpochMilliseconds(5_000)
        LibraryBackupMatcher.mergeStats(song(), BackedUpSong(identityOf(song()), dateAdded = 9_000)).dateAdded shouldBe Instant.fromEpochMilliseconds(9_000)
    }

    @Test
    fun `merge ignores negative backup values`() {
        val current = song(playCount = 2, playbackPosition = 5)
        val merged = LibraryBackupMatcher.mergeStats(
            current,
            BackedUpSong(identityOf(current), playCount = -4, lastPlayed = -1, lastCompleted = -1, playbackPosition = -9, favouritedAt = -1, dateAdded = -1)
        )
        merged.playCount shouldBe 2
        merged.playbackPosition shouldBe 5
        merged.lastPlayed.shouldBeNull()
        merged.lastCompleted.shouldBeNull()
        merged.favouritedAt.shouldBeNull()
        merged.dateAdded.shouldBeNull()
        LibraryBackupMatcher.mergeStats(song(), BackedUpSong(identityOf(song()), playCount = -4, playbackPosition = -9)).playCount shouldBe 0
    }

    @Test
    fun `missing members are the backup songs not already present, once each`() {
        val a = song(id = 1)
        val b = song(id = 2)
        val c = song(id = 3)
        LibraryBackupMatcher.missingMembers(existingIds = setOf(c.id, a.id), backup = listOf(a, b, b, c)) shouldBe listOf(b)
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
