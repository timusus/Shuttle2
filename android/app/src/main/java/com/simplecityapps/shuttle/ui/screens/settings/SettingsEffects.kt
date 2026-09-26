package com.simplecityapps.shuttle.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.TransactionTooLargeException
import com.simplecityapps.imageloading.ArtworkDownloadService
import com.simplecityapps.imageloading.ArtworkImageLoader
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.settings.LibrarySettings
import com.simplecityapps.mediaprovider.worker.ImportFrequency
import com.simplecityapps.mediaprovider.worker.MediaImportWorker
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.debug.DebugLoggingTree
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.Setting
import com.simplecityapps.shuttle.ui.ThemeManager
import com.simplecityapps.shuttle.ui.widgets.WidgetManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What a settings change or action does beyond storing a value: the live processors, workers, widgets and
 * system services the legacy preference screens poked. Kept behind an interface so the ViewModels stay free of
 * Context and are tested against a fake.
 */
interface SettingsEffects {
    /** Called after [setting] has been written with [value]. */
    fun <T> onSettingChanged(
        setting: Setting<T>,
        value: T
    )

    /** When the library was last scanned, if it has been. */
    fun lastScanDate(): Date?

    /** Starts a library scan that outlives the screen. */
    fun rescan()

    suspend fun clearArtworkCache()

    fun downloadAllArtwork()

    suspend fun copyDebugLogs(): CopyDebugLogsResult
}

enum class CopyDebugLogsResult { Copied, TooLarge, Empty }

class AndroidSettingsEffects @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val replayGainAudioProcessor: ReplayGainAudioProcessor,
    private val widgetManager: WidgetManager,
    private val themeManager: ThemeManager,
    private val mediaImporter: MediaImporter,
    private val imageLoader: ArtworkImageLoader,
    private val generalPreferenceManager: GeneralPreferenceManager
) : SettingsEffects {
    override fun <T> onSettingChanged(
        setting: Setting<T>,
        value: T
    ) {
        when (setting) {
            AppearanceSettings.Theme -> themeManager.setDayNightMode()
            AppearanceSettings.WidgetBackgroundOpacity -> widgetManager.onBackgroundOpacityChanged(value as Int)
            PlaybackSettings.ReplayGain -> replayGainAudioProcessor.mode = value as ReplayGainMode
            PlaybackSettings.PreAmpGain -> replayGainAudioProcessor.preAmpGain = (value as Float).toDouble()
            LibrarySettings.RescanFrequency -> MediaImportWorker.updateWork(context, value as ImportFrequency)
        }
    }

    override fun lastScanDate(): Date? = generalPreferenceManager.lastMediaImportDate

    override fun rescan() {
        appScope.launch { mediaImporter.import() }
    }

    override suspend fun clearArtworkCache() {
        imageLoader.clearCache(context)
    }

    override fun downloadAllArtwork() {
        context.startService(Intent(context, ArtworkDownloadService::class.java))
    }

    override suspend fun copyDebugLogs(): CopyDebugLogsResult {
        val logs = withContext(Dispatchers.IO) {
            context.getFileStreamPath(DebugLoggingTree.FILE_NAME).takeIf { it.exists() }?.readText()
        }
        if (logs.isNullOrEmpty()) return CopyDebugLogsResult.Empty
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return try {
            clipboardManager.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.settings_logging_clipboard_name), logs))
            CopyDebugLogsResult.Copied
        } catch (e: Exception) {
            // The system server rethrows a clip over the binder limit wrapped in a RuntimeException
            if (e is TransactionTooLargeException || e.cause is TransactionTooLargeException) CopyDebugLogsResult.TooLarge else throw e
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsEffectsModule {
    @Binds
    abstract fun bindSettingsEffects(effects: AndroidSettingsEffects): SettingsEffects
}
