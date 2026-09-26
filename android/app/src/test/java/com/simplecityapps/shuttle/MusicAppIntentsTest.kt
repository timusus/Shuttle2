package com.simplecityapps.shuttle

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The intents the system, launchers, Assistant and other apps use to find a music player all resolve to S2 (#106).
 * Resolved against the merged manifest, so the playback module's components count too.
 */
@RunWith(RobolectricTestRunner::class)
class MusicAppIntentsTest {
    private val context = RuntimeEnvironment.getApplication()
    private val packageManager = context.packageManager

    private fun activitiesFor(intent: Intent): List<String> = packageManager
        .queryIntentActivities(intent.setPackage(context.packageName), PackageManager.MATCH_DEFAULT_ONLY)
        .map { it.activityInfo.name }

    @Test
    fun `S2 is listed as a music app, for the default music app setting`() {
        activitiesFor(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC).selector!!) shouldContain MAIN_ACTIVITY
        activitiesFor(Intent(MediaStore.INTENT_ACTION_MUSIC_PLAYER)) shouldContain MAIN_ACTIVITY
    }

    @Test
    fun `Assistant's play from search reaches the voice search activity`() {
        activitiesFor(Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).putExtra("query", "Radiohead")) shouldContain VOICE_SEARCH_ACTIVITY
    }

    @Test
    fun `audio files from other apps open in S2, and other files don't`() {
        activitiesFor(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://media/external/audio/media/1"), "audio/mpeg")) shouldContain MAIN_ACTIVITY
        activitiesFor(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("file:///sdcard/Music/song.flac"), "audio/flac")) shouldContain MAIN_ACTIVITY
        activitiesFor(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://downloads/1"), "application/ogg")) shouldContain MAIN_ACTIVITY
        activitiesFor(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://media/external/video/media/1"), "video/mp4")) shouldNotContain MAIN_ACTIVITY
    }

    @Test
    fun `apps that list media players find S2's media buttons and media browser`() {
        packageManager.queryBroadcastReceivers(Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(context.packageName), 0)
            .map { it.activityInfo.name } shouldContain "androidx.media3.session.MediaButtonReceiver"
        packageManager.queryIntentServices(Intent("android.media.browse.MediaBrowserService").setPackage(context.packageName), 0)
            .map { it.serviceInfo.name } shouldContain PLAYBACK_SERVICE
    }

    private companion object {
        const val MAIN_ACTIVITY = "com.simplecityapps.shuttle.ui.MainActivity"
        const val VOICE_SEARCH_ACTIVITY = "com.simplecityapps.playback.VoiceSearchActivity"
        const val PLAYBACK_SERVICE = "com.simplecityapps.playback.PlaybackService"
    }
}
