package com.simplecityapps.shuttle.ui.screens.settings.backup

import com.simplecityapps.shuttle.model.Song
import kotlin.math.abs
import kotlin.time.Instant

/**
 * Pure library-matching and stats-merging for backup restore: no Android, no Room, unit-testable.
 *
 * Match order per backup identity: exact (provider, path) -> normalized relative path (the SD-card
 * volume segment changes across installs) -> (provider, externalId) for remote items -> tag
 * fingerprint (normalized title/album/artist + duration within [DURATION_TOLERANCE_S]).
 */
object LibraryBackupMatcher {

    enum class MatchKind { ExactPath, RelativePath, ExternalId, Fuzzy }

    data class SongMatch(val song: Song, val kind: MatchKind)

    fun matchAll(
        identities: List<SongIdentity>,
        library: List<Song>
    ): Map<SongIdentity, SongMatch?> {
        val exact = library.associateBy { it.mediaProvider.name to it.path }
        val relative = HashMap<Pair<String, String>, Song>()
        val byExternalId = HashMap<Pair<String, String>, Song>()
        val fuzzy = HashMap<String, MutableList<Song>>()
        for (song in library) {
            relative.putIfAbsent(song.mediaProvider.name to relativePath(song.path), song)
            song.externalId?.let { byExternalId.putIfAbsent(song.mediaProvider.name to it, song) }
            fuzzy.getOrPut(fingerprintKey(song.name, song.album, song.friendlyArtistName)) { mutableListOf() }.add(song)
        }
        return identities.associateWith { identity ->
            exact[identity.provider to identity.path]?.let { return@associateWith SongMatch(it, MatchKind.ExactPath) }
            relative[identity.provider to relativePath(identity.path)]?.let { return@associateWith SongMatch(it, MatchKind.RelativePath) }
            identity.externalId?.let { byExternalId[identity.provider to it] }?.let { return@associateWith SongMatch(it, MatchKind.ExternalId) }
            fuzzy[fingerprintKey(identity.title, identity.album, identity.artist)]
                ?.firstOrNull { abs(it.duration - identity.duration) <= DURATION_TOLERANCE_S }
                ?.let { SongMatch(it, MatchKind.Fuzzy) }
        }
    }

    /**
     * Field-level merge: restore must never destroy newer on-device activity, and a reinstall must
     * regain lifetime counts. Counters take the max, timestamps the latest, position follows
     * whichever side was played last, exclusions OR together, favourites/date-added keep the
     * earliest (original) time.
     */
    data class MergedStats(
        val playCount: Int,
        val lastPlayed: Instant?,
        val lastCompleted: Instant?,
        val playbackPosition: Int,
        val excluded: Boolean,
        val favouritedAt: Instant?,
        val dateAdded: Instant?
    )

    fun mergeStats(current: Song, backup: BackedUpSong): MergedStats {
        val backupLastPlayed = backup.lastPlayed?.let(Instant::fromEpochMilliseconds)
        val backupLastCompleted = backup.lastCompleted?.let(Instant::fromEpochMilliseconds)
        val backupFavourite = backup.favouritedAt?.let(Instant::fromEpochMilliseconds)
        val backupAdded = backup.dateAdded?.let(Instant::fromEpochMilliseconds)
        val latestPlayed = latestOf(current.lastPlayed, backupLastPlayed)
        val position = when {
            backupLastPlayed != null && backupLastPlayed >= (current.lastPlayed ?: Instant.DISTANT_PAST) -> backup.playbackPosition
            else -> current.playbackPosition
        }
        return MergedStats(
            playCount = maxOf(current.playCount, backup.playCount),
            lastPlayed = latestPlayed,
            lastCompleted = latestOf(current.lastCompleted, backupLastCompleted),
            playbackPosition = position,
            excluded = current.blacklisted || backup.excluded,
            favouritedAt = earliestOf(current.favouritedAt, backupFavourite),
            dateAdded = earliestOf(current.dateAdded, backupAdded) ?: current.dateAdded
        )
    }

    fun statsEqual(current: Song, merged: MergedStats): Boolean =
        current.playCount == merged.playCount &&
            current.lastPlayed == merged.lastPlayed &&
            current.lastCompleted == merged.lastCompleted &&
            current.playbackPosition == merged.playbackPosition &&
            current.dateAdded == merged.dateAdded

    /** Strips a `/storage/<volume>/` prefix so volume id changes don't break matching. Handles both
     * physical volumes (`/storage/ABCD-1234/…`) and emulated storage (`/storage/emulated/0/…`). */
    internal fun relativePath(path: String): String {
        val lower = path.lowercase()
        val storagePrefix = Regex("^/storage/(emulated/\\d+|[^/]+)/")
        return storagePrefix.replace(lower, "")
    }

    private fun fingerprintKey(title: String?, album: String?, artist: String?): String =
        listOf(title, album, artist).joinToString("\u0001") { it?.trim()?.lowercase() ?: "" }

    // Named latest/earliest (not max/min) so overload resolution can never mistake these
    // for kotlin.comparisons.maxOf/minOf and recurse.
    private fun latestOf(a: Instant?, b: Instant?): Instant? =
        if (a == null) b else if (b == null) a else if (a >= b) a else b

    private fun earliestOf(a: Instant?, b: Instant?): Instant? =
        if (a == null) b else if (b == null) a else if (a <= b) a else b

    private const val DURATION_TOLERANCE_S = 2
}
