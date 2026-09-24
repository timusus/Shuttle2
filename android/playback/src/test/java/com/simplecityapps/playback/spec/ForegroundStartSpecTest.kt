package com.simplecityapps.playback.spec

import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.simplecityapps.playback.ForegroundStarts
import com.simplecityapps.playback.PlaybackService
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.R
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.song
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.ShadowService

/**
 * The foreground rules in docs/testing/playback-behaviour-spec.md: a start of the playback service in the foreground (the
 * widget, a shortcut, a headset's play button) as the app starts, with the saved queue still being restored. The service
 * under test is [PlaybackService]'s start handling and foreground hold over a [SessionHarness]'s session, as
 * PlaybackService itself needs Hilt.
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
class ForegroundStartSpecTest {
    private val saved = listOf(song(1), song(2))

    private val harness = SessionHarness(restored = false)

    private val queue = harness.playback.queueOperations

    private lateinit var service: ServiceController<StartedService>

    @After
    fun tearDown() {
        service.destroy()
        harness.release()
    }

    @Test
    fun `RS-48 a cold start from the widget stays in the foreground until the saved queue plays`() {
        val foreground = start(Intent(PlaybackService.ACTION_TOGGLE_PLAYBACK))

        foreground.lastForegroundNotificationId shouldBe PlaybackService.NOTIFICATION_ID
        // Media3 has no notification to show while the queue is empty, or while the restored queue isn't playing yet, and
        // would take the service out of the foreground.
        stayForegroundUntil(foreground) { true }

        restore(foreground)
        // Media3's own notification takes the foreground over once the queue plays.
        stayForegroundUntil(foreground) { isPlaying() && foreground.isMedia3Notification() }
    }

    @Test
    fun `RS-48 a cold start from a headset's play button stays in the foreground until the saved queue plays`() {
        val play = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
        val foreground =
            start(
                Intent(Intent.ACTION_MEDIA_BUTTON)
                    .setComponent(ComponentName(harness.playback.context, StartedService::class.java))
                    .putExtra(Intent.EXTRA_KEY_EVENT, play)
            )

        foreground.lastForegroundNotificationId shouldBe PlaybackService.NOTIFICATION_ID
        stayForegroundUntil(foreground) { true }

        restore(foreground)
        stayForegroundUntil(foreground) { isPlaying() && foreground.isMedia3Notification() }
        queue.queueStateFlow.value.currentItem?.song shouldBe saved[1]
    }

    @Test
    fun `RS-48 a cold start that doesn't play leaves the foreground once its command has run`() {
        val foreground = start(Intent(PlaybackService.ACTION_TOGGLE_SHUFFLE))
        stayForegroundUntil(foreground) { true }

        restore(foreground)
        harness.playback.runUntil { foreground.isForegroundStopped }
    }

    private fun start(intent: Intent): ShadowService {
        StartedService.harness = harness
        service = Robolectric.buildService(StartedService::class.java, intent).create().startCommand(0, 1)
        return shadowOf(service.get())
    }

    /** Turns the main looper until [condition], failing if the service leaves the foreground on the way. */
    private fun stayForegroundUntil(
        foreground: ShadowService,
        condition: () -> Boolean
    ) {
        harness.playback.idle()
        harness.playback.runUntil {
            foreground.isForegroundStopped shouldBe false
            condition()
        }
    }

    private fun isPlaying() = harness.playback.playbackOperations.playbackStateFlow.value == PlaybackState.Playing

    /**
     * As the app's restore does, once the service has started: the saved queue is set (Media3 sees it while the player is
     * idle, and has no notification to show for it), then marked restored. The app loads it in between; that's left to
     * the command here, as waiting on the player turns the looper's clock past the restore's time limit.
     */
    private fun restore(foreground: ShadowService) {
        harness.playback.run { queue.setQueue(saved, position = 1) }
        stayForegroundUntil(foreground) { true }
        queue.hasRestoredQueue = true
    }

    private fun ShadowService.isMedia3Notification(): Boolean = lastForegroundNotification?.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true

    /** [PlaybackService]'s start handling and foreground hold, over the harness's session. */
    class StartedService : MediaLibraryService() {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        private lateinit var foregroundStarts: ForegroundStarts

        override fun onCreate() {
            super.onCreate()
            setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).setNotificationId(PlaybackService.NOTIFICATION_ID).build())
            foregroundStarts = ForegroundStarts(this, harness.session.player, scope, PlaybackService.NOTIFICATION_ID) {
                NotificationCompat.Builder(this, PlaybackService.NOTIFICATION_CHANNEL_ID).setSmallIcon(R.drawable.ic_stat_name).build()
            }
            addSession(harness.session)
        }

        override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = harness.session

        override fun onStartCommand(
            intent: Intent?,
            flags: Int,
            startId: Int
        ): Int {
            val result = super.onStartCommand(intent, flags, startId)
            if (intent != null) PlaybackService.handleStart(intent, foregroundStarts, harness.playback.playbackOperations, harness.playback.queueOperations)
            return result
        }

        override fun onUpdateNotificationAsync(
            session: MediaSession,
            startInForegroundRequired: Boolean
        ): ListenableFuture<Void?> = if (foregroundStarts.mayUpdateNotification(startInForegroundRequired)) {
            super.onUpdateNotificationAsync(session, startInForegroundRequired)
        } else {
            Futures.immediateVoidFuture()
        }

        override fun onDestroy() {
            scope.cancel()
            removeSession(harness.session)
            super.onDestroy()
        }

        companion object {
            lateinit var harness: SessionHarness
        }
    }
}
