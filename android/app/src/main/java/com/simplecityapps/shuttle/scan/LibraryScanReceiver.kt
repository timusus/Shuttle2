package com.simplecityapps.shuttle.scan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.shuttle.di.appGraph
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Release-build rescan trigger for automation apps and file-sync tools (Tasker, FolderSync, ...):
 *
 * `adb shell am broadcast -a com.simplecityapps.shuttle.action.RESCAN_LIBRARY`
 *
 * The debug [com.simplecityapps.shuttle.debug.DebugMediaImportReceiver] does the same thing, but it lives in the
 * debug source set behind the DUMP permission so only adb can fire it. This receiver is deliberately unguarded, so
 * third-party apps can fire it too. Rapid repeat triggers are debounced [DEBOUNCE_MS], and [MediaImporter.import]
 * coalesces with an import already running through its own importLock.
 *
 * Launched on Main, like the onboarding scan's `appCoroutineScope` call: [MediaImporter.import] moves its own work
 * to IO but notifies its listeners on the caller's thread, and an IO caller crashes Settings > Media's listener if
 * the import finishes while that screen is open (#386).
 */
class LibraryScanReceiver : BroadcastReceiver() {
    @ContributesTo(AppScope::class)
    interface Injector {
        fun inject(receiver: LibraryScanReceiver)
    }

    @Inject
    lateinit var mediaImporter: MediaImporter

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        if (intent.action != ACTION_RESCAN_LIBRARY) return
        if (!debounce()) return
        context.appGraph<Injector>().inject(this)
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
        const val ACTION_RESCAN_LIBRARY = "com.simplecityapps.shuttle.action.RESCAN_LIBRARY"

        const val DEBOUNCE_MS = 60_000L

        @Volatile
        private var lastTriggerUptimeMs = 0L

        internal fun debounce(nowUptimeMs: Long = SystemClock.uptimeMillis()): Boolean {
            synchronized(this) {
                if (nowUptimeMs - lastTriggerUptimeMs < DEBOUNCE_MS) return false
                lastTriggerUptimeMs = nowUptimeMs
                return true
            }
        }
    }
}
