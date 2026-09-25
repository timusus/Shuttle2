package com.simplecityapps.shuttle.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.simplecityapps.mediaprovider.MediaImporter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug-build-only: lets `support/scripts/seed-test-media.sh` trigger a library import via
 * `adb shell am broadcast` instead of waiting on a real onboarding pass or a scheduled import.
 *
 * Launched on Main, like the onboarding scan's `appCoroutineScope` call: [MediaImporter.import]
 * moves its own work to IO but notifies its listeners on the caller's thread, and an IO caller
 * crashes Settings > Media's listener if the import finishes while that screen is open (#386).
 */
@AndroidEntryPoint
class DebugMediaImportReceiver : BroadcastReceiver() {
    @Inject
    lateinit var mediaImporter: MediaImporter

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                mediaImporter.import()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_IMPORT_MEDIA = "com.simplecityapps.shuttle.debug.ACTION_IMPORT_MEDIA"
    }
}
