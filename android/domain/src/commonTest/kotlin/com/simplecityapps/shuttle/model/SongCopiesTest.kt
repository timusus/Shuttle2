package com.simplecityapps.shuttle.model

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant

class SongCopiesTest {
    private val grouping = SongCopyGrouping()

    private val titles = listOf("Atlas", "Cazenove", "Apricots", "Lamplight", "Heartbreak", "Rever", "Ode", "Rain", "Saku", "X", "Hawk", "Orca")

    private fun song(
        id: Long,
        track: Int,
        provider: MediaProviderType = MediaProviderType.Shuttle,
        name: String = titles[track - 1],
        mimeType: String = if (provider.remote) "audio/mpeg" else "audio/flac",
        bitRate: Int? = if (provider.remote) 320 else 900,
        audioCodec: String? = null,
        mbTrackId: String? = null,
        duration: Int = 240_000 + track * 1_000,
        playCount: Int = 0,
        lastPlayed: Instant? = null,
        playbackPosition: Int = 0,
        favouritedAt: Instant? = null,
        path: String = "${provider.name}/$id"
    ) = Song(
        id = id,
        name = name,
        albumArtist = "Bicep",
        artists = listOf("Bicep"),
        album = "Isles",
        track = track,
        disc = if (provider.remote) null else 1,
        duration = duration,
        date = null,
        genres = emptyList(),
        path = path,
        size = 0,
        mimeType = mimeType,
        lastModified = null,
        lastPlayed = lastPlayed,
        lastCompleted = null,
        playCount = playCount,
        playbackPosition = playbackPosition,
        blacklisted = false,
        mediaProvider = provider,
        lyrics = null,
        grouping = null,
        bitRate = bitRate,
        bitDepth = null,
        sampleRate = null,
        channelCount = null,
        audioCodec = audioCodec,
        favouritedAt = favouritedAt,
        mbTrackId = mbTrackId,
        serverAlbumId = if (provider.remote) "isles" else null
    )

    private fun local(tracks: IntRange) = tracks.map { song(id = it.toLong(), track = it) }

    private fun emby(tracks: IntRange) = tracks.map { song(id = 100L + it, track = it, provider = MediaProviderType.Emby) }

    // Two servers' album ids differ, so only a shared MusicBrainz release makes their songs one album
    private fun onOneRelease(songs: List<Song>) = songs.map { it.copy(mbAlbumId = "mb-isles") }

    private fun merge(
        songs: List<Song>,
        ranking: Comparator<Song> = SongCopyAvailability().ranking()
    ) = grouping.merge(songs.withAlbumIdentities(), ranking)

    @Test
    fun `an album in both sources lists each of its twelve songs once`() {
        val merged = merge(local(1..12) + emby(1..12))

        merged.map { it.id } shouldBe (1L..12L).toList()
        merged.map { song -> song.copies.map { it.id } } shouldBe (1L..12L).map { listOf(it, 100L + it) }
    }

    @Test
    fun `ten local songs and twelve server songs of one album are twelve songs`() {
        val merged = merge(local(1..10) + emby(1..12))

        merged.map { it.id } shouldBe (1L..10L).toList() + listOf(111L, 112L)
        merged.drop(10).map { it.copies } shouldBe listOf(emptyList(), emptyList())
    }

    @Test
    fun `a source's own duplicates stay apart`() {
        val duplicate = song(id = 50, track = 1, path = "Shuttle/copy-of-1")

        merge(local(1..1) + duplicate).map { it.id } shouldBe listOf(1L, 50L)
        merge(local(1..1) + duplicate + emby(1..1)).map { song -> song.id to song.copies.map { it.id } } shouldBe
            listOf(1L to listOf(1L, 101L), 50L to emptyList())
    }

    @Test
    fun `a song in one source only is returned unchanged`() {
        val songs = local(1..3).withAlbumIdentities()

        grouping.merge(songs) shouldBe songs
    }

