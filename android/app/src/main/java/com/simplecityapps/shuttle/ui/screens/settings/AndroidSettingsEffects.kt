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
import com.simplecityapps.shuttle.di.ApplicationContext
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.Setting
import com.simplecityapps.shuttle.ui.ThemeManager
import com.simplecityapps.shuttle.ui.widgets.WidgetManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The Android [SettingsEffects]: live audio processors, WorkManager, widgets, the clipboard and services. */
class AndroidSettingsEffects @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val replayGainAudioProcessor: ReplayGainAudioProcessor,
    private val widgetManager: WidgetManager,
    private val themeManager: ThemeManager,
    private val mediaImporter: MediaImporter,
    private val imageLoader: ArtworkImageLoader
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

    override fun rescan() {
        appScope.launch { mediaImporter.import() }
    }

    override suspend fun clearArtworkCache() {
        imageLoader.clearCache()
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

@BindingContainer
@ContributesTo(AppScope::class)
abstract class SettingsEffectsModule {
    @Binds
    abstract fun bindSettingsEffects(effects: AndroidSettingsEffects): SettingsEffects
}
