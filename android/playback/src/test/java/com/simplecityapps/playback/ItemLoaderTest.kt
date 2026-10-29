package com.simplecityapps.playback

import android.net.Uri
import android.os.Looper
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.TestExoPlayerBuilder
import com.simplecityapps.playback.PlaybackPolicy
import com.simplecityapps.playback.engine.MediaResolutionException
import com.simplecityapps.playback.engine.S2ShuffleOrder
import com.simplecityapps.playback.engine.SongUriResolver
import com.simplecityapps.playback.exoplayer.MediaResolver
import com.simplecityapps.playback.exoplayer.ResolvedMedia
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.queue.QueueFacade
import com.simplecityapps.playback.queue.ShuffleOrder
import com.simplecityapps.playback.queue.queueEntryOrNull
import com.simplecityapps.playback.queue.shuffledIndices
import com.simplecityapps.playback.queue.toMediaItem
import com.simplecityapps.playback.queue.toQueueEntry
import com.simplecityapps.playback.queue.uri
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.playback.spec.ClockDriver
import com.simplecityapps.playback.spec.PlaybackHarness
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SourceStateStore
import com.simplecityapps.shuttle.settings.SettingsStore
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** Loading the current item and skipping ones that fail, on a real player: the behaviour is RS-10, RS-23 and RS-56. */
// Robolectric: drives a real TestExoPlayerBuilder-backed ExoPlayer.
@RunWith(RobolectricTestRunner::class)
class ItemLoaderTest {
    private val clock = FakeClock(false)

    private val player: ExoPlayer = TestExoPlayerBuilder(RuntimeEnvironment.getApplication()).setClock(clock).build()

    private val driver = ClockDriver(clock, player)

    private var gaveUp = 0

    /** A song streamed from a transcode the server drops when another starts (as Plex does): its own file, as that's what tells it apart. */
    private val transcode = PlaybackHarness.song(3, file = PlaybackHarness.TONE_3S)

    private val sourceState = SourceStateStore(InMemoryKeyValueStore())

    private val queueFacade =
        QueueFacade(
            player,
            PlaybackSettings(SettingsStore(InMemoryKeyValueStore())),
            SongUriResolver(MediaResolver { song, _ -> ResolvedMedia(uri = song.path, mimeType = song.mimeType, isRemote = false) }, Dispatchers.IO),
            buildContext = Dispatchers.Unconfined,
            mainImmediateDispatcher = Dispatchers.Main.immediate
        )

    private val loader =
        ItemLoader(
            player,
            player,
            giveUp = { gaveUp++ },
            isReplaceableTranscode = { uri -> uri == transcode.uri() },
            sourceState = sourceState,
            replaceSong = queueFacade::replaceSong
        ).also(player::addListener)

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private val failures = mutableListOf<Song>().also { failures -> scope.launch { loader.failureFlow.collect { failures += it } } }

    private val missing = testSong(1, path = PlaybackHarness.MISSING_FILE_URI, mimeType = "audio/wav")

    private val playable = PlaybackHarness.song(2)

