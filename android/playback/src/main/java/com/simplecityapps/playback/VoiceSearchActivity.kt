package com.simplecityapps.playback

import android.app.Activity
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import timber.log.Timber

/**
 * Where a voice search ("play Radiohead on S2", [MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH]) arrives, whether the
 * app is running or not. It has no UI: it starts the playback service in the foreground with the search, while an
 * activity of the app's is showing and so may, and finishes. The service plays it once the saved queue is restored,
 * and stays in the foreground until it has ([ForegroundStarts]).
 *
 * The search runs once, in onCreate: the activity keeps no history, so it's never recreated or relaunched from recents
 * with the search again.
 */
class VoiceSearchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent?.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
            val serviceIntent = Intent(this, PlaybackService::class.java)
                .setAction(PlaybackService.ACTION_PLAY_FROM_SEARCH)
                .putExtras(intent.extras ?: Bundle.EMPTY)
            try {
                ContextCompat.startForegroundService(this, serviceIntent)
            } catch (e: IllegalStateException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                    Timber.w(e, "Can't start the playback service for a voice search")
                } else {
                    throw e
                }
            }
        }
        finish()
    }
}
