package com.simplecityapps.playback.engine

import android.net.Uri
import android.os.Looper
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.test.utils.TestExoPlayerBuilder
import com.simplecityapps.playback.exoplayer.MediaResolver
import com.simplecityapps.playback.exoplayer.ResolvedMedia
import com.simplecityapps.playback.fakes.FakeSharedPreferences
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.queue.QueueFacade
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.settings.SettingsStore
import io.kotest.matchers.shouldBe
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
                ResolvedMedia(uri = "https://server/stream/${song.id}", mimeType = song.mimeType, isRemote = true)
            }
        )

    private val player = TestExoPlayerBuilder(RuntimeEnvironment.getApplication()).build()

    private val queue = QueueFacade(player, PlaybackSettings(SettingsStore(FakeSharedPreferences())), resolver, buildContext = EmptyCoroutineContext)

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

    /** Opens [song]'s URI as the player's loader would, returning the URI the upstream was asked to open. */
    private fun open(song: com.simplecityapps.shuttle.model.Song): Result<Uri> = runCatching {
        dataSource.open(DataSpec(Uri.parse(song.path)))
        dataSource.close()
        checkNotNull(upstream.opened)
    }

    private class RecordingDataSource : DataSource {
        var opened: Uri? = null

        override fun open(dataSpec: DataSpec): Long {
            opened = dataSpec.uri
            return 0
        }

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int
        ): Int = -1

        override fun getUri(): Uri? = opened

        override fun close() = Unit

        override fun addTransferListener(transferListener: TransferListener) = Unit
    }
}
