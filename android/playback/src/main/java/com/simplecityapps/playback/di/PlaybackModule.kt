package com.simplecityapps.playback.di

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import androidx.core.content.getSystemService
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.AudioEffectSessionManager
import com.simplecityapps.playback.BitPerfectOutput
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.androidauto.MediaIdHelper
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.engine.SongUriResolver
import com.simplecityapps.playback.exoplayer.AudioTrackMonitor
import com.simplecityapps.playback.exoplayer.EqualizerAudioProcessor
import com.simplecityapps.playback.mediasession.UriSongResolver
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.persistence.QueueStore
import com.simplecityapps.playback.queue.QueueFacade
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.QueueSongRefresher
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.playback.sleeptimer.SleepTimer
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.di.ApplicationContext
import com.squareup.moshi.Moshi
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope

@ContributesTo(AppScope::class)
@BindingContainer
class PlaybackModule {
    @SingleIn(AppScope::class)
    @Provides
    fun provideQueueFacade(
        player: ExoPlayer,
        activePlayer: Player,
        playbackSettings: PlaybackSettings,
        songUriResolver: SongUriResolver
    ): QueueFacade = QueueFacade(player, playbackSettings, songUriResolver, activePlayer = activePlayer)

    @Provides
    fun provideQueueOperations(queueFacade: QueueFacade): QueueOperations = queueFacade

    @SingleIn(AppScope::class)
    @Provides
    fun provideQueueStore(
        activePlayer: Player,
        localPlayer: ExoPlayer,
        queueFacade: QueueFacade,
        playbackPreferenceManager: PlaybackPreferenceManager,
        songRepository: SongRepository,
        @AppCoroutineScope appCoroutineScope: CoroutineScope
    ): QueueStore = QueueStore(activePlayer, localPlayer, queueFacade, playbackPreferenceManager, songRepository, appCoroutineScope)

    @SingleIn(AppScope::class)
    @Provides
    fun provideQueueSongRefresher(
        songRepository: SongRepository,
        queueOperations: QueueOperations,
        @AppCoroutineScope appCoroutineScope: CoroutineScope
    ): QueueSongRefresher = QueueSongRefresher(songRepository, queueOperations, appCoroutineScope)

    @SingleIn(AppScope::class)
    @Provides
    fun providePlaybackPreferenceManager(
        sharedPreferences: SharedPreferences,
        moshi: Moshi
    ): PlaybackPreferenceManager = PlaybackPreferenceManager(sharedPreferences, moshi)

    @Provides
    fun provideMediaIdHelper(
        playlistRepository: PlaylistRepository,
        artistRepository: AlbumArtistRepository,
        albumRepository: AlbumRepository,
        songRepository: SongRepository
    ): MediaIdHelper = MediaIdHelper(playlistRepository, artistRepository, albumRepository, songRepository)

    @Provides
    fun provideUriSongResolver(
        @ApplicationContext context: Context,
        songRepository: SongRepository
    ): UriSongResolver = UriSongResolver(context, songRepository)

    @Provides
    fun provideAudioManager(
        @ApplicationContext context: Context
    ): AudioManager? = context.getSystemService()

    @Provides
    fun provideAudioEffectSessionManager(
        @ApplicationContext context: Context
    ): AudioEffectSessionManager = AudioEffectSessionManager(context)

    @SingleIn(AppScope::class)
    @Provides
    fun provideBitPerfectOutput(
        audioManager: AudioManager?,
        playbackSettings: PlaybackSettings,
        audioTrackMonitor: AudioTrackMonitor,
        equalizerAudioProcessor: EqualizerAudioProcessor,
        replayGainAudioProcessor: ReplayGainAudioProcessor,
        @AppCoroutineScope appCoroutineScope: CoroutineScope
    ): BitPerfectOutput = BitPerfectOutput(audioManager, playbackSettings, audioTrackMonitor, equalizerAudioProcessor, replayGainAudioProcessor, appCoroutineScope)

    @SingleIn(AppScope::class)
    @Provides
    fun provideSleepTimer(
        playbackOperations: PlaybackOperations,
        @AppCoroutineScope appCoroutineScope: CoroutineScope
    ): SleepTimer = SleepTimer(playbackOperations, appCoroutineScope)
}
