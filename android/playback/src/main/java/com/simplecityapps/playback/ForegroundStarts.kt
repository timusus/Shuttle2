package com.simplecityapps.playback

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.view.KeyEvent
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Keeps a [MediaSessionService] started in the foreground there until the command it was started for has run, then
 * hands the foreground state back to Media3.
 *
 * A start with startForegroundService must be followed by startForeground within seconds. Media3 only goes to the
 * foreground once it has a notification to show and the player is playing, which, as the app starts, waits on the
 * saved queue being restored: the widget's play button, a shortcut, or a headset's play button (whose start Media3's
 * MediaButtonReceiver makes, but whose play Media3 leaves until the queue is back, through
 * [onPlaybackResumption][androidx.media3.session.MediaSession.Callback.onPlaybackResumption]) would crash the app with
 * ForegroundServiceDidNotStartInTimeException.
 *
 * So [start] goes to the foreground at once, with Media3's notification if it's showing and a placeholder under the same
 * id if not, and [mayUpdateNotification] (the service's onUpdateNotificationAsync) keeps Media3 from taking it down
 * until the command has run. Then, once the player plays, Media3 puts its own notification in the foreground; if it
 * doesn't play, Media3 shows the paused notification, or none, and leaves the foreground as it would anyway.
 */
@UnstableApi
class ForegroundStarts(
    private val service: MediaSessionService,
    private val player: Player,
    private val scope: CoroutineScope,
    private val notificationId: Int,
    private val placeholder: () -> Notification
) {
    private var running = 0

    private var holding = false

    /** Goes to the foreground now, and stays there until [command] has run and Media3 takes the foreground over. */
    fun start(command: suspend () -> Unit) {
        running++
        holding = true
        val notification = service.getSystemService(NotificationManager::class.java).activeNotifications.firstOrNull { it.id == notificationId }?.notification ?: placeholder()
        try {
            Util.setForegroundServiceNotification(service, notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK, "mediaPlayback")
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (API 31+): started with startService from the background.
            Timber.w(e, "Unable to start the service in the foreground")
        }
        scope.launch {
            try {
                command()
            } finally {
                running--
                // Once the player plays, Media3 asks for the foreground itself (see mayUpdateNotification).
                if (running == 0 && isActive && !player.isEngaged()) {
                    holding = false
                    service.triggerNotificationUpdate()
                }
            }
        }
    }

    /**
     * Whether Media3 may update the notification and the foreground state now, from the service's
     * onUpdateNotificationAsync: not while a start's command is still running, unless Media3 is going to the foreground
     * itself.
     */
    fun mayUpdateNotification(startInForegroundRequired: Boolean): Boolean {
        if (holding && (startInForegroundRequired || (running == 0 && !player.isEngaged()))) {
            holding = false
        }
        return !holding
    }

    private fun Player.isEngaged(): Boolean = playWhenReady && (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_READY)

    companion object {
        /**
         * How long a headset's play button holds the foreground once the queue is restored: longer than the wait Media3
         * makes for a double tap (ViewConfiguration.getDoubleTapTimeout, 300ms) before it plays or resumes.
         */
        const val MEDIA_BUTTON_SETTLE_MS = 1_000L

        /** Whether [intent] is a play button's, which Media3's MediaButtonReceiver starts the service in the foreground for. */
        fun isPlayButton(intent: Intent): Boolean = intent.action == Intent.ACTION_MEDIA_BUTTON &&
            intent.keyEvent()?.keyCode in playKeyCodes

        private fun Intent.keyEvent(): KeyEvent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        }

        private val playKeyCodes = setOf(KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK)
    }
}
