package com.simplecityapps.playback.exoplayer

import androidx.media3.common.C
import androidx.media3.common.Player
import com.simplecityapps.playback.Playback
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.dsp.replaygain.ReplayGain
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.fakes.FakePlayer
import com.simplecityapps.playback.fakes.FakePlayerFactory
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Pins how [ExoPlayerPlayback] drives its player: what it queues, what it re-applies to a rebuilt
 * player, and how it turns player events into [Playback.Callback] calls.
 */
class ExoPlayerPlaybackTest {
    private val playerFactory = FakePlayerFactory()
    private val replayGainAudioProcessor = ReplayGainAudioProcessor(ReplayGainMode.Off, 0.0)
    private val replayGainTracker = replayGainAudioProcessor.streamTracker
    private val callbackEvents = mutableListOf<String>()
    private val playback =
        ExoPlayerPlayback(
            playerFactory = playerFactory,
            replayGainAudioProcessor = replayGainAudioProcessor,
            mediaResolver = { song -> ResolvedMedia(uri = song.path, mimeType = song.mimeType, isRemote = song.path.startsWith("http")) }
        ).apply {
            callback =
                object : Playback.Callback {
                    override fun onPlaybackStateChanged(playbackState: PlaybackState) {
                        callbackEvents += "state $playbackState"
                    }

                    override fun onTrackEnded(trackWentToNext: Boolean) {
                        callbackEvents += "trackEnded $trackWentToNext"
                    }

                    override fun onPositionDiscontinuity() {
                        callbackEvents += "discontinuity ${this@apply.getProgress()}"
                    }
                }
        }

    private val songA = createSong("a")
    private val songB = createSong("b")
    private val songC = createSong("c")
    private val songD = createSong("d")

    private val player: FakePlayer get() = playerFactory.latest

    private suspend fun load(
        current: Song,
        next: Song? = null,
        seekPosition: Int = 0
    ) {
        playback.load(current, next, seekPosition) {}
    }

    private fun queuedUris() = player.playlist.map { it.uri }

    @Test
    fun `load drives the player in a fixed order`() = runTest {
        load(songA, next = songB, seekPosition = 5_000)

        player.commands shouldBe
            listOf(
                "repeatMode ${Player.REPEAT_MODE_OFF}",
                "removeListener",
                "pause",
                "seekTo 0",
                "addListener",
                "setMediaItem /music/a.flac",
                "seekTo 5000",
                "prepare",
                "setWakeMode ${C.WAKE_MODE_LOCAL}",
                "addMediaItem /music/b.flac"
            )
    }

    @Test
    fun `repeat mode is re-applied to a player rebuilt after release`() = runTest {
        load(songA)
        playback.setRepeatMode(QueueManager.RepeatMode.All)
        playback.release()

        load(songA)

        player.isReleased shouldBe false
        player.repeatMode shouldBe Player.REPEAT_MODE_ALL
    }

    @Test
    fun `audio session id is re-applied to a player rebuilt after release`() = runTest {
        load(songA)
        playback.setAudioSessionId(42)
        playback.release()

        playback.getAudioSessionId() shouldBe 42

        load(songA)

        player.audioSessionId shouldBe 42
        playback.getAudioSessionId() shouldBe 42
    }

    @Test
    fun `a player rebuilt after a duck starts at full volume`() = runTest {
        // The duck must not outlive the player: switching to Cast disables audio focus, so the focus
        // gain that would restore the volume never reaches a player rebuilt on the way back.
        load(songA)
        playback.setVolume(0.2f)
        player.volume shouldBe 0.2f
        playback.release()

        load(songA)

        player.isReleased shouldBe false
        player.volume shouldBe 1f
        player.commands.filter { it.startsWith("setVolume") }.shouldBeEmpty()
    }

    @Test
    fun `a volume set before the first load is not applied to the loaded player`() = runTest {
        playback.setVolume(0.2f)

        load(songA)

        player.volume shouldBe 1f
        player.commands.filter { it.startsWith("setVolume") }.shouldBeEmpty()
    }

    @Test
    fun `settings requested before the first load reach the loaded player`() = runTest {
        playback.setAudioSessionId(42)
        playback.setRepeatMode(QueueManager.RepeatMode.All)

        load(songA)

        player.audioSessionId shouldBe 42
        player.repeatMode shouldBe Player.REPEAT_MODE_ALL
    }