    @After
    fun tearDown() {
        scope.cancel()
        player.release()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun queue(vararg songs: Song) = player.setMediaItems(songs.map { it.toQueueEntry().toMediaItem() })

    private fun load(skipUnloadable: Boolean): Result<Boolean> {
        var result: Result<Boolean>? = null
        loader.load(0, skipUnloadable) { result = it }
        loader.isLoading shouldBe true
        driver.runUntil { result != null }
        return checkNotNull(result)
    }

    @Test
    fun `an item that loads is ready at the first attempt`() {
        queue(playable)

        load(skipUnloadable = true) shouldBe Result.success(true)

        loader.isLoading shouldBe false
        loader.isCurrentReady shouldBe true
    }

    @Test
    fun `an item that fails to load is reported and skipped for the next`() {
        queue(missing, playable)

        load(skipUnloadable = true) shouldBe Result.success(false)

        player.currentMediaItemIndex shouldBe 1
        failures shouldBe listOf(missing)
        gaveUp shouldBe 0
    }

    @Test
    fun `a load that doesn't skip leaves a failed item current, and gives up`() {
        queue(missing, playable)

        load(skipUnloadable = false).isFailure shouldBe true

        player.currentMediaItemIndex shouldBe 0
        loader.isCurrentReady shouldBe false
        gaveUp shouldBe 1
    }

    @Test
    fun `with nothing after it to skip to, a failed load gives up`() {
        queue(playable, missing)
        player.seekTo(1, 0)

        load(skipUnloadable = true).isFailure shouldBe true

        gaveUp shouldBe 1
    }

    @Test
    fun `a load replaced before its item is ready is dropped - and the new one completes`() {
        queue(playable)
        var replaced: Result<Boolean>? = null
        loader.load(0, skipUnloadable = true) { replaced = it }

        load(skipUnloadable = true) shouldBe Result.success(true)

        (replaced?.exceptionOrNull() is CancellationException) shouldBe true
    }

    @Test
    fun `a 404 on a replaced transcode opens it again at the position - playing on`() {
        queue(transcode)
        load(skipUnloadable = true)
        player.play()
        driver.runUntil { player.currentPosition >= 500 }
        val position = player.currentPosition

        failWithNotFound()

        player.playerError shouldBe null
        player.playWhenReady shouldBe true
        driver.runUntil { player.isPlaying }
        player.currentMediaItemIndex shouldBe 0
        player.currentPosition shouldBeGreaterThanOrEqual position
        failures shouldBe emptyList()
        gaveUp shouldBe 0
    }

    @Test
    fun `a transcode that 404s again at the same position gives up`() {
        queue(transcode)
        load(skipUnloadable = true)
        player.seekTo(0, 1_000)
        driver.runUntil { player.playbackState == Player.STATE_READY }

        failWithNotFound()
        driver.runUntil { player.playbackState == Player.STATE_READY }
        gaveUp shouldBe 0

        failWithNotFound()

        player.playbackState shouldBe Player.STATE_IDLE
        failures shouldBe listOf(transcode)
        gaveUp shouldBe 1
        sourceState.missingSongs("Shuttle") shouldBe emptySet()
    }

    @Test
    fun `a transcode replaced again at the same position after playback moved on is opened again`() {
        queue(transcode)
        load(skipUnloadable = true)
        player.seekTo(0, 1_000)
        driver.runUntil { player.playbackState == Player.STATE_READY }
        failWithNotFound()
        driver.runUntil { player.playbackState == Player.STATE_READY }

        player.seekTo(0, 2_000)
        driver.runUntil { player.playbackState == Player.STATE_READY }
        player.seekTo(0, 1_000)
        driver.runUntil { player.playbackState == Player.STATE_READY }
        failWithNotFound()

        driver.runUntil { player.playbackState == Player.STATE_READY }
        gaveUp shouldBe 0
        failures shouldBe emptyList()
    }

    @Test
    fun `a 404 at another position than the last re-open is opened again`() {
        queue(transcode)
        load(skipUnloadable = true)
        player.seekTo(0, 1_000)
        driver.runUntil { player.playbackState == Player.STATE_READY }
        failWithNotFound()
        driver.runUntil { player.playbackState == Player.STATE_READY }

        player.seekTo(0, 2_000)
        driver.runUntil { player.playbackState == Player.STATE_READY }
        failWithNotFound()

        driver.runUntil { player.playbackState == Player.STATE_READY }
        gaveUp shouldBe 0
    }

    @Test
    fun `a 404 on a transcode not yet ready opens it again rather than skipping it`() {
        queue(transcode, playable)
        player.play()
        var result: Result<Boolean>? = null
        loader.load(0, skipUnloadable = true) { result = it }

        failWithNotFound()
        driver.runUntil { result != null }

        result shouldBe Result.success(true)
        player.currentMediaItemIndex shouldBe 0
        failures shouldBe emptyList()
    }

    @Test
    fun `a 404 on a stream that isn't a replaceable transcode stops playback as before`() {
        queue(playable)
        load(skipUnloadable = true)
        player.play()
        driver.runUntil { player.isPlaying }

        failWithNotFound()

        player.playbackState shouldBe Player.STATE_IDLE
        failures shouldBe listOf(playable)
        gaveUp shouldBe 1
    }

    /** Fails the player as a 404 on a stream's segment does, from its playback thread. */
    private fun failWithNotFound() {
        val notFound = HttpDataSource.InvalidResponseCodeException(404, "Not Found", null, emptyMap(), DataSpec(Uri.parse("https://plex/segment.ts")), ByteArray(0))
        player.createMessage { _, _ -> throw ExoPlaybackException.createForSource(notFound, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) }.send()
        driver.idle()
    }

    /** Fails the player as [cause] does, from its playback thread. */
    private fun failWith(cause: Throwable) {
        player.createMessage { _, _ -> throw ExoPlaybackException.createForSource(cause as java.io.IOException, PlaybackException.ERROR_CODE_IO_UNSPECIFIED) }.send()
        driver.idle()
    }

    private fun uidAt(index: Int) = checkNotNull(player.getMediaItemAt(index).queueEntryOrNull).uid

    /** Plays [songs] from the first, then fails the item playing with [cause]. */
    private fun playingFailure(
        cause: Throwable,
        vararg songs: Song = arrayOf(playable)
    ): SongLoadFailure? {
        queue(*songs)
        load(skipUnloadable = true)
        player.play()
        driver.runUntil { player.isPlaying }
        failWith(cause)
        return loader.loadFailure.value
    }

    private fun openFailure(cause: Throwable?) = HttpDataSource.HttpDataSourceException("open", cause as? java.io.IOException, DataSpec(Uri.parse("http://127.0.0.1:4533/stream")), PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, HttpDataSource.HttpDataSourceException.TYPE_OPEN)

    private fun serverUnreachable(cause: Throwable): Boolean? {
        val failure = playingFailure(cause)
        return failure?.serverUnreachable
    }

    @Test
    fun `a failure caused by a refused connection says the server can't be reached`() {
        playingFailure(openFailure(java.net.ConnectException("refused"))) shouldBe SongLoadFailure(uidAt(0), playable, serverUnreachable = true)
    }

    @Test
    fun `a failure caused by an unknown host says the server can't be reached`() {
        serverUnreachable(java.io.IOException("lookup", java.net.UnknownHostException("nas.local"))) shouldBe true
    }

    @Test
    fun `a socket timeout says the server can't be reached`() {
        serverUnreachable(openFailure(java.net.SocketTimeoutException("timed out"))) shouldBe true
    }

    @Test
    fun `no route to the host says the server can't be reached`() {
        serverUnreachable(java.io.IOException("route", java.net.NoRouteToHostException("no route"))) shouldBe true
    }

    @Test
    fun `a failure of the stream itself doesn't blame the server`() {
        val notFound = HttpDataSource.InvalidResponseCodeException(404, "Not Found", null, emptyMap(), DataSpec(Uri.parse("https://plex/segment.ts")), ByteArray(0))

        serverUnreachable(notFound) shouldBe false
    }

    @Test
    fun `a failed open with no network cause doesn't blame the server`() {
        serverUnreachable(openFailure(null)) shouldBe false
    }

    @Test
    fun `a failed open caused by something else than the network doesn't blame the server`() {
        serverUnreachable(openFailure(java.io.IOException("reset"))) shouldBe false
    }

    @Test
    fun `a stream that can't be resolved because the network failed says the server can't be reached`() {
        serverUnreachable(MediaResolutionException("no route", java.net.ConnectException("refused"))) shouldBe true
        failures shouldBe emptyList()
    }

    @Test
    fun `a stream that can't be resolved for another reason doesn't blame the server`() {
        serverUnreachable(MediaResolutionException("no such song")) shouldBe false
        failures shouldBe emptyList()
    }

    @Test
    fun `a failure while restoring stays visible to a collector that arrives after it`() {
        queue(missing, playable)

        load(skipUnloadable = false).isFailure shouldBe true

        loader.loadFailure.value shouldBe SongLoadFailure(uidAt(0), missing, serverUnreachable = false)
    }

    @Test
    fun `a failure that skips on to the next item leaves nothing to show`() {
        queue(missing, playable)

        load(skipUnloadable = true) shouldBe Result.success(false)

        loader.loadFailure.value shouldBe null
    }

    @Test
    fun `the failure clears when its entry becomes ready`() {
        playingFailure(java.io.IOException("broken")) shouldBe SongLoadFailure(uidAt(0), playable, serverUnreachable = false)

        load(skipUnloadable = true) shouldBe Result.success(true)

        loader.loadFailure.value shouldBe null
    }

    @Test
    fun `the failure clears when another entry becomes current`() {
        playingFailure(java.io.IOException("broken"), playable, PlaybackHarness.song(4))

        player.seekTo(1, 0)
        driver.idle()

        loader.loadFailure.value shouldBe null
    }

    @Test
    fun `the failure clears when the queue is replaced`() {
        playingFailure(java.io.IOException("broken"))

        queue(PlaybackHarness.song(4))
        driver.idle()

        loader.loadFailure.value shouldBe null
    }

    @Test
    fun `the failure clears when the queue is emptied`() {
        playingFailure(java.io.IOException("broken"))

        player.clearMediaItems()
        driver.idle()

        loader.loadFailure.value shouldBe null
    }

    @Test
    fun `the same song twice in the queue keys the failure on the entry that failed`() {
        queue(playable, playable)
        player.seekTo(1, 0)
        load(skipUnloadable = true)
        player.play()
        driver.runUntil { player.isPlaying }

        failWith(java.io.IOException("broken"))

        uidAt(0) shouldNotBe uidAt(1)
        loader.loadFailure.value shouldBe SongLoadFailure(uidAt(1), playable, serverUnreachable = false)
    }

    /** [first] with [others] as its other copies, ranked in that order. */
    private fun withCopies(
        first: Song,
        vararg others: Song
    ): Song = first.copy(copies = listOf(first, *others))

    private val currentSongId get() = player.currentMediaItem?.queueEntryOrNull?.song?.id

    @Test
    fun `a copy whose file is missing is marked - and its next copy plays in its place without a failure`() {
        val song = withCopies(missing, playable)
        queue(song, PlaybackHarness.song(4))
        val uid = uidAt(0)

        load(skipUnloadable = true) shouldBe Result.success(true)

        player.currentMediaItemIndex shouldBe 0
        uidAt(0) shouldBe uid
        currentSongId shouldBe 2L
        failures shouldBe emptyList()
        loader.loadFailure.value shouldBe null
        gaveUp shouldBe 0
        sourceState.missingSongs("Shuttle") shouldBe setOf(1L)
    }

    @Test
    fun `a song whose every copy fails is reported and skipped as before`() {
        val otherMissing = testSong(5, path = "file:///nonexistent/other.wav", mimeType = "audio/wav")
        queue(withCopies(missing, otherMissing), playable)

        load(skipUnloadable = true) shouldBe Result.success(false)

        player.currentMediaItemIndex shouldBe 1
        failures.map { it.id } shouldBe listOf(5L)
        sourceState.missingSongs("Shuttle") shouldBe setOf(1L, 5L)
    }

    @Test
    fun `a chosen copy that replaceSong leaves unplayed is tried once, then the song fails as before`() {
        val stuck =
            ItemLoader(player, player, giveUp = { gaveUp++ }, isReplaceableTranscode = { false }, sourceState = sourceState, replaceSong = { _, _ -> })
                .also(player::addListener)
        val otherMissing = testSong(5, path = "file:///nonexistent/other.wav", mimeType = "audio/wav")
        queue(withCopies(missing, otherMissing), playable)
        var result: Result<Boolean>? = null

        stuck.load(0, skipUnloadable = false) { result = it }
        driver.runUntil { result != null }

        result?.isFailure shouldBe true
        gaveUp shouldBe 1
    }

    @Test
    fun `a copy already marked missing is passed over for the next`() {
        sourceState.setSongMissing("Shuttle", 2, missing = true)
        queue(withCopies(missing, playable, PlaybackHarness.song(4)))

        load(skipUnloadable = true) shouldBe Result.success(true)

        currentSongId shouldBe 4L
    }

    @Test
    fun `a connection failure plays the next copy without marking the failed one`() {
        playingFailure(openFailure(java.net.ConnectException("refused")), withCopies(playable, PlaybackHarness.song(4)))

        driver.runUntil { player.isPlaying }
        currentSongId shouldBe 4L
        loader.loadFailure.value shouldBe null
        sourceState.missingSongs("Shuttle") shouldBe emptySet()
    }

    @Test
    fun `a copy failing mid-song resumes on the next copy at the same position`() {
        queue(withCopies(playable, PlaybackHarness.song(4, file = PlaybackHarness.TONE_3S)))
        load(skipUnloadable = true)
        player.play()
        driver.runUntil { player.currentPosition >= 500 }
        val position = player.currentPosition

        failWith(java.io.IOException("broken"))

        driver.runUntil { player.isPlaying }
        currentSongId shouldBe 4L
        player.currentPosition shouldBeGreaterThanOrEqual position
        gaveUp shouldBe 0
    }

    @Test
    fun `a copy marked missing that plays again is unmarked`() {
        sourceState.setSongMissing("Shuttle", 2, missing = true)
        queue(playable)

        load(skipUnloadable = true) shouldBe Result.success(true)

        sourceState.missingSongs("Shuttle") shouldBe emptySet()
    }

    @Test
    fun `a marked copy that starts playing by playing on from the one before is unmarked`() {
        sourceState.setSongMissing("Shuttle", 4, missing = true)
        queue(playable, PlaybackHarness.song(4))
        load(skipUnloadable = true)

        player.play()
        driver.runUntil { player.currentMediaItemIndex == 1 }

        sourceState.missingSongs("Shuttle") shouldBe emptySet()
    }

    @Test
    fun `a marked copy that plays again on repeat is unmarked`() {
        queue(playable)
        player.repeatMode = Player.REPEAT_MODE_ONE
        load(skipUnloadable = true)
        sourceState.setSongMissing("Shuttle", 2, missing = true)

        player.play()
        driver.runUntil { sourceState.missingSongs("Shuttle").isEmpty() }
    }

    @Test
    fun `copies that each fail transiently once playing stop playback after one pass`() {
        val other = PlaybackHarness.song(4)
        queue(withCopies(playable, other))
        load(skipUnloadable = true)
        player.play()
        driver.runUntil { player.isPlaying }

        failWith(java.io.IOException("broken"))
        driver.runUntil { player.isPlaying && currentSongId == 4L }
        failWith(java.io.IOException("broken"))

        gaveUp shouldBe 1
        currentSongId shouldBe 4L
        sourceState.missingSongs("Shuttle") shouldBe emptySet()
    }

    @Test
    fun `a song's copies are all tried again when it plays again on repeat`() {
        val other = PlaybackHarness.song(4)
        queue(withCopies(playable, other))
        player.repeatMode = Player.REPEAT_MODE_ONE
        load(skipUnloadable = true)
        player.play()
        driver.runUntil { player.isPlaying }
        failWith(java.io.IOException("broken"))
        driver.runUntil { player.isPlaying && currentSongId == 4L }
        var repeated = false
        player.addListener(
            object : Player.Listener {
                override fun onMediaItemTransition(
                    mediaItem: androidx.media3.common.MediaItem?,
                    reason: Int
                ) {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) repeated = true
                }
            }
        )

        driver.runUntil { repeated && player.isPlaying }
        failWith(java.io.IOException("broken"))

        driver.runUntil { player.isPlaying && currentSongId == 2L }
        gaveUp shouldBe 0
    }

