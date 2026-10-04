package com.simplecityapps.localmediaprovider.local.provider

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Process
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import java.io.File
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/** A file a native tag read opens, as the version it is now: a file replaced or edited since is another [key]. */
data class TagReadFile(
    val path: String,
    val size: Long,
    val lastModified: Long
) {
    val key: String get() = "$path|$size|$lastModified"
}

/**
 * Keeps a file that crashes TagLib from crashing every import after it (#840). A native fault can't be caught, so each
 * read leaves a marker file in a slot of [markerDir] while it runs, written before and deleted after; a marker left
 * behind names a read the process died during. [recover] takes them at the start of the next import: if Android says
 * that process crashed natively ([crashedNatively]), a file read alone is quarantined, and files read alongside others
 * become suspects, each read alone next so a crash points at one. Where Android can't say (before 11), a file read alone
 * when the app died takes a strike, and a second strike quarantines it. The quarantine is kept in [preferences] until
 * Sources' retry clears it; [read] leaves those files unread.
 */
class TagReadGuard(
    private val markerDir: File,
    private val preferences: GeneralPreferenceManager,
    // Whether the process with this id ended in a native crash; null if Android can't say
    private val crashedNatively: (pid: Int) -> Boolean?,
    private val limiter: TagReadLimiter = TagReadLimiter(),
    private val pid: Int = Process.myPid()
) {
    constructor(context: Context, preferences: GeneralPreferenceManager) : this(
        markerDir = File(context.filesDir, "tag-reads"),
        preferences = preferences,
        crashedNatively = { pid -> nativeCrash(context, pid) }
    )

    // A read holds at least one permit, so there's always a free slot for it
    private val freeSlots = ArrayDeque((0 until limiter.permits).toList())

    @Volatile
    private var quarantine: Set<String>? = null

    @Volatile
    private var strikes: Set<String>? = null

    @Volatile
    private var suspects: Set<String> = emptySet()

    private val skipped: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    /** The paths [read] left unread since the last [recover], being quarantined. */
    val skippedPaths: Set<String> get() = synchronized(skipped) { skipped.toSet() }

    /**
     * Takes the markers a process that died left, and quarantines or suspects the files they name; called at the start of
     * each import, so the quarantine Sources' retry cleared is read again too.
     */
    suspend fun recover() = withContext(Dispatchers.IO) {
        skipped.clear()
        var quarantine = preferences.tagReadQuarantine()
        var strikes = preferences.tagReadStrikes()
        val suspects = mutableSetOf<String>()
        val markers =
            markerDir.listFiles { file -> file.name.startsWith(SLOT_PREFIX) }.orEmpty().mapNotNull { file ->
                val (owner, key) = file.readText().split('\n', limit = 2).takeIf { it.size == 2 }.let { it?.get(0)?.toIntOrNull() to it?.get(1) }
                // This process's own are reads running now
                if (owner == pid) return@mapNotNull null
                file.delete()
                if (owner == null || key == null) null else owner to key
            }
        markers.groupBy({ it.first }, { it.second }).forEach { (owner, keys) ->
            when (crashedNatively(owner)) {
                // Killed some other way (swiped away, out of memory): the read didn't crash it
                false -> return@forEach

                true -> if (keys.size == 1) quarantine += keys else suspects += keys

                null ->
                    when {
                        keys.size > 1 -> suspects += keys

                        keys.single() in strikes -> {
                            quarantine += keys
                            strikes -= keys
                        }

                        else -> strikes += keys
                    }
            }
        }
        if (markers.isNotEmpty()) {
            Timber.w("Tag reads in flight when the app died: ${markers.map { it.second }}; quarantined ${quarantine.size}, suspects ${suspects.size}, strikes ${strikes.size}")
            quarantine.minus(preferences.tagReadQuarantine()).forEach(preferences::quarantineTagRead)
            preferences.setTagReadStrikes(strikes)
        }
        this@TagReadGuard.quarantine = quarantine
        this@TagReadGuard.strikes = strikes
        this@TagReadGuard.suspects = suspects
    }

    /**
     * [read]'s result for [file], or null without reading it if it's quarantined. A large file, a suspect or one with a
     * strike is read alone.
     */
    suspend fun <T : Any> read(
        file: TagReadFile,
        read: suspend () -> T?
    ): T? {
        val key = file.key
        if (key in quarantine()) {
            Timber.w("Not reading quarantined file ${file.path}")
            skipped += file.path
            return null
        }
        val struck = key in strikes()
        return limiter.withPermits(all = file.size > LARGE_TAG_READ_BYTES || struck || key in suspects) {
            val slot = synchronized(freeSlots) { freeSlots.removeFirst() }
            val marker = File(markerDir, "$SLOT_PREFIX$slot")
            try {
                withContext(Dispatchers.IO) {
                    markerDir.mkdirs()
                    marker.writeText("$pid\n$key")
                }
                read().also {
                    if (struck) {
                        strikes = strikes() - key
                        preferences.setTagReadStrikes(strikes())
                    }
                }
            } finally {
                marker.delete()
                synchronized(freeSlots) { freeSlots.addLast(slot) }
            }
        }
    }

    private fun quarantine(): Set<String> = quarantine ?: preferences.tagReadQuarantine().also { quarantine = it }

    private fun strikes(): Set<String> = strikes ?: preferences.tagReadStrikes().also { strikes = it }

    private companion object {
        const val SLOT_PREFIX = "slot-"
    }
}

/** Whether this app's process [pid] ended in a native crash, from Android's record of how its processes exited (11+). */
private fun nativeCrash(
    context: Context,
    pid: Int
): Boolean? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    val exits = context.getSystemService(ActivityManager::class.java)?.getHistoricalProcessExitReasons(null, 0, 0) ?: return null
    return exits.firstOrNull { exit -> exit.pid == pid }?.let { exit -> exit.reason == ApplicationExitInfo.REASON_CRASH_NATIVE }
}