    @Test
    fun `playback speed is not re-applied to a player rebuilt after release`() = runTest {
        // Pins today's behaviour on purpose: speed only carries the expired-trial speed ramp, which
        // #232 replaces. Until then a rebuilt player starts at normal speed.
        load(songA)
        playback.setPlaybackSpeed(0.8f)
        playback.release()

        // A released player still reports the speed it held, as it did before the player was lazy.
        playback.getPlaybackSpeed() shouldBe 0.8f

        load(songA)

        player.commands.filter { it.startsWith("setPlaybackParameters") }.shouldBeEmpty()
        playback.getPlaybackSpeed() shouldBe 1f
    }

    @Test
    fun `an unset audio session id is not applied to a rebuilt player`() = runTest {
        playback.setAudioSessionId(C.AUDIO_SESSION_ID_UNSET)

        load(songA)

        player.commands.filter { it.startsWith("audioSessionId") }.shouldBeEmpty()
    }

    @Test
    fun `no player is built until the first load`() = runTest {
        playback.setAudioSessionId(42)
        playback.setRepeatMode(QueueManager.RepeatMode.All)
        playback.setVolume(0.5f)
        playback.setPlaybackSpeed(1.5f)
        playback.loadNext(songB)
        playback.play()
        playback.pause()
        playback.seek(1_000)

        playerFactory.players.shouldBeEmpty()
        playback.isReleased shouldBe true
        playback.playBackState() shouldBe PlaybackState.Paused
        playback.getProgress() shouldBe 0
        playback.getDuration() shouldBe null
        playback.getAudioSessionId() shouldBe 42
        // Pins pre-lazy-player behaviour (#232): the requested speed is reported until the first load
        // builds a player, which starts at normal speed. The trial speed ramp is set at startup, before
        // any load, and the media session and a switch to Cast read it back.
        playback.getPlaybackSpeed() shouldBe 1.5f

        load(songA)

        playerFactory.players.size shouldBe 1
        playback.isReleased shouldBe false
        player.commands.filter { it.startsWith("setPlaybackParameters") }.shouldBeEmpty()
        playback.getPlaybackSpeed() shouldBe 1f
    }

    @Test
    fun `a load after release builds one new player and leaves the old one released`() = runTest {
        load(songA)
        playback.release()

        load(songB)

        playerFactory.players.size shouldBe 2
        playerFactory.players.first().isReleased shouldBe true
        player.isReleased shouldBe false
    }

    @Test
    fun `a player replaced while still live is released`() = runTest {
        load(songA)
        // isReleased is settable from outside the playback, without releasing its player.
        playback.isReleased = true

        load(songB)

        playerFactory.players.size shouldBe 2
        playerFactory.players.first().isReleased shouldBe true
        player.isReleased shouldBe false
    }

    @Test
    fun `a load while loaded reuses the player`() = runTest {
        load(songA)

        load(songB)

        playerFactory.players.size shouldBe 1
    }

    @Test
    fun `load queues the current and next songs`() = runTest {
        load(songA, next = songB)

        queuedUris() shouldBe listOf("/music/a.flac", "/music/b.flac")
    }

    @Test
    fun `loadNext replaces the item after the current one`() = runTest {
        load(songA, next = songB)

        playback.loadNext(songC)

        queuedUris() shouldBe listOf("/music/a.flac", "/music/c.flac")
    }

    @Test
    fun `loadNext with no song removes the item after the current one`() = runTest {
        load(songA, next = songB)

        playback.loadNext(null)

        queuedUris() shouldBe listOf("/music/a.flac")
    }

    @Test
    fun `loadNext removes every item after the current one when repeat-all wrapped back to the start`() = runTest {
        playback.setRepeatMode(QueueManager.RepeatMode.All)
        load(songA, next = songB)
        player.playToEnd()
        // The next item wasn't queued before b ended, so ExoPlayer wrapped around to the first item.
        player.playToEnd()
        player.currentMediaItemIndex shouldBe 0

        playback.loadNext(songD)

        queuedUris() shouldBe listOf("/music/a.flac", "/music/d.flac")
    }

    @Test
    fun `a gapless session keeps only the playing and next items`() = runTest {
        load(songA, next = songB)
        player.playToEnd()
        playback.loadNext(songC)
        player.playToEnd()

        playback.loadNext(songD)

        queuedUris() shouldBe listOf("/music/c.flac", "/music/d.flac")
        player.currentMediaItemIndex shouldBe 0
    }

    @Test
    fun `dropping played items reports nothing to the callback`() = runTest {
        load(songA, next = songB)
        player.playToEnd()
        callbackEvents.clear()

        playback.loadNext(songC)

        callbackEvents.shouldBeEmpty()
    }