    @Test
    fun `a replaced copy keeps the entry's place in the shuffled order`() {
        val other = PlaybackHarness.song(4)
        queue(playable, PlaybackHarness.song(5), PlaybackHarness.song(6))
        player.setShuffleOrder(S2ShuffleOrder(ShuffleOrder(listOf(2, 0, 1))))
        player.shuffleModeEnabled = true

        queueFacade.replaceSong(uidAt(0), other)

        player.getMediaItemAt(0).queueEntryOrNull?.song?.id shouldBe 4L
        player.shuffledIndices() shouldBe listOf(2, 0, 1)
    }

    @Test
    fun `copies tried do not count towards the songs skipped in a row`() {
        queue(*failingSongs(PlaybackPolicy.MAX_LOAD_ATTEMPTS - 1), playable)

        load(skipUnloadable = true) shouldBe Result.success(false)

        currentSongId shouldBe 2L
    }

    @Test
    fun `the songs skipped in a row stop at the most attempts however many copies each has`() {
        queue(*failingSongs(PlaybackPolicy.MAX_LOAD_ATTEMPTS), playable)

        load(skipUnloadable = true).isFailure shouldBe true

        gaveUp shouldBe 1
        currentSongId shouldNotBe 2L
    }

    /** [count] songs, each with a second copy, that all fail to load. */
    private fun failingSongs(count: Int) = Array(count) { index ->
        withCopies(
            testSong(100L + 2 * index, path = PlaybackHarness.MISSING_FILE_URI, mimeType = "audio/wav"),
            testSong(101L + 2 * index, path = PlaybackHarness.MISSING_FILE_URI, mimeType = "audio/wav")
        )
    }

    @Test
    fun `abandoning a load fails it`() {
        queue(playable)
        var result: Result<Boolean>? = null
        loader.load(0, skipUnloadable = true) { result = it }

        loader.abandon()

        result?.isFailure shouldBe true
        loader.isLoading shouldBe false
    }
}
