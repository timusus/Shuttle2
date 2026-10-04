package com.simplecityapps.shuttle.ui.screens.settings

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
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
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The Android [SettingsEffects]: live audio processors, WorkManager, widgets, the log share sheet and services. */
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
            LibrarySettings.RescanFrequency -> MediaImportWorker.updateWork(context, value as ImportFrequency, hasRemoteSource = mediaImporter.mediaProviders.any { it.type.remote })
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

    override suspend fun shareDebugLogs(): ShareDebugLogsResult {
        val shared = withContext(Dispatchers.IO) {
            val log = context.getFileStreamPath(DebugLoggingTree.FILE_NAME).takeIf { it.exists() && it.length() > 0 } ?: return@withContext null
            File(context.cacheDir, SHARED_LOGS_DIR).apply { mkdirs() }
                .resolve(SHARED_LOG_NAME)
                .also { log.copyTo(it, overwrite = true) }
        } ?: return ShareDebugLogsResult.Empty
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shared))
            .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.settings_logging_name))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return ShareDebugLogsResult.Shared
    }

    private companion object {
        /** A subdirectory of the cache, named in `res/xml/file_paths.xml`. */
        const val SHARED_LOGS_DIR = "shared_logs"
        const val SHARED_LOG_NAME = "shuttle-debug-log.txt"
    }
}

@BindingContainer
@ContributesTo(AppScope::class)
abstract class SettingsEffectsModule {
    @Binds
    abstract fun bindSettingsEffects(effects: AndroidSettingsEffects): SettingsEffects
}
