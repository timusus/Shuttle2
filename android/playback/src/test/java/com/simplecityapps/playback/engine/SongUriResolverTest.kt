package com.simplecityapps.playback.engine

import android.net.Uri
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.test.utils.TestExoPlayerBuilder
import com.simplecityapps.mediaprovider.TimeSeekableStream
import com.simplecityapps.playback.exoplayer.MediaResolver
import com.simplecityapps.playback.exoplayer.ResolvedMedia
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.queue.QueueFacade
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.settings.SettingsStore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** The resolver knows the remote songs the playlist holds, and forgets each one the playlist no longer does. */
// Robolectric: drives a real TestExoPlayerBuilder-backed ExoPlayer through QueueFacade.
@RunWith(RobolectricTestRunner::class)
class SongUriResolverTest {
    /** How many times a stream was resolved. */
    private val resolved = AtomicInteger()

    /** How many of the next resolutions fail. */
    private val failures = AtomicInteger()

    /** What each resolution waits for before answering. */
    @Volatile
    private var gate = CompletableDeferred(Unit)

    private val resolver =
        SongUriResolver(
            MediaResolver { song ->
                gate.await()
                resolved.incrementAndGet()
                if (failures.getAndDecrement() > 0) throw IOException("Server unreachable")
                val timeSeek = if (song.path.startsWith("subsonic:")) {
                    // 8 kbps: a thousand bytes a second, for 10 s
                    TimeSeekableStream(bitrateKbps = 8, durationMs = if (song.duration == 0) 0 else 10_000) { offsetSeconds -> "https://server/stream/${song.id}?offset=$offsetSeconds" }
                } else {
                    null
                }
                if (song.path.startsWith("plex:")) {
                    ResolvedMedia(uri = "https://plex:32400/music/transcode/start.m3u8?id=${song.id}", mimeType = "application/x-mpegURL", isRemote = true, isReplaceableTranscode = true)
                } else {
                    ResolvedMedia(uri = "https://server/stream/${song.id}", mimeType = song.mimeType, isRemote = true, timeSeek = timeSeek)
                }
            }
        )

    private val player = TestExoPlayerBuilder(RuntimeEnvironment.getApplication()).build()

    private val queue = QueueFacade(player, PlaybackSettings(SettingsStore(InMemoryKeyValueStore())), resolver, buildContext = EmptyCoroutineContext)

    private val upstream = RecordingDataSource()

    private val dataSource = resolver.dataSourceFactory { upstream }.createDataSource()

    private val songs = (1L..3L).map { id -> testSong(id, path = "jellyfin://item/$id") }

    @Test
    fun `a queued remote song opens its stream`() {
        runBlocking { queue.setQueue(songs) }

        open(songs[1]) shouldBe Result.success(Uri.parse("https://server/stream/2"))
    }

    @Test
    fun `a song removed from the queue is forgotten`() {
        runBlocking { queue.setQueue(songs) }

        queue.remove(listOf(queue.getQueue()[1]))
        shadowOf(Looper.getMainLooper()).idle()

        open(songs[1]).exceptionOrNull()?.isResolutionFailure() shouldBe true
        open(songs[2]) shouldBe Result.success(Uri.parse("https://server/stream/3"))
    }

    @Test
    fun `a replaced queue forgets the songs it no longer holds`() {
        runBlocking {
            queue.setQueue(songs)
            queue.setQueue(listOf(songs[2]))
        }

        open(songs[0]).exceptionOrNull()?.isResolutionFailure() shouldBe true
        open(songs[2]) shouldBe Result.success(Uri.parse("https://server/stream/3"))
    }

    @Test
    fun `a stream is resolved once however often it's opened`() {
        runBlocking { queue.setQueue(songs) }

        open(songs[0]) shouldBe Result.success(Uri.parse("https://server/stream/1"))
        open(songs[0]) shouldBe Result.success(Uri.parse("https://server/stream/1"))

        resolved.get() shouldBe 1
    }

    @Test
    fun `a failed resolution is asked again on the next open`() {
        runBlocking { queue.setQueue(songs) }
        failures.set(1)

        open(songs[0]).exceptionOrNull()?.isResolutionFailure() shouldBe true
        open(songs[0]) shouldBe Result.success(Uri.parse("https://server/stream/1"))

        resolved.get() shouldBe 2
    }

    @Test
    fun `an interrupted open stops waiting, and the resolution carries on for the next`() {
        runBlocking { queue.setQueue(songs) }
        gate = CompletableDeferred()

        // Media3 cancels a load by interrupting its thread.
        Thread.currentThread().interrupt()
        (open(songs[0]).exceptionOrNull() is InterruptedIOException) shouldBe true
        Thread.interrupted()

        gate.complete(Unit)
        open(songs[0]) shouldBe Result.success(Uri.parse("https://server/stream/1"))
        resolved.get() shouldBe 1
    }

    @Test
    fun `a time-seekable stream opens from the second a position stands for, and reads on to the position`() {
        val song = testSong(4, path = "subsonic://song/4")
        runBlocking { queue.setQueue(listOf(song)) }

        val length = dataSource.open(DataSpec.Builder().setUri(Uri.parse(song.path)).setPosition(2_500).build())

        upstream.opened shouldBe Uri.parse("https://server/stream/4?offset=2")
        upstream.skipped shouldBe 500
        // What's left of 10 s at a thousand bytes a second
        length shouldBe 7_500
        dataSource.uri shouldBe Uri.parse("https://server/stream/4?offset=2")
    }