    @Test
    fun `loadNext leaves the playlist alone when the song is already the single next item`() = runTest {
        load(songA, next = songB)
        player.commands.clear()

        playback.loadNext(songB)

        player.commands.shouldBeEmpty()
        queuedUris() shouldBe listOf("/music/a.flac", "/music/b.flac")
    }

    @Test
    fun `loadNext recognises a queued next item whose mime type the player normalised`() = runTest {
        val flac = createSong("x", mimeType = "audio/x-flac")
        load(songA, next = flac)
        player.commands.clear()

        playback.loadNext(flac)

        player.commands.shouldBeEmpty()
    }

    @Test
    fun `repeat one queues nothing after the current item`() = runTest {
        playback.setRepeatMode(QueueManager.RepeatMode.One)

        load(songA, next = songB)
        playback.loadNext(songC)

        player.repeatMode shouldBe Player.REPEAT_MODE_ONE
        queuedUris() shouldBe listOf("/music/a.flac")
    }

    @Test
    fun `repeat one leaves an already queued next item in place`() = runTest {
        load(songA, next = songB)
        playback.setRepeatMode(QueueManager.RepeatMode.One)

        playback.loadNext(songC)

        queuedUris() shouldBe listOf("/music/a.flac", "/music/b.flac")
    }

    @Test
    fun `queued items carry each song's ReplayGain values`() = runTest {
        val loud = createSong("loud", replayGainTrack = -8.5, replayGainAlbum = -7.0)
        val quiet = createSong("quiet", replayGainTrack = 3.2, replayGainAlbum = null)

        load(loud, next = quiet)

        player.playlist.map { it.replayGain } shouldBe
            listOf(
                ReplayGain(trackGain = -8.5, albumGain = -7.0),
                ReplayGain(trackGain = 3.2, albumGain = null)
            )
    }

    @Test
    fun `the ReplayGain tracker follows the replaced next item across a gapless transition`() = runTest {
        val loud = createSong("loud", replayGainTrack = -8.5, replayGainAlbum = -7.0)
        val quiet = createSong("quiet", replayGainTrack = 3.2, replayGainAlbum = null)
        load(loud, next = songB)
        playback.loadNext(quiet)
        // The sink starts the loaded item.
        replayGainTracker.onSinkRestarted()
        replayGainTracker.onSinkConfigured()
        replayGainTracker.onSinkBufferHandled()
        replayGainTracker.onProcessorFlushed()
        replayGainTracker.currentReplayGain() shouldBe ReplayGain(trackGain = -8.5, albumGain = -7.0)

        // The renderer moves on to the next item before the player reports the transition.
        replayGainTracker.onSinkConfigured()
        replayGainTracker.onSinkBufferHandled()
        replayGainTracker.onProcessorFlushed()

        replayGainTracker.currentReplayGain() shouldBe ReplayGain(trackGain = 3.2, albumGain = null)
    }

    @Test
    fun `the ReplayGain tracker stays on the playing item when played items are dropped`() = runTest {
        val first = createSong("first", replayGainTrack = -1.0)
        val second = createSong("second", replayGainTrack = -2.0)
        val third = createSong("third", replayGainTrack = -3.0)
        load(first, next = second)
        replayGainTracker.onSinkRestarted()
        replayGainTracker.onSinkConfigured()
        replayGainTracker.onSinkBufferHandled()
        replayGainTracker.onProcessorFlushed()
        // The sink reaches the second item, then the player reports the transition.
        replayGainTracker.onSinkConfigured()
        replayGainTracker.onSinkBufferHandled()
        replayGainTracker.onProcessorFlushed()
        player.playToEnd()

        playback.loadNext(third)

        replayGainTracker.currentReplayGain() shouldBe ReplayGain(trackGain = -2.0, albumGain = null)
        replayGainTracker.onSinkConfigured()
        replayGainTracker.onSinkBufferHandled()
        replayGainTracker.onProcessorFlushed()
        replayGainTracker.currentReplayGain() shouldBe ReplayGain(trackGain = -3.0, albumGain = null)
    }

    @Test
    fun `a remote song holds a network wake lock`() = runTest {
        load(createSong("stream", path = "https://server/stream"))

        player.wakeMode shouldBe C.WAKE_MODE_NETWORK
    }

    @Test
    fun `automatic and repeat transitions report the track ended and went to the next`() = runTest {
        load(songA, next = songB)

        player.playToEnd()
        playback.setRepeatMode(QueueManager.RepeatMode.One)
        player.playToEnd()

        callbackEvents.filter { it.startsWith("trackEnded") } shouldBe listOf("trackEnded true", "trackEnded true")
    }

