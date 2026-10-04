package com.simplecityapps.shuttle.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.SyncTrigger
import com.simplecityapps.shuttle.di.appGraph
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug-build-only: lets `support/scripts/seed-test-media.sh` trigger a library import via
 * `adb shell am broadcast` instead of waiting on a real onboarding pass or a scheduled import. With [EXTRA_SYNC]
 * it runs the scheduled background sync instead, which trusts MediaStore where an import walks the chosen folders.
 *
 * Launched on Main, like the onboarding scan's `appCoroutineScope` call: [MediaImporter.import]
 * moves its own work to IO but notifies its listeners on the caller's thread, and an IO caller
 * crashes Settings > Media's listener if the import finishes while that screen is open (#386).
 */
class DebugMediaImportReceiver : BroadcastReceiver() {
    @ContributesTo(AppScope::class)
    interface Injector {
        fun inject(receiver: DebugMediaImportReceiver)
    }

    @Inject
    lateinit var mediaImporter: MediaImporter

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        context.appGraph<Injector>().inject(this)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (intent.getBooleanExtra(EXTRA_SYNC, false)) mediaImporter.sync(SyncTrigger.Periodic) else mediaImporter.import()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_IMPORT_MEDIA = "com.simplecityapps.shuttle.debug.ACTION_IMPORT_MEDIA"
        const val EXTRA_SYNC = "sync"
    }
}
