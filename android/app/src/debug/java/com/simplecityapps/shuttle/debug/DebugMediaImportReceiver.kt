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
        CoroutineScope(Dispatchers.IO).launch {
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