    @Test
    fun `a position discontinuity is reported with the new position readable`() = runTest {
        load(songA)
        callbackEvents.clear()

        player.emitPositionDiscontinuity(42_000, Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT)

        callbackEvents shouldBe listOf("discontinuity 42000")
    }

    @Test
    fun `the playlist-changed transition of a load does not report the track ended`() = runTest {
        load(songA, next = songB)

        load(songC)

        callbackEvents.filter { it.startsWith("trackEnded") }.shouldBeEmpty()
    }

    @Test
    fun `a first load reports the seek to its resume position`() = runTest {
        load(songA, seekPosition = 5_000)

        callbackEvents shouldBe listOf("state ${PlaybackState.Loading}", "discontinuity 5000")
    }

    @Test
    fun `a load onto a loaded player also reports the playlist replacement as a discontinuity`() = runTest {
        load(songA)
        playback.play()
        player.emitPlaybackState(Player.STATE_READY)
        callbackEvents.clear()

        load(songB, seekPosition = 5_000)

        // The old listener is detached while the old item is paused and rewound; the replaced
        // playlist then reports a discontinuity at the new item's start before the resume seek.
        callbackEvents shouldBe listOf("state ${PlaybackState.Loading}", "discontinuity 0", "discontinuity 5000")
    }

    @Test
    fun `ended before ready is ignored`() = runTest {
        load(songA)
        callbackEvents.clear()

        player.emitPlaybackState(Player.STATE_ENDED)

        callbackEvents.shouldBeEmpty()
    }

    @Test
    fun `ended after ready pauses and reports the track ended without going to the next`() = runTest {
        load(songA)
        playback.play()
        player.emitPlaybackState(Player.STATE_READY)
        callbackEvents.clear()

        player.emitPlaybackState(Player.STATE_ENDED)

        // The pause it issues is itself reported, after the end: Media3 delivers an event raised
        // inside a listener once the current event has reached every listener.
        callbackEvents shouldBe listOf("state ${PlaybackState.Paused}", "trackEnded false", "state ${PlaybackState.Paused}")
        player.playWhenReady shouldBe false
    }

    @Test
    fun `ready while paused reports paused`() = runTest {
        load(songA)
        callbackEvents.clear()

        player.emitPlaybackState(Player.STATE_READY)

        callbackEvents shouldBe listOf("state ${PlaybackState.Paused}")
    }

    @Test
    fun `ready after play reports playing`() = runTest {
        load(songA)
        callbackEvents.clear()

        playback.play()
        player.emitPlaybackState(Player.STATE_READY)

        // play() reports itself through the player's play-when-ready change, then ready again.
        callbackEvents shouldBe listOf("state ${PlaybackState.Playing}", "state ${PlaybackState.Playing}")
        player.isPlaying shouldBe true
    }

    @Test
    fun `pause reports paused through the player`() = runTest {
        load(songA)
        playback.play()
        player.emitPlaybackState(Player.STATE_READY)
        callbackEvents.clear()

        playback.pause()

        callbackEvents shouldBe listOf("state ${PlaybackState.Paused}")
    }

    @Test
    fun `a player error reports paused`() = runTest {
        load(songA)
        callbackEvents.clear()

        player.emitError(IllegalStateException("boom"))

        callbackEvents shouldBe listOf("state ${PlaybackState.Paused}")
    }

    @Test
    fun `playback speed sets speed and pitch together`() = runTest {
        load(songA)

        playback.setPlaybackSpeed(1.5f)

        player.commands.last() shouldBe "setPlaybackParameters 1.5 1.5"
        playback.getPlaybackSpeed() shouldBe 1.5f
    }

    private fun createSong(
        name: String,
        path: String = "/music/$name.flac",
        mimeType: String = "audio/flac",
        replayGainTrack: Double? = null,
        replayGainAlbum: Double? = null
    ) = Song(
        id = name.hashCode().toLong(),
        name = name,
        albumArtist = null,
        artists = emptyList(),
        album = null,
        track = null,
        disc = null,
        duration = 180_000,
        date = null,
        genres = emptyList(),
        path = path,
        size = 0,
        mimeType = mimeType,
        lastModified = null,
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        mediaProvider = MediaProviderType.Shuttle,
        replayGainTrack = replayGainTrack,
        replayGainAlbum = replayGainAlbum,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )
}
