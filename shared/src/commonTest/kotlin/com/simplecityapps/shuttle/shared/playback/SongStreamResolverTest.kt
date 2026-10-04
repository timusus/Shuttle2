package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.mediaprovider.StreamUrlProvider
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.shuttle.entitlement.ServerAccess
import com.simplecityapps.shuttle.model.Song
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class SongStreamResolverTest {
    private class FakeStreamUrls(
        private val scheme: String
    ) : StreamUrlProvider {
        /** The play each stream was asked for under, in order. */
        val plays = mutableListOf<String?>()

        /** The plays ended, in order. */
        val endedPlays = mutableListOf<String>()

        override fun handles(scheme: String?): Boolean = scheme == this.scheme

        override suspend fun endPlay(playId: String) {
            endedPlays += playId
        }

        override fun streamUrl(
            song: Song,
            startPositionMs: Long,
            playId: String?
        ): String = if (song.name == "signed out") {
            throw IllegalStateException("Failed to authenticate")
        } else {
            plays += playId
            "https://$scheme.example/Audio/${song.path.substringAfterLast('/')}/universal" +
                if (startPositionMs > 0) "?StartTimeTicks=${startPositionMs * 10_000}" else ""
        }
    }

    private val jellyfin = FakeStreamUrls("jellyfin")
    private val plex = FakeStreamUrls("plex")

    private var replayGainMode = ReplayGainMode.Off
    private var preAmpGainDb = 0f
    private var serverAccess = ServerAccess.Allowed
    private val askedToStream = mutableListOf<String>()
    private val downloaded = mutableMapOf<String, String>()

    private val resolver = SongStreamResolver(
        listOf(jellyfin, FakeStreamUrls("emby"), plex),
        replayGainMode = { replayGainMode },
        preAmpGainDb = { preAmpGainDb },
        serverStreamAccess = { song, _ ->
            askedToStream += song.path
            serverAccess
        },
        localFiles = object : StreamUrlProvider {
            override fun handles(scheme: String?): Boolean = scheme == "s2local"

            override fun streamUrl(
                song: Song,
                startPositionMs: Long,
                playId: String?
            ): String = if (song.name == "gone") throw IllegalStateException("Out of reach") else "file:///Documents/a%20b.flac"
        },
        downloadedFile = { path -> downloaded[path] }
    )

    @Test
    fun aLocalSongPlaysFromItsFileFromTheStart() = runTest {
        resolver.resolve(songAt(path = "s2local://documents/a b.flac"), 30_000, playRequested = true, playId = "play-1") shouldBe
            IosStream(url = "file:///Documents/a%20b.flac", opensAtPosition = false)
        shouldThrow<IllegalStateException> { resolver.resolve(songAt(path = "s2local://documents/a b.flac", name = "gone"), 0, playRequested = true, playId = "play-1") }
    }

    @Test
    fun aLocalSongPlaysWithoutAskingTheGateWhateverItsAnswer() = runTest {
        for (access in listOf(ServerAccess.Undecided, ServerAccess.Refused)) {
            serverAccess = access
            resolver.resolve(songAt(path = "s2local://documents/a b.flac"), 0, playRequested = true, playId = "play-1").url shouldBe "file:///Documents/a%20b.flac"
        }
        askedToStream shouldBe emptyList()
    }

    @Test
    fun aServerStreamIsAskedForUnderItsPlayAndTheEndOfThePlayGoesToTheSameProvider() = runTest {
        resolver.resolve(songAt(path = "plex:///library/metadata/1"), 0, playRequested = true, playId = "play-7")
        resolver.endPlay(songAt(path = "plex:///library/metadata/1"), "play-7")

        plex.plays shouldBe listOf("play-7")
        plex.endedPlays shouldBe listOf("play-7")
        jellyfin.endedPlays shouldBe emptyList()
    }

    @Test
    fun serverSongsStreamFromTheProviderThatHandlesTheirPath() = runTest {
        resolver.resolve(songAt(path = "jellyfin://item/abc"), 0, playRequested = true, playId = "play-1") shouldBe
            IosStream(url = "https://jellyfin.example/Audio/abc/universal", opensAtPosition = true)
        resolver.resolve(songAt(path = "emby://item/def"), 0, playRequested = true, playId = "play-1") shouldBe
            IosStream(url = "https://emby.example/Audio/def/universal", opensAtPosition = true)
        resolver.resolve(songAt(path = "plex:///library/metadata/1"), 0, playRequested = true, playId = "play-1") shouldBe
            IosStream(url = "https://plex.example/Audio/1/universal", opensAtPosition = true)
    }

    @Test
    fun aServerStreamOpensAtThePositionAskedFor() = runTest {
        resolver.resolve(songAt(path = "jellyfin://item/abc"), 30_000, playRequested = true, playId = "play-1").url shouldBe
            "https://jellyfin.example/Audio/abc/universal?StartTimeTicks=300000000"
    }

    @Test
    fun aDownloadedServerSongPlaysFromItsFileFromTheStart() = runTest {
        downloaded["jellyfin://item/abc"] = "file:///Downloads/amVsbHlmaW46Ly9pdGVtL2FiYw.flac"

        resolver.resolve(songAt(path = "jellyfin://item/abc", name = "signed out"), 30_000, playRequested = true, playId = "play-1") shouldBe
            IosStream(url = "file:///Downloads/amVsbHlmaW46Ly9pdGVtL2FiYw.flac", opensAtPosition = false)
    }

    @Test
    fun aDownloadedServerSongPlaysWithoutAskingTheGateWhateverItsAnswer() = runTest {
        downloaded["jellyfin://item/abc"] = "file:///Downloads/amVsbHlmaW46Ly9pdGVtL2FiYw.flac"
        for (access in listOf(ServerAccess.Undecided, ServerAccess.Refused)) {
            serverAccess = access
            resolver.resolve(songAt(path = "jellyfin://item/abc"), 0, playRequested = true, playId = "play-1") shouldBe
                IosStream(url = "file:///Downloads/amVsbHlmaW46Ly9pdGVtL2FiYw.flac", opensAtPosition = false)
        }
        askedToStream shouldBe emptyList()
    }

    @Test
    fun aServerSongWithNoDownloadedFileIsRefusedWhenTheGateRefuses() = runTest {
        downloaded["jellyfin://item/other"] = "file:///Downloads/other.flac"
        serverAccess = ServerAccess.Refused

        shouldThrow<ServerStreamNotAllowedException> { resolver.resolve(songAt(path = "jellyfin://item/abc"), 0, playRequested = true, playId = "play-1") }
        askedToStream shouldBe listOf("jellyfin://item/abc")
    }

    @Test
    fun aFilePathBecomesAnEscapedFileUrl() = runTest {
        resolver.resolve(songAt(path = "/Music/Björk/Post/03 Hyperballad.flac"), 0, playRequested = true, playId = "play-1") shouldBe
            IosStream(url = "file:///Music/Bj%C3%B6rk/Post/03%20Hyperballad.flac", opensAtPosition = false)
    }

    @Test
    fun anyOtherPathPassesThrough() = runTest {
        resolver.resolve(songAt(path = "https://example.com/song.mp3"), 0, playRequested = true, playId = "play-1") shouldBe IosStream(url = "https://example.com/song.mp3")
        resolver.resolve(songAt(path = "demo://1"), 0, playRequested = true, playId = "play-1").url shouldBe "demo://1"
    }

    @Test
    fun aProviderThatCantBuildTheUrlFailsTheSong() = runTest {
        shouldThrow<IllegalStateException> { resolver.resolve(songAt(path = "jellyfin://item/abc", name = "signed out"), 0, playRequested = true, playId = "play-1") }
    }

    @Test
    fun aServerSongTheGateRefusesFailsTheSong() = runTest {
        serverAccess = ServerAccess.Refused

        shouldThrow<ServerStreamNotAllowedException> { resolver.resolve(songAt(path = "jellyfin://item/abc"), 0, playRequested = true, playId = "play-1") }
            .undecided shouldBe false
    }

    @Test
    fun aPlexSongAsksTheGateLikeAnyServerSong() = runTest {
        serverAccess = ServerAccess.Refused

        shouldThrow<ServerStreamNotAllowedException> { resolver.resolve(songAt(path = "plex:///library/metadata/1"), 0, playRequested = true, playId = "play-1") }
        askedToStream shouldBe listOf("plex:///library/metadata/1")
    }

    @Test
    fun aServerSongTheGateCantDecideYetFailsAsUndecided() = runTest {
        serverAccess = ServerAccess.Undecided

        shouldThrow<ServerStreamNotAllowedException> { resolver.resolve(songAt(path = "jellyfin://item/abc"), 0, playRequested = true, playId = "play-1") }
            .undecided shouldBe true
    }

    @Test
    fun onlyServerSongsAskTheGate() = runTest {
        serverAccess = ServerAccess.Refused

        resolver.resolve(songAt(path = "/Music/song.flac"), 0, playRequested = true, playId = "play-1").url shouldBe "file:///Music/song.flac"
        resolver.resolve(songAt(path = "demo://1"), 0, playRequested = true, playId = "play-1").url shouldBe "demo://1"
        askedToStream shouldBe emptyList()
    }

    @Test
    fun theStreamCarriesTheReplayGainTheModeChooses() = runTest {
        val tagged = songAt(path = "/Music/a.flac").copy(replayGainTrack = -6.5, replayGainAlbum = -3.0)
        preAmpGainDb = 2f

        replayGainMode = ReplayGainMode.Track
        resolver.resolve(tagged, 0, playRequested = true, playId = "play-1").gainDb shouldBe -4.5f
        replayGainMode = ReplayGainMode.Album
        resolver.resolve(tagged, 0, playRequested = true, playId = "play-1").gainDb shouldBe -1f
        replayGainMode = ReplayGainMode.Off
        resolver.resolve(tagged, 0, playRequested = true, playId = "play-1").gainDb shouldBe 2f
    }

    @Test
    fun aMissingTagFallsBackToTheOtherOne() = runTest {
        replayGainMode = ReplayGainMode.Album
        resolver.resolve(songAt(path = "jellyfin://item/abc").copy(replayGainTrack = -7.0), 0, playRequested = true, playId = "play-1").gainDb shouldBe -7f
        resolver.resolve(songAt(path = "jellyfin://item/abc"), 0, playRequested = true, playId = "play-1").gainDb shouldBe 0f
    }

    private fun songAt(
        path: String,
        name: String = "Song"
    ) = song(id = 1, path = path).copy(name = name)
}
