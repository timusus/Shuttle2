package com.simplecityapps.playback.spec

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.RobolectricUtil
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper
import com.simplecityapps.playback.AudioEffectSessionManager
import com.simplecityapps.playback.CallMonitor
import com.simplecityapps.playback.PlaybackFacade
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.chromecast.CastQueue
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.engine.SongUriResolver
import com.simplecityapps.playback.exoplayer.AudioTrackMonitor
import com.simplecityapps.playback.exoplayer.EqualizerAudioProcessor
import com.simplecityapps.playback.exoplayer.ExoPlayerFactory
import com.simplecityapps.playback.exoplayer.MediaResolver
import com.simplecityapps.playback.exoplayer.ResolvedMedia
import com.simplecityapps.playback.fakes.FakeSharedPreferences
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueFacade
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.settings.SettingsStore
import com.squareup.moshi.Moshi
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowAudioManager
import org.robolectric.shadows.ShadowAudioTrack

/**
 * The real playback stack (PlaybackFacade and QueueFacade over the ExoPlayer that owns the queue), built by the
 * production [ExoPlayerFactory] (its renderers, audio sink, EQ and ReplayGain processors and media source factory)
 * on a [FakeClock]. Media comes from WAV files in the test resources.
 *
 * Tests drive it only through [playbackOperations] and [queueOperations] and observe their flows, plus what the
 * platform sees: the audio written to the AudioTrack ([audioOutput]) and the audio focus requests on
 * [audioManager] (and [audioFocus], which counts them). Nothing here reaches into the engine, so the tests hold
 * across the Media3 refactor (#345).
 *
 * Everything runs on the Robolectric main looper, as it does on the main thread in production. [runUntil] turns
 * that looper (and so the player, whose clock advances whenever its threads are idle) until a condition holds.
 * The player takes and gives up audio focus on its playback thread, which [idle] waits for.
 */