    @Test
    fun `the same recording id on one album merges copies whose titles differ`() {
        val songs = listOf(
            song(id = 1, track = 1, mbTrackId = "rec-1"),
            song(id = 101, track = 1, provider = MediaProviderType.Emby, name = "Atlas (Live)", mbTrackId = "REC-1")
        ).withAlbumIdentities()

        grouping.merge(songs).map { it.id } shouldBe listOf(1L)
        SongCopyGrouping(useRecordingIds = false).merge(songs).map { it.id } shouldBe listOf(1L, 101L)
    }

    @Test
    fun `the plain tag rule pairs copies without recording ids`() {
        val songs = (local(1..2) + emby(1..2)).map { it.copy(mbTrackId = null) }.withAlbumIdentities()

        SongCopyGrouping(useRecordingIds = false).merge(songs).map { it.id } shouldBe listOf(1L, 2L)
    }

    @Test
    fun `the default copy is a local file over a server stream`() {
        merge(emby(1..1) + local(1..1)).single().copies.map { it.id } shouldBe listOf(1L, 101L)
    }

    @Test
    fun `a missing local file gives way to the server copy`() {
        val ranking = SongCopyAvailability(missingSongIds = setOf(1L)).ranking()

        merge(local(1..1) + emby(1..1), ranking).single().id shouldBe 101L
    }

    @Test
    fun `offline a downloaded server copy beats a missing local file and an undownloaded one`() {
        val jellyfin = song(id = 201, track = 1, provider = MediaProviderType.Jellyfin)
        val ranking = SongCopyAvailability(offline = true, downloadedPaths = setOf(jellyfin.path), missingSongIds = setOf(1L)).ranking()

        merge(local(1..1) + emby(1..1) + jellyfin, ranking).single().copies.map { it.id } shouldBe listOf(201L, 1L, 101L)
    }

    @Test
    fun `an unreachable source ranks after a reachable one`() {
        val jellyfin = song(id = 201, track = 1, provider = MediaProviderType.Jellyfin)
        val ranking = SongCopyAvailability(unreachableProviders = setOf(MediaProviderType.Emby)).ranking()

        merge(onOneRelease(emby(1..1) + jellyfin), ranking).single().id shouldBe 201L
    }

    @Test
    fun `between server copies lossless wins then the higher bitrate then the source order`() {
        val embyMp3 = song(id = 101, track = 1, provider = MediaProviderType.Emby, bitRate = 320)
        val jellyfinFlac = song(id = 201, track = 1, provider = MediaProviderType.Jellyfin, mimeType = "audio/mpeg", audioCodec = "flac", bitRate = 1000)
        val plexMp3 = song(id = 301, track = 1, provider = MediaProviderType.Plex, bitRate = 320)
        val subsonicMp3 = song(id = 401, track = 1, provider = MediaProviderType.Subsonic, bitRate = 256)

        merge(onOneRelease(listOf(subsonicMp3, plexMp3, embyMp3, jellyfinFlac))).single().copies.map { it.id } shouldBe listOf(201L, 101L, 301L, 401L)
    }

    @Test
    fun `a preference outranks quality but not playability`() {
        val preferEmby = compareByDescending<Song> { it.mediaProvider == MediaProviderType.Emby }

        merge(local(1..1) + emby(1..1), SongCopyAvailability().ranking(preferEmby)).single().id shouldBe 101L
        merge(local(1..1) + emby(1..1), SongCopyAvailability(offline = true).ranking(preferEmby)).single().id shouldBe 1L
    }

    @Test
    fun `a merged song carries the history of all its copies`() {
        val earlier = Instant.fromEpochSeconds(1_000)
        val later = Instant.fromEpochSeconds(2_000)
        val songs = listOf(
            song(id = 1, track = 1, playCount = 3, lastPlayed = earlier, playbackPosition = 10),
            song(id = 101, track = 1, provider = MediaProviderType.Emby, playCount = 2, lastPlayed = later, playbackPosition = 20, favouritedAt = later)
        )

        val merged = merge(songs).single()

        merged.id shouldBe 1L
        merged.playCount shouldBe 5
        merged.lastPlayed shouldBe later
        merged.playbackPosition shouldBe 20
        merged.favouritedAt shouldBe later
        merged.copies.map { it.playCount } shouldBe listOf(3, 2)
    }
}
