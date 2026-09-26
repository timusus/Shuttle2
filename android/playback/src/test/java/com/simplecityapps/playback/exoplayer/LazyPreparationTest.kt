package com.simplecityapps.playback.exoplayer

import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.test.utils.TestExoPlayerBuilder
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.engine.SongUriResolver
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.queue.toMediaItem
import com.simplecityapps.playback.queue.toQueueEntry
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The player prepares only the items around the current one (lazy preparation, on by default) for a source that
 * reports a single window; any other source is prepared as soon as it joins the playlist, so a long queue would
 * prepare every item.
 */
// Robolectric: real Context via RuntimeEnvironment.getApplication().
@RunWith(RobolectricTestRunner::class)
class LazyPreparationTest {
    @Test
    fun `every kind of song's media source is a single window`() {
        val context = RuntimeEnvironment.getApplication()
        var mediaSourceFactory: MediaSource.Factory? = null
        val player =
            ExoPlayerFactory(
                context,
                EqualizerAudioProcessor(false),
                ReplayGainAudioProcessor(ReplayGainMode.Off),
                AudioTrackMonitor(),
                SongUriResolver(MediaResolver { song -> ResolvedMedia(uri = song.path, mimeType = song.mimeType, isRemote = true) })
            ) { renderersFactory, factory ->
                mediaSourceFactory = factory
                TestExoPlayerBuilder(context).setRenderersFactory(renderersFactory).setMediaSourceFactory(factory).build()
            }.create()

        try {
            val songs =
                listOf(
                    testSong(id = 1, path = "/music/local.mp3"),
                    testSong(id = 2, path = "content://media/external/audio/media/2", mimeType = "audio/flac"),
                    testSong(id = 3, path = "https://example.com/Audio/3/stream.flac", mimeType = "Audio/*"),
                    // Extensionless streams are probed for HLS when prepared.
                    testSong(id = 4, path = "https://example.com/Audio/4/universal", mimeType = "Audio/*"),
                    testSong(id = 5, path = "jellyfin://song/5", mimeType = "Audio/*")
                )
            songs.forEach { song ->
                checkNotNull(mediaSourceFactory).createMediaSource(song.toQueueEntry().toMediaItem()).isSingleWindow shouldBe true
            }
        } finally {
            player.release()
        }
    }
}