class PlaybackHarness(
    replayGainMode: ReplayGainMode = ReplayGainMode.Off,
    equalizerEnabled: Boolean = false,
    /** Where queue entries are built. Inline by default, so a queue change completes within the call that makes it. */
    buildContext: CoroutineContext = EmptyCoroutineContext,
    /** Whether the player prepares only the items around the current one, as production's does. Off only to measure the cost of preparing every item. */
    lazyPreparation: Boolean = true,
    /** The player the app plays through, around the local player: the local player itself, as when not casting. */
    activePlayer: (ExoPlayer) -> Player = { it },
    /** What keeps a Cast receiver in line, around the local player: none, as when there's no Cast. Built before [activePlayer]. */
    castQueue: (ExoPlayer) -> CastQueue? = { null },
    /** Where settings are kept. Pass one harness's to the next to model the app starting again. */
    val sharedPreferences: SharedPreferences = FakeSharedPreferences()
) {
    val context: Context = RuntimeEnvironment.getApplication()

    val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Every write to an AudioTrack since the output was last cleared, in order, plus the audio the sink wrote ahead,
     * before the clear, to a track that hadn't started yet (it pre-fills one while paused): that audio is heard once the
     * track plays, unless it's dropped first, by a seek flushing the track or by the sink moving on to another track.
     */
    private val writes = mutableListOf<Write>()

    private class Write(val track: AudioTrack, val audio: ByteArray, val headPosition: Int, val whilePlaying: Boolean) {
        /** Written before the output was cleared, to a track that hadn't started. */
        var aheadOfClear = false

        /** Flushed from its track, or left behind on a track that never played, before it was heard. */
        var dropped = false

        fun isHeard() = !aheadOfClear || (!dropped && track.state != AudioTrack.STATE_UNINITIALIZED)
    }

    private fun MutableList<Write>.hasPlayed(track: AudioTrack) = any { it.track === track && it.whilePlaying }

    /** The format of the last audio written to an AudioTrack, or null before any. */
    @Volatile
    var audioOutputFormat: AudioFormat? = null
        private set

    private val audioDataListener =
        ShadowAudioTrack.OnAudioDataWrittenListener { track, audioData, format ->
            audioOutputFormat = format
            synchronized(writes) {
                val write = Write(track, audioData.copyOf(), track.playbackHeadPosition, track.playState == AudioTrack.PLAYSTATE_PLAYING)
                writes.forEach { earlier ->
                    // A write only moves its track's head on, so a head behind an earlier write's means a flush.
                    val flushed = earlier.track === track && earlier.headPosition > write.headPosition
                    val leftBehind = earlier.track !== track && !writes.hasPlayed(earlier.track)
                    if (flushed || leftBehind) earlier.dropped = true
                }
                writes += write
            }
        }

    val equalizer = EqualizerAudioProcessor(equalizerEnabled)

    val replayGain = ReplayGainAudioProcessor(replayGainMode)

    val playbackPreferenceManager = PlaybackPreferenceManager(FakeSharedPreferences(), Moshi.Builder().build())

    val audioEffectSessionManager = AudioEffectSessionManager(context)

    /** The audio focus the player asks [audioManager] for and gives up. */
    val audioFocus: AudioFocusCounts

    // A song's path is the URI it plays from. An unresolvable one fails as a remote song does when its server can't be reached.
    private val songUriResolver =
        SongUriResolver(
            MediaResolver { song ->
                if (song.path.startsWith(UNRESOLVABLE_SCHEME)) throw IOException("Can't resolve ${song.path}")
                ResolvedMedia(uri = song.path, mimeType = song.mimeType, isRemote = false)
            }
        )

    private val player: ExoPlayer

    val queueOperations: QueueOperations

    val playbackOperations: PlaybackOperations

    /** The player the app plays through, which the media session publishes. */
    val appPlayer: Player

    /** Whether the player changed whether it plays, or stopped, since the playback thread last caught up: either moves audio focus. */
    private var focusMayChange = false

    /** How many times the player's playlist has changed: each change is a timeline rebuild, costing time in the queue's length. */
    var playlistChanges = 0
        private set

    init {
        ShadowAudioTrack.addAudioDataListener(audioDataListener)
        player =
            ExoPlayerFactory(context, equalizer, replayGain, AudioTrackMonitor(), songUriResolver) { renderersFactory, mediaSourceFactory ->
                TestExoPlayerBuilder(context)
                    .setClock(FakeClock(true))
                    // Production's ExoPlayer.Builder prepares lazily by default: only the items around the current one.
                    .setUseLazyPreparation(lazyPreparation)
                    .setRenderersFactory(renderersFactory)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .build()
            }.create()
        player.addListener(
            object : Player.Listener {
                override fun onTimelineChanged(
                    timeline: Timeline,
                    reason: Int
                ) {
                    if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) playlistChanges++
                }

                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int
                ) {
                    focusMayChange = true
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    // A stopped player gives focus up; buffering and ready leave it as it is.
                    if (playbackState == Player.STATE_IDLE) focusMayChange = true
                }
            }
        )
        val cast = castQueue(player)
        val active = activePlayer(player)
        appPlayer = active
        audioEffectSessionManager.attach(active, player)
        audioFocus = AudioFocusCounts(Shadow.extract(audioManager))
        val playbackSettings = PlaybackSettings(SettingsStore(sharedPreferences))
        val queueFacade = QueueFacade(player, playbackSettings, songUriResolver, buildContext, active)
        queueOperations = queueFacade
        playbackOperations =
            PlaybackFacade(
                queueOperations = queueFacade,
                player = active,
                localPlayer = player,
                playbackPreferenceManager = playbackPreferenceManager,
                playbackSpeed = playbackSettings.playbackSpeed,
                callMonitor = CallMonitor(audioManager),
                appCoroutineScope = scope,
                castQueue = cast
            )
    }

    /** Runs a suspending operation to completion, then lets the main looper catch up with what it started. */
    fun <T> run(block: suspend () -> T): T = runBlocking { block() }.also { idle() }

    /** Starts a suspending operation on the main thread, as a UI caller does, running it until it first suspends. */
    fun launch(block: suspend () -> Unit): Job = scope.launch(start = CoroutineStart.UNDISPATCHED) { block() }

    /**
     * Runs the main looper's due tasks, and what they hand the playback thread (such as taking or giving up audio
     * focus), without letting playback time pass.
     */
    fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        // Waiting on the playback thread turns the player's clock, which would play on what's playing.
        if (!appPlayer.isPlaying && !player.isPlaying) awaitFocusChange()
    }

    /**
     * Lets the playback thread take or give up audio focus for the last change to whether the player plays, if there's
     * been one since the last wait.
     */
    private fun awaitFocusChange() {
        if (focusMayChange) {
            focusMayChange = false
            TestPlayerRunHelper.runUntilPendingCommandsAreFullyHandled(player)
        }
    }

    /** Turns the main looper, playing the player on, until [condition] holds. Fails after [timeoutMs] of wall time. */
    fun runUntil(
        timeoutMs: Long = 10_000,
        condition: () -> Boolean
    ) {
        RobolectricUtil.runMainLooperUntil({ condition() }, timeoutMs, androidx.media3.common.util.Clock.DEFAULT)
        awaitFocusChange()
    }

    /**
     * Another app taking audio focus or giving it back ([focusChange] is one of AudioManager's `AUDIOFOCUS_` changes),
     * as the platform tells the player: on its playback thread, where it asked to be told.
     */
    fun changeAudioFocus(focusChange: Int) {
        val request = checkNotNull(shadowOf(audioManager).lastAudioFocusRequest) { "The player hasn't asked for audio focus" }
        Handler(player.playbackLooper).post { request.listener.onAudioFocusChange(focusChange) }
        focusMayChange = true
        awaitFocusChange()
        shadowOf(Looper.getMainLooper()).idle()
    }

    /**
     * A call ringing, starting or ending, as the platform sets the audio mode ([mode] is one of AudioManager's `MODE_`
     * values). Robolectric tells the app's mode listeners on API 31+.
     */
    fun setAudioMode(mode: Int) {
        audioManager.mode = mode
        idle()
    }

    /** Headphones unplugged: the platform's becoming-noisy broadcast. */
    fun unplugHeadphones() {
        context.sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        runUntil { !player.playWhenReady }
        idle()
    }

    /** Every value [flow] emits from now on, collected on the main thread as production consumers do. */
    fun <T> record(flow: Flow<T>): List<T> {
        val values = mutableListOf<T>()
        scope.launch(start = CoroutineStart.UNDISPATCHED) { flow.collect { values += it } }
        return values
    }

    /** The PCM written to AudioTracks so far, in the format the sink wrote it (16-bit mono for the test files). */
    fun audioOutput(): ByteArray = synchronized(writes) {
        val heard = ByteArrayOutputStream()
        writes.filter(Write::isHeard).forEach { heard.write(it.audio) }
        heard.toByteArray()
    }

    /** Forgets the audio output so far, except what's been written ahead to a track that has yet to play it. */
    fun clearAudioOutput() = synchronized(writes) {
        writes.retainAll { write ->
            !write.dropped && !writes.hasPlayed(write.track) && write.track.state != AudioTrack.STATE_UNINITIALIZED
        }
        writes.forEach { write -> write.aheadOfClear = true }
    }

    fun release() {
        ShadowAudioTrack.removeAudioDataListener(audioDataListener)
        scope.cancel()
        player.release()
        shadowOf(Looper.getMainLooper()).idle()
    }

    companion object {
        /** 2 s of a 440 Hz sine at half scale, 16 kHz mono 16-bit. */
        const val TONE_2S = "tone-2s.wav"
        const val TONE_2S_MS = 2_000

        /** 1 s of a 440 Hz sine at half scale, 16 kHz mono 16-bit. */
        const val TONE_1S = "tone-1s.wav"
        const val TONE_1S_MS = 1_000

        /** 3 s of a 440 Hz sine at half scale, 16 kHz mono 16-bit: long enough to play past the restart threshold. */
        const val TONE_3S = "tone-3s.wav"
        const val TONE_3S_MS = 3_000

        /** Bytes of output per millisecond of the 16-bit test files: 16 kHz, mono, 2 bytes a sample. */
        const val BYTES_PER_MS = 32

        /** Two minutes: more than the player buffers ahead (50 s, plus up to a 1 MB load chunk). */
        const val LONG_SONG_MS = 120_000

        private const val SAMPLE_RATE = 16_000
        private const val WAV_HEADER_SIZE = 44

        /** A file URI nothing can be read from. */
        const val MISSING_FILE_URI = "file:///nonexistent/missing.wav"

        private const val UNRESOLVABLE_SCHEME = "unresolvable:"

        fun resourceUri(name: String): String = File(checkNotNull(PlaybackHarness::class.java.getResource("/audio/$name")).toURI()).toURI().toString()

        /** A song that plays [file] from the test resources, with its real length. */
        fun song(
            id: Long,
            file: String = TONE_2S,
            durationMs: Int =
                when (file) {
                    TONE_2S -> TONE_2S_MS
                    TONE_3S -> TONE_3S_MS
                    else -> TONE_1S_MS
                },
            replayGainTrack: Double? = null
        ): Song = testSong(id = id, path = resourceUri(file), mimeType = "audio/wav", duration = durationMs, replayGainTrack = replayGainTrack)

        /**
         * A song [durationMs] long, of silence in the test files' format, written to a temporary file: long enough that the
         * player buffers only part of it, so a seek far enough ahead reads the file again.
         */
        fun longSong(
            id: Long,
            durationMs: Int = LONG_SONG_MS
        ): Song {
            val dataSize = durationMs * BYTES_PER_MS
            val header =
                ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
                    put("RIFF".toByteArray()).putInt(WAV_HEADER_SIZE - 8 + dataSize).put("WAVE".toByteArray())
                    put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2).putShort(2).putShort(16)
                    put("data".toByteArray()).putInt(dataSize)
                }
            val file = File.createTempFile("long-song-$id", ".wav").apply { deleteOnExit() }
            file.outputStream().use { output ->
                output.write(header.array())
                output.write(ByteArray(dataSize))
            }
            return testSong(id = id, path = file.toURI().toString(), mimeType = "audio/wav", duration = durationMs)
        }

        /** Deletes [song]'s file, as when a file is deleted or its storage removed while it plays. */
        fun deleteFile(song: Song) {
            File(URI(song.path)).delete()
        }

        /** A song whose file can't be read, as when an opened file's URI grant has lapsed. */
        fun unreadableSong(id: Long): Song = testSong(id = id, path = MISSING_FILE_URI, mimeType = "audio/wav", duration = TONE_2S_MS)

        /** A song whose stream can't be resolved, as when a remote song's server can't be reached. */
        fun unresolvableSong(id: Long): Song = testSong(id = id, path = "${UNRESOLVABLE_SCHEME}song$id", mimeType = "audio/wav", duration = TONE_2S_MS)
    }
}

/** The audio focus requests and abandons [shadow] has seen. */
class AudioFocusCounts(private val shadow: CountingShadowAudioManager) {
    val requests: Int get() = shadow.requests.get()

    val abandons: Int get() = shadow.abandons.get()
}

/** Robolectric's audio manager, counting focus requests and abandons (made on the player's playback thread). */
@Implements(AudioManager::class)
class CountingShadowAudioManager : ShadowAudioManager() {
    val requests = AtomicInteger()

    val abandons = AtomicInteger()

    @Implementation(minSdk = Build.VERSION_CODES.O)
    override fun requestAudioFocus(audioFocusRequest: android.media.AudioFocusRequest): Int {
        requests.incrementAndGet()
        return super.requestAudioFocus(audioFocusRequest)
    }

    @Implementation(minSdk = Build.VERSION_CODES.O)
    override fun abandonAudioFocusRequest(audioFocusRequest: android.media.AudioFocusRequest): Int {
        abandons.incrementAndGet()
        return super.abandonAudioFocusRequest(audioFocusRequest)
    }
}
