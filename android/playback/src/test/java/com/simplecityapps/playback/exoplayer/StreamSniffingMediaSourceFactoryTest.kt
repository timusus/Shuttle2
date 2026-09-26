package com.simplecityapps.playback.exoplayer

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.upstream.Loader
import androidx.media3.exoplayer.util.ReleasableExecutor
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.concurrent.Executor
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class StreamSniffingMediaSourceFactoryTest {
    private val server = FakeStreamServer()
    private val dataSourceFactory = DefaultHttpDataSource.Factory()

    // The probe loads on the test thread, so preparing a source makes its request before it returns.
    private val factory = StreamSniffingMediaSourceFactory(
        dataSourceFactory,
        DefaultMediaSourceFactory(dataSourceFactory),
        HlsMediaSource.Factory(dataSourceFactory),
        newProbeLoader = { Loader(ReleasableExecutor.from(Executor(Runnable::run)) {}) }
    )
    private val caller = MediaSource.MediaSourceCaller { _, _ -> }

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.stop()
    }

    private fun item(
        uri: String,
        mimeType: String? = "Audio/*"
    ) = MediaItem.Builder().setUri(uri).setMimeType(mimeType).build()

    /** Prepares [source] on the main looper, then delivers the probe's result, which picks a child source. */
    private fun resolve(source: StreamSniffingMediaSource): MediaSource {
        source.prepareSource(caller, PlayerId.UNSET, DefaultBandwidthMeter.Builder(RuntimeEnvironment.getApplication()).build())
        shadowOf(Looper.getMainLooper()).idle()
        return checkNotNull(source.childSource).also { source.releaseSource(caller) }
    }

    @Test
    fun `local files and known mime types get the default source without a request`() {
        factory.createMediaSource(item("file:///storage/emulated/0/Music/a.flac", "audio/flac")).shouldBeInstanceOf<ProgressiveMediaSource>()
        factory.createMediaSource(item("${server.baseUrl}/Audio/1/hls", "audio/mpeg")).shouldBeInstanceOf<ProgressiveMediaSource>()
        factory.createMediaSource(item("content://media/external/audio/media/12")).shouldBeInstanceOf<ProgressiveMediaSource>()

        server.requestCount.get() shouldBe 0
    }

    @Test
    fun `creating a sniffing source makes no request until it is prepared`() {
        factory.createMediaSource(item("${server.baseUrl}/Audio/1/hls")).shouldBeInstanceOf<StreamSniffingMediaSource>()

        server.requestCount.get() shouldBe 0
    }

    @Test
    fun `transcoded stream plays through an hls source`() {
        val mediaItem = item("${server.baseUrl}/Audio/1/hls")
        val source = factory.createMediaSource(mediaItem) as StreamSniffingMediaSource

        resolve(source).shouldBeInstanceOf<HlsMediaSource>()
        source.mediaItem shouldBe mediaItem
    }

    @Test
    fun `direct stream plays through a progressive source`() {
        val source = factory.createMediaSource(item("${server.baseUrl}/Audio/1/direct")) as StreamSniffingMediaSource

        resolve(source).shouldBeInstanceOf<ProgressiveMediaSource>()
    }

    @Test
    fun `failed probe falls back to the progressive source`() {
        val source = factory.createMediaSource(item("${server.baseUrl}/Audio/1/rejected")) as StreamSniffingMediaSource

        resolve(source).shouldBeInstanceOf<ProgressiveMediaSource>()
    }
}