    @Test
    fun `a time-seekable stream opens from its start with its estimated length`() {
        val song = testSong(4, path = "subsonic://song/4")
        runBlocking { queue.setQueue(listOf(song)) }

        val length = dataSource.open(DataSpec(Uri.parse(song.path)))

        upstream.opened shouldBe Uri.parse("https://server/stream/4")
        upstream.skipped shouldBe 0
        length shouldBe 10_000
    }

    @Test
    fun `a time-seekable stream with no duration has an unknown length`() {
        val song = testSong(4, path = "subsonic://song/4").copy(duration = 0)
        runBlocking { queue.setQueue(listOf(song)) }

        val length = dataSource.open(DataSpec(Uri.parse(song.path)))

        length shouldBe C.LENGTH_UNSET.toLong()
    }

    @Test
    fun `only a stream that resolved to one seeking by time is time-seekable`() {
        val timeSeekable = testSong(4, path = "subsonic://song/4")
        runBlocking { queue.setQueue(songs + timeSeekable) }

        resolver.isTimeSeekable(Uri.parse(timeSeekable.path)) shouldBe false
        dataSource.open(DataSpec(Uri.parse(timeSeekable.path)))
        dataSource.open(DataSpec(Uri.parse(songs[0].path)))

        resolver.isTimeSeekable(Uri.parse(timeSeekable.path)) shouldBe true
        resolver.isTimeSeekable(Uri.parse(songs[0].path)) shouldBe false
        resolver.isTimeSeekable(Uri.parse(songs[1].path)) shouldBe false
    }

    @Test
    fun `a stream that seeks by byte range opens at the position`() {
        runBlocking { queue.setQueue(songs) }

        dataSource.open(DataSpec.Builder().setUri(Uri.parse(songs[0].path)).setPosition(2_500).build())

        upstream.openedPosition shouldBe 2_500
        upstream.skipped shouldBe 0
    }

    @Test
    fun `only a stream that resolved to a replaceable transcode is one - and its server's URLs are`() {
        val transcode = testSong(5, path = "plex://item/5")
        runBlocking { queue.setQueue(songs + transcode) }

        resolver.isReplaceableTranscode(Uri.parse(transcode.path)) shouldBe false
        resolver.servesReplaceableTranscode(PLEX_SEGMENT) shouldBe false
        dataSource.open(DataSpec(Uri.parse(transcode.path)))
        dataSource.open(DataSpec(Uri.parse(songs[0].path)))

        resolver.isReplaceableTranscode(Uri.parse(transcode.path)) shouldBe true
        resolver.isReplaceableTranscode(Uri.parse(songs[0].path)) shouldBe false
        resolver.servesReplaceableTranscode(PLEX_SEGMENT) shouldBe true
        resolver.servesReplaceableTranscode(Uri.parse("https://server/stream/1")) shouldBe false
    }

    @Test
    fun `a 404 from a replaceable transcode's server fails at once - and other load errors are retried`() {
        val transcode = testSong(5, path = "plex://item/5")
        runBlocking { queue.setQueue(songs + transcode) }
        dataSource.open(DataSpec(Uri.parse(transcode.path)))
        dataSource.open(DataSpec(Uri.parse(songs[0].path)))
        val policy = S2LoadErrorHandlingPolicy(resolver::servesReplaceableTranscode)

        policy.retryDelayFor(httpError(404, PLEX_SEGMENT)) shouldBe C.TIME_UNSET
        policy.retryDelayFor(httpError(500, PLEX_SEGMENT)) shouldNotBe C.TIME_UNSET
        policy.retryDelayFor(httpError(404, Uri.parse("https://server/stream/1"))) shouldNotBe C.TIME_UNSET
        policy.retryDelayFor(httpError(404, Uri.parse("https://plex:32400/library/parts/5/file.mp3"))) shouldNotBe C.TIME_UNSET
        policy.retryDelayFor(MediaResolutionException("Server unreachable")) shouldBe C.TIME_UNSET
    }

    private fun S2LoadErrorHandlingPolicy.retryDelayFor(exception: IOException): Long = getRetryDelayMsFor(
        LoadErrorHandlingPolicy.LoadErrorInfo(LoadEventInfo(0, DataSpec(Uri.EMPTY), 0), MediaLoadData(C.DATA_TYPE_MEDIA), exception, 1)
    )

    private fun httpError(
        responseCode: Int,
        uri: Uri
    ) = HttpDataSource.InvalidResponseCodeException(responseCode, null, null, emptyMap(), DataSpec(uri), ByteArray(0))

    /** Opens [song]'s URI as the player's loader would, returning the URI the upstream was asked to open. */
    private fun open(song: com.simplecityapps.shuttle.model.Song): Result<Uri> = runCatching {
        dataSource.open(DataSpec(Uri.parse(song.path)))
        dataSource.close()
        checkNotNull(upstream.opened)
    }

    /** Records what it was asked to open, and serves endless bytes, counting those read as [skipped]. */
    private class RecordingDataSource : DataSource {
        var opened: Uri? = null
        var openedPosition: Long? = null
        var skipped = 0L

        override fun open(dataSpec: DataSpec): Long {
            opened = dataSpec.uri
            openedPosition = dataSpec.position
            skipped = 0
            return 0
        }

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int
        ): Int {
            skipped += length
            return length
        }

        override fun getUri(): Uri? = opened

        override fun close() = Unit

        override fun addTransferListener(transferListener: TransferListener) = Unit
    }

    private companion object {
        /** A segment of a Plex HLS transcode, on the server the transcode resolved to but at another path. */
        val PLEX_SEGMENT: Uri = Uri.parse("https://plex:32400/video/transcode/session/s2-5/base/00001.ts")
    }
}
