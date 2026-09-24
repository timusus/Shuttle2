package com.simplecityapps.playback.spec

import android.content.Context
import android.media.AudioManager
import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.RobolectricUtil
import com.simplecityapps.playback.AudioEffectSessionManager
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.ProgressTicker
import com.simplecityapps.playback.audiofocus.AudioFocusHelperApi26
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.exoplayer.AudioTrackMonitor
import com.simplecityapps.playback.exoplayer.EqualizerAudioProcessor
import com.simplecityapps.playback.exoplayer.ExoPlayerFactory
import com.simplecityapps.playback.exoplayer.ExoPlayerPlayback
import com.simplecityapps.playback.exoplayer.MediaResolver
import com.simplecityapps.playback.exoplayer.ResolvedMedia
import com.simplecityapps.playback.fakes.FakeSharedPreferences
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.squareup.moshi.Moshi
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAudioTrack

/**
 * The real playback stack (PlaybackManager, QueueManager, ExoPlayerPlayback) on a real ExoPlayer, built by the
 * production [ExoPlayerFactory] (its renderers, audio sink, EQ and ReplayGain processors and media source factory)
 * on a [FakeClock]. Media comes from WAV files in the test resources.
 *
 * Tests drive it only through [playbackOperations] and [queueOperations] and observe their flows, plus what the
 * platform sees: the audio written to the AudioTrack ([audioOutput]) and the audio focus requests on
 * [audioManager]. Nothing here reaches into the engine, so the tests hold across the Media3 refactor (#345).
 *
 * Everything runs on the Robolectric main looper, as it does on the main thread in production. [runUntil] turns
 * that looper (and so the player, whose clock advances whenever its threads are idle) until a condition holds.
 */
class PlaybackHarness(
    replayGainMode: ReplayGainMode = ReplayGainMode.Off,
    equalizerEnabled: Boolean = false
) {
    val context: Context = RuntimeEnvironment.getApplication()

    val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val players = mutableListOf<ExoPlayer>()

    private val output = ByteArrayOutputStream()

    private val audioDataListener = ShadowAudioTrack.OnAudioDataWrittenListener { _, audioData, _ -> output.write(audioData) }

    val equalizer = EqualizerAudioProcessor(equalizerEnabled)

    val replayGain = ReplayGainAudioProcessor(replayGainMode)

    val playbackPreferenceManager = PlaybackPreferenceManager(FakeSharedPreferences(), Moshi.Builder().build())

    private val queueManager = QueueManager(GeneralPreferenceManager(FakeSharedPreferences()))

    val queueOperations: QueueOperations = queueManager

    val playbackOperations: PlaybackOperations

    init {
        ShadowAudioTrack.addAudioDataListener(audioDataListener)
        val playerFactory =
            ExoPlayerFactory(context, equalizer, replayGain, AudioTrackMonitor()) { renderersFactory, mediaSourceFactory ->
                TestExoPlayerBuilder(context)
                    .setClock(FakeClock(true))
                    .setRenderersFactory(renderersFactory)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .build()
                    .also(players::add)
            }
        // A song's path is the URI it plays from. An unresolvable one fails as a remote song does when its server can't be reached.
        val mediaResolver =
            MediaResolver { song ->
                if (song.path.startsWith(UNRESOLVABLE_SCHEME)) throw IOException("Can't resolve ${song.path}")
                ResolvedMedia(uri = song.path, mimeType = song.mimeType, isRemote = false)
            }
        playbackOperations =
            PlaybackManager(
                queueManager = queueManager,
                audioFocusHelper = AudioFocusHelperApi26(context),
                playbackPreferenceManager = playbackPreferenceManager,
                audioEffectSessionManager = AudioEffectSessionManager(context),
                appCoroutineScope = scope,
                progressTicker = ProgressTicker(scope),
                exoplayerPlayback = ExoPlayerPlayback(playerFactory, mediaResolver),
                audioManager = audioManager
            )
    }

    /** Runs a suspending operation to completion, then lets the main looper catch up with what it started. */
    fun <T> run(block: suspend () -> T): T = runBlocking { block() }.also { idle() }

    /** Runs the main looper's due tasks, without letting playback time pass. */
    fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Turns the main looper, playing the player on, until [condition] holds. Fails after [timeoutMs] of wall time. */
    fun runUntil(
        timeoutMs: Long = 10_000,
        condition: () -> Boolean
    ) {
        RobolectricUtil.runMainLooperUntil({ condition() }, timeoutMs, androidx.media3.common.util.Clock.DEFAULT)
    }

    /** Every value [flow] emits from now on, collected on the main thread as production consumers do. */
    fun <T> record(flow: Flow<T>): List<T> {
        val values = mutableListOf<T>()
        scope.launch(start = CoroutineStart.UNDISPATCHED) { flow.collect { values += it } }
        return values
    }

    /** The PCM written to AudioTracks so far, in the format the sink wrote it (16-bit mono for the test files). */
    fun audioOutput(): ByteArray = output.toByteArray()

    fun clearAudioOutput() {
        output.reset()
    }

    fun release() {
        ShadowAudioTrack.removeAudioDataListener(audioDataListener)
        scope.cancel()
        players.forEach(ExoPlayer::release)
        idle()
    }

    companion object {
        /** 2 s of a 440 Hz sine at half scale, 16 kHz mono 16-bit. */
        const val TONE_2S = "tone-2s.wav"
        const val TONE_2S_MS = 2_000

        /** 1 s of a 440 Hz sine at half scale, 16 kHz mono 16-bit. */
        const val TONE_1S = "tone-1s.wav"
        const val TONE_1S_MS = 1_000

        /** Bytes of output per millisecond of the 16-bit test files: 16 kHz, mono, 2 bytes a sample. */
        const val BYTES_PER_MS = 32

        /** A file URI nothing can be read from. */
        const val MISSING_FILE_URI = "file:///nonexistent/missing.wav"

        private const val UNRESOLVABLE_SCHEME = "unresolvable:"

        fun resourceUri(name: String): String = File(checkNotNull(PlaybackHarness::class.java.getResource("/audio/$name")).toURI()).toURI().toString()

        /** A song that plays [file] from the test resources, with its real length. */
        fun song(
            id: Long,
            file: String = TONE_2S,
            durationMs: Int = if (file == TONE_2S) TONE_2S_MS else TONE_1S_MS,
            replayGainTrack: Double? = null
        ): Song = testSong(id = id, path = resourceUri(file), mimeType = "audio/wav", duration = durationMs, replayGainTrack = replayGainTrack)

        /** A song whose file can't be read, as when an opened file's URI grant has lapsed. */
        fun unreadableSong(id: Long): Song = testSong(id = id, path = MISSING_FILE_URI, mimeType = "audio/wav", duration = TONE_2S_MS)

        /** A song whose stream can't be resolved, as when a remote song's server can't be reached. */
        fun unresolvableSong(id: Long): Song = testSong(id = id, path = "${UNRESOLVABLE_SCHEME}song$id", mimeType = "audio/wav", duration = TONE_2S_MS)
    }
}
