package com.simplecityapps.shuttle.ui.shell.player

import android.content.Context
import android.graphics.Bitmap
import com.bumptech.glide.Glide
import com.simplecityapps.playback.chromecast.CastSessionManager
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.designsystem.theme.ArtworkSeed
import com.simplecityapps.shuttle.designsystem.theme.SeedColorCache
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.ui.screens.settings.SettingsEffects
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Extracts seeds from a small Glide bitmap of the song's artwork, cached per album. */
class GlideArtworkSeedSource(
    private val context: Context,
    private val cache: SeedColorCache = SeedColorCache(),
) : ArtworkSeedSource {
    override suspend fun seedFor(song: Song): ArtworkSeed = cache.getOrExtract(song.albumGroupKey.toString()) { loadBitmap(song) }

    private suspend fun loadBitmap(song: Song): Bitmap? = withContext(Dispatchers.IO) {
        val target = Glide.with(context).asBitmap().load(song).submit(SEED_BITMAP_SIZE, SEED_BITMAP_SIZE)
        try {
            target.get()
        } catch (e: Exception) {
            Timber.v(e, "No artwork seed for ${song.name}")
            null
        } finally {
            Glide.with(context).clear(target)
        }
    }

    private companion object {
        const val SEED_BITMAP_SIZE = 112
    }
}

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {
    @Provides
    @Singleton
    fun provideArtworkSeedSource(
        @ApplicationContext context: Context,
    ): ArtworkSeedSource = GlideArtworkSeedSource(context)

    @Provides
    fun provideCastAvailability(castSessionManager: CastSessionManager): CastAvailability = CastAvailability { castSessionManager.isAvailable }

    @Provides
    fun provideSavedNowPlaying(playbackPreferenceManager: PlaybackPreferenceManager): SavedNowPlaying = SavedNowPlaying { playbackPreferenceManager.nowPlaying }

    @Provides
    fun provideSleepTimerPreference(preferenceManager: GeneralPreferenceManager): SleepTimerPreference = object : SleepTimerPreference {
        override var playToEnd: Boolean
            get() = preferenceManager.sleepTimerPlayToEnd
            set(value) {
                preferenceManager.sleepTimerPlayToEnd = value
            }
    }

    // The same write and live-processor effect as the Settings screen's ReplayGain choice.
    @Provides
    fun provideReplayGainPreference(
        playbackSettings: PlaybackSettings,
        settingsEffects: SettingsEffects,
    ): ReplayGainPreference = object : ReplayGainPreference {
        override val mode: Flow<ReplayGainMode> = playbackSettings.replayGainMode.flow

        override fun set(mode: ReplayGainMode) {
            playbackSettings.replayGainMode.value = mode
            settingsEffects.onSettingChanged(PlaybackSettings.ReplayGain, mode)
        }
    }

    @Provides
    fun provideColourFromArtworkPreference(appearanceSettings: AppearanceSettings): ColourFromArtworkPreference = object : ColourFromArtworkPreference {
        override val enabled: Flow<Boolean> = appearanceSettings.colourFromArtwork.flow
    }
}
