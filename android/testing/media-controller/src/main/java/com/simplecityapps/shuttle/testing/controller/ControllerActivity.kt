package com.simplecityapps.shuttle.testing.controller

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor

/**
 * Runs one command from the intent's extras against Shuttle's session, then logs `S2CTRL cmd=<cmd> result=<...>` and
 * finishes:
 *
 *   am start -n com.simplecityapps.shuttle.testing.controller/.ControllerActivity --es cmd <cmd> [args]
 *
 * Media3 commands (`pkg` overrides the target app): `state`, `play`, `pause`, `next`, `add --es mediaId`,
 * `move --ei from --ei to`, `remove --ei index`, `clear`, `browse-root`, `browse-children --es parent`.
 * Legacy-session commands (MediaControllerCompat): `compat-play-id --es mediaId`, `compat-search --es query`
 * (empty: resume the queue).
 */
class ControllerActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executor { main.post(it) }
    private val cleanups = mutableListOf<() -> Unit>()
    private var cmd = ""
    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cmd = intent.getStringExtra("cmd").orEmpty()
        val component = ComponentName(intent.getStringExtra("pkg") ?: SHUTTLE_PACKAGE, SERVICE_CLASS)
        main.postDelayed({ report("timeout") }, TIMEOUT_MS)
        try {
            when (cmd) {
                "compat-play-id", "compat-search" -> runCompat(component)
                "browse-root", "browse-children" -> runBrowse(component)
                else -> runController(component)
            }
        } catch (e: Exception) {
            report("error", "exception" to e)
        }
    }

    override fun onDestroy() {
        cleanups.forEach { it() }
        super.onDestroy()
    }

    private fun runController(component: ComponentName) {
        val future = MediaController.Builder(this, SessionToken(this, component)).buildAsync()
        cleanups += { MediaController.releaseFuture(future) }
        future.then {
            val c = it.get()
            val available = { command: Int -> c.isCommandAvailable(command) }
            val extras = arrayOf(
                "canChangeMediaItems" to available(Player.COMMAND_CHANGE_MEDIA_ITEMS),
                "canPlayPause" to available(Player.COMMAND_PLAY_PAUSE),
                "canSkip" to available(Player.COMMAND_SEEK_TO_NEXT)
            )
            when (cmd) {
                "play" -> c.play()
                "pause" -> c.pause()
                "next" -> c.seekToNext()
                "add" -> c.addMediaItem(MediaItem.Builder().setMediaId(intent.getStringExtra("mediaId").orEmpty()).build())
                "move" -> c.moveMediaItem(intent.getIntExtra("from", 0), intent.getIntExtra("to", 0))
                "remove" -> c.removeMediaItem(intent.getIntExtra("index", 0))
                "clear" -> c.clearMediaItems()
                "state" -> Unit
                else -> return@then report("error", "reason" to "unknown cmd")
            }
            // Give the session a moment to act on a command before the connection is released.
            main.postDelayed({ report("ok", *extras, "items" to c.mediaItemCount) }, SETTLE_MS)
        }
    }

    private fun runBrowse(component: ComponentName) {
        val future = MediaBrowser.Builder(this, SessionToken(this, component)).buildAsync()
        cleanups += { MediaBrowser.releaseFuture(future) }
        future.then {
            val browser = it.get()
            if (cmd == "browse-root") {
                browser.getLibraryRoot(null).then { result -> reportLibrary(result.get().resultCode, result.get().value?.mediaId) }
            } else {
                browser.getChildren(intent.getStringExtra("parent").orEmpty(), 0, Int.MAX_VALUE, null).then { result ->
                    val list = result.get()
                    reportLibrary(list.resultCode, list.value?.joinToString("|") { item -> item.mediaMetadata.title ?: item.mediaId })
                }
            }
        }
    }

    private fun reportLibrary(resultCode: Int, value: CharSequence?) {
        report(if (resultCode == LibraryResult.RESULT_SUCCESS) "ok" else "error", "code" to resultCode, "value" to value)
    }

    private fun runCompat(component: ComponentName) {
        lateinit var browser: MediaBrowserCompat
        browser = MediaBrowserCompat(
            this,
            component,
            object : MediaBrowserCompat.ConnectionCallback() {
                override fun onConnected() {
                    val controls = MediaControllerCompat(this@ControllerActivity, browser.sessionToken).transportControls
                    if (cmd == "compat-play-id") {
                        controls.playFromMediaId(intent.getStringExtra("mediaId").orEmpty(), null)
                    } else {
                        controls.playFromSearch(intent.getStringExtra("query").orEmpty(), null)
                    }
                    main.postDelayed({ report("ok") }, SETTLE_MS)
                }

                override fun onConnectionFailed() = report("error", "reason" to "connection failed")

                override fun onConnectionSuspended() = report("error", "reason" to "connection suspended")
            },
            null
        )
        cleanups += { browser.disconnect() }
        browser.connect()
    }

    private fun <T> ListenableFuture<T>.then(block: (ListenableFuture<T>) -> Unit) {
        addListener(
            {
                try {
                    block(this)
                } catch (e: Exception) {
                    report("error", "exception" to e)
                }
            },
            executor
        )
    }

    private fun report(result: String, vararg extras: Pair<String, Any?>) {
        if (done) return
        done = true
        val details = extras.joinToString("") { (key, value) -> " $key=$value" }
        Log.i(TAG, "cmd=$cmd result=$result$details")
        finish()
    }

    companion object {
        private const val TAG = "S2CTRL"
        private const val SHUTTLE_PACKAGE = "com.simplecityapps.shuttle.dev"
        private const val SERVICE_CLASS = "com.simplecityapps.playback.PlaybackService"
        private const val TIMEOUT_MS = 15_000L
        private const val SETTLE_MS = 1_000L
    }
}
