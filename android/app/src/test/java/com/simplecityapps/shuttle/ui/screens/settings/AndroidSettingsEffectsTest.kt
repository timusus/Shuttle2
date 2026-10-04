package com.simplecityapps.shuttle.ui.screens.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.simplecityapps.imageloading.ArtworkDownloadService
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.debug.DebugLoggingTree
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.ThemeMode
import com.simplecityapps.shuttle.ui.ThemeManager
import com.simplecityapps.shuttle.ui.widgets.WidgetManager
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** What a settings change reaches beyond the stored value: the live processor, widgets, theme, services and the log share sheet. */
@RunWith(RobolectricTestRunner::class)
class AndroidSettingsEffectsTest {
    private val context: Application = RuntimeEnvironment.getApplication()
    private val replayGain = ReplayGainAudioProcessor(ReplayGainMode.Off)
    private val widgetManager = mockk<WidgetManager>(relaxed = true)
    private val themeManager = mockk<ThemeManager>(relaxed = true)

    private val effects = AndroidSettingsEffects(
        context = context,
        appScope = TestScope(),
        replayGainAudioProcessor = replayGain,
        widgetManager = widgetManager,
        themeManager = themeManager,
        mediaImporter = mockk(relaxed = true),
        imageLoader = mockk(relaxed = true)
    )

    @Before
    fun setUp() {
        context.deleteFile(DebugLoggingTree.FILE_NAME)
    }

    @Test
    fun `replay gain mode and pre-amp reach the playing audio`() {
        effects.onSettingChanged(PlaybackSettings.ReplayGain, ReplayGainMode.Album)
        effects.onSettingChanged(PlaybackSettings.PreAmpGain, 4.5f)

        replayGain.mode shouldBe ReplayGainMode.Album
        replayGain.preAmpGain shouldBe 4.5
    }

    @Test
    fun `the widget opacity redraws the widgets`() {
        effects.onSettingChanged(AppearanceSettings.WidgetBackgroundOpacity, 40)

        verify { widgetManager.onBackgroundOpacityChanged(40) }
    }

    @Test
    fun `the theme reapplies day and night`() {
        effects.onSettingChanged(AppearanceSettings.Theme, ThemeMode.Dark)

        verify { themeManager.setDayNightMode() }
    }

    @Test
    fun `download all artwork starts the artwork download service`() {
        effects.downloadAllArtwork()

        shadowOf(context).nextStartedService?.component?.className shouldBe ArtworkDownloadService::class.java.name
    }

    @Test
    fun `sharing debug logs offers a copy of the log file to share`() = runTest {
        context.openFileOutput(DebugLoggingTree.FILE_NAME, Context.MODE_PRIVATE).use { it.write("Playback started".toByteArray()) }

        effects.shareDebugLogs() shouldBe ShareDebugLogsResult.Shared

        val chooser = shadowOf(context).nextStartedActivity
        chooser.action shouldBe Intent.ACTION_CHOOSER
        val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        send.action shouldBe Intent.ACTION_SEND
        val uri = send.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)!!
        uri.authority shouldBe "${context.packageName}.fileprovider"
        context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } shouldBe "Playback started"
    }

    @Test
    fun `sharing debug logs with no log file says there are none`() = runTest {
        effects.shareDebugLogs() shouldBe ShareDebugLogsResult.Empty
        shadowOf(context).nextStartedActivity shouldBe null
    }
}
