package com.simplecityapps.localmediaprovider.local.provider

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Process
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
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
 * become suspects, each read alone next so a crash points at one. Where Android can't say (before 11, or 11+ with no
 * record of that process), a file read alone when the app died takes a strike, and a second strike quarantines it. The
 * quarantine is kept in [preferences] until Sources' retry clears it; [read] leaves those files unread.
 *
 * A marker says only that the process died during a read, not that the read killed it, so two cases quarantine a file
 * that reads fine, accepted because Sources says how many files were left out and its retry reads them again:
 * - a native crash elsewhere in the app (a decoder, another library) while that file's read ran alone;
 * - before 11, two ordinary deaths (swiped away, killed for memory) each while that file's read ran alone, which is
 *   likeliest for a file over [LARGE_TAG_READ_BYTES], always read alone and slowest to read.
 */
class TagReadGuard(
    private val markerDir: File,
    private val preferences: GeneralPreferenceManager,
    // How Android recorded this app's processes ending; null where it keeps no record (before 11)
    private val processExits: () -> List<ProcessExit>?,
    private val limiter: TagReadLimiter = TagReadLimiter(),
    private val pid: Int = Process.myPid()
) {
    constructor(context: Context, preferences: GeneralPreferenceManager) : this(
        markerDir = File(context.filesDir, "tag-reads"),
        preferences = preferences,
        processExits = { processExits(context) }
    )

    // A read holds at least one permit, so there's always a free slot for it
    private val freeSlots = ArrayDeque((0 until limiter.permits).toList())

    @Volatile
    private var quarantine: Set<String>? = null

    @Volatile
    private var strikes: Set<String>? = null

    // Files a crash came during alongside others, read alone until one reads without crashing
    private val suspects: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // Each source's own, so one provider's recovery doesn't clear what another's import left unread
    private val skipped = ConcurrentHashMap<MediaProviderType, MutableSet<String>>()

    // Logs the first marker that couldn't be written, not one per read
    private val markerWriteFailed = AtomicBoolean()

    /** The paths [read] left unread for [source] since its last [recover], being quarantined. */
    fun skippedPaths(source: MediaProviderType): Set<String> = skipped[source].orEmpty().toSet()

    /**
     * Takes the markers a process that died left, and quarantines or suspects the files they name; called at the start of
     * each of [source]'s imports, so the quarantine Sources' retry cleared is read again too.
     */
    suspend fun recover(source: MediaProviderType) = withContext(Dispatchers.IO) {
        skipped.remove(source)
        var quarantine = preferences.tagReadQuarantine()
        var strikes = preferences.tagReadStrikes()
        val suspects = mutableSetOf<String>()
        val now = System.currentTimeMillis()
        val markers =
            markerDir.listFiles { file -> file.name.startsWith(SLOT_PREFIX) }.orEmpty().mapNotNull { file ->
                // Whose it is comes from its name, so a read of this process's still writing its marker is never taken
                val owner = MARKER_NAME.matchEntire(file.name)?.groupValues?.get(1)?.toIntOrNull()
                if (owner == pid) return@mapNotNull null
                val key =
                    try {
                        file.readText().takeIf { it.isNotEmpty() }
                    } catch (e: IOException) {
                        null
                    }
                if (owner == null || key == null) {
                    // Unreadable: a fresh one may be a marker another process is writing now, an old one names nothing
                    if (now - file.lastModified() > FRESH_MARKER_MILLIS) file.delete()
                    return@mapNotNull null
                }
                Marker(owner, key, file.lastModified()).also { file.delete() }
            }
        // Asked once, and only when a marker was left
        val exits by lazy { processExits() }
        markers.groupBy { it.owner }.forEach { (owner, ownMarkers) ->
            val keys = ownMarkers.map { it.key }
            when (crashedNatively(exits, owner, since = ownMarkers.maxOf { it.writtenAt })) {
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
            Timber.w("Tag reads in flight when the app died: ${markers.map { it.key }}; quarantined ${quarantine.size}, suspects ${suspects.size}, strikes ${strikes.size}")
            quarantine.minus(preferences.tagReadQuarantine()).forEach(preferences::quarantineTagRead)
            preferences.setTagReadStrikes(strikes)
        }
        this@TagReadGuard.quarantine = quarantine
        this@TagReadGuard.strikes = strikes
        this@TagReadGuard.suspects += suspects
    }

    /**
     * [read]'s result for [file], or null without reading it if it's quarantined, which counts among [source]'s
     * [skippedPaths]. A large file, a suspect or one with a strike is read alone.
     */
    suspend fun <T : Any> read(
        file: TagReadFile,
        source: MediaProviderType,
        read: suspend () -> T?
    ): T? {
        val key = file.key
        if (key in quarantine()) {
            Timber.w("Not reading quarantined file ${file.path}")
            skipped.getOrPut(source) { ConcurrentHashMap.newKeySet() } += file.path
            return null
        }
        val struck = key in strikes()
        return limiter.withPermits(all = file.size > LARGE_TAG_READ_BYTES || struck || key in suspects) {
            val slot = synchronized(freeSlots) { freeSlots.removeFirst() }
            val marker = File(markerDir, "$SLOT_PREFIX$pid-$slot")
            try {
                writeMarker(marker, key)
                read().also {
                    suspects -= key
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

    private suspend fun writeMarker(
        marker: File,
        text: String
    ) = withContext(Dispatchers.IO) {
        try {
            markerDir.mkdirs()
            marker.writeText(text)
        } catch (e: IOException) {
            // Disk full or the folder gone: the read goes ahead unguarded, rather than one marker failing the import
            if (!markerWriteFailed.getAndSet(true)) Timber.e(e, "Couldn't write a tag read marker; reading without one")
        }
    }

    private fun quarantine(): Set<String> = quarantine ?: preferences.tagReadQuarantine().also { quarantine = it }

    private fun strikes(): Set<String> = strikes ?: preferences.tagReadStrikes().also { strikes = it }

    private class Marker(
        val owner: Int,
        val key: String,
        val writtenAt: Long
    )

    private companion object {
        const val SLOT_PREFIX = "slot-"

        // slot-<pid>-<slot>: the process whose read it marks, and which of its slots
        val MARKER_NAME = Regex("$SLOT_PREFIX(\\d+)-\\d+")

        // How long an unreadable marker of another process is left, in case it's being written now
        const val FRESH_MARKER_MILLIS = 60_000L
    }
}

/** How Android recorded one of this app's processes ending: its [pid], when, and whether a native crash ended it. */
data class ProcessExit(
    val pid: Int,
    val timestamp: Long,
    val nativeCrash: Boolean
)

/**
 * Whether the process [pid], alive when it wrote a marker at [since], ended in a native crash, from [exits]; null if
 * Android can't say. Below 11 there's no record ([exits] is null). On 11+ the process's end is the first record for its
 * pid from [since] on, as an earlier one is a process that had the pid before; with none (Android keeps a bounded
 * history and doesn't record every death) it's unknown too, so a file read alone then takes a strike rather than being
 * quarantined at once.
 */
internal fun crashedNatively(
    exits: List<ProcessExit>?,
    pid: Int,
    since: Long
): Boolean? = exits?.filter { exit -> exit.pid == pid && exit.timestamp >= since }?.minByOrNull { it.timestamp }?.nativeCrash

/** How Android recorded this app's processes ending (11+), or null where it can't say. */
private fun processExits(context: Context): List<ProcessExit>? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    val exits = context.getSystemService(ActivityManager::class.java)?.getHistoricalProcessExitReasons(null, 0, 0) ?: return null
    return exits.map { exit -> ProcessExit(exit.pid, exit.timestamp, nativeCrash = exit.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) }
}
