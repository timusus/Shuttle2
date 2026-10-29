package com.simplecityapps.shuttle.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.simplecityapps.shuttle.di.appGraph
import com.simplecityapps.shuttle.settings.DebugSettings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject

/**
 * Debug-build-only: [EXTRA_USE_RECORDING_IDS] false merges song copies on the plain-tag rule alone, to check it against
 * a library that has MusicBrainz recording ids; the library re-merges at once.
 */
class DebugSongCopiesReceiver : BroadcastReceiver() {
    @ContributesTo(AppScope::class)
    interface Injector {
        fun inject(receiver: DebugSongCopiesReceiver)
    }

    @Inject
    lateinit var debugSettings: DebugSettings

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        context.appGraph<Injector>().inject(this)
        debugSettings.copyRecordingIds.value = intent.getBooleanExtra(EXTRA_USE_RECORDING_IDS, true)
    }

    companion object {
        const val ACTION_SONG_COPIES = "com.simplecityapps.shuttle.debug.ACTION_SONG_COPIES"
        const val EXTRA_USE_RECORDING_IDS = "use_recording_ids"
    }
}
