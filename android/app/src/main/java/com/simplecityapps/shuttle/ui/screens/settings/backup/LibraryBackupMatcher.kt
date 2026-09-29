package com.simplecityapps.shuttle.ui.screens.settings.backup

import com.simplecityapps.shuttle.model.Song
import kotlin.math.abs
import kotlin.time.Instant

/**
 * Pure library-matching and stats restore for backup restore: no Android, no Room, unit-testable.
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
     * Snapshot restore: the backup's values overwrite the on-device ones, so restoring a backup
     * taken at playCount=1 after playing to 2 sets it back to 1. The only exception is [MergedStats.dateAdded]:
     * a null backup (unknown at export) keeps the current value rather than wiping a known date.
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
        return MergedStats(
            playCount = backup.playCount,
            lastPlayed = backupLastPlayed,
            lastCompleted = backupLastCompleted,
            playbackPosition = backup.playbackPosition,
            excluded = backup.excluded,
            favouritedAt = backupFavourite,
            dateAdded = backupAdded ?: current.dateAdded
        )
    }

    fun statsEqual(current: Song, merged: MergedStats): Boolean =
        current.playCount == merged.playCount &&
            current.lastPlayed == merged.lastPlayed &&
            current.lastCompleted == merged.lastCompleted &&
            current.playbackPosition == merged.playbackPosition &&
            current.dateAdded == merged.dateAdded &&
            current.blacklisted == merged.excluded &&
            current.favouritedAt == merged.favouritedAt

    /** Strips a `/storage/<volume>/` prefix so volume id changes don't break matching. Handles both
     * physical volumes (`/storage/ABCD-1234/…`) and emulated storage (`/storage/emulated/0/…`). */
    internal fun relativePath(path: String): String {
        val lower = path.lowercase()
        val storagePrefix = Regex("^/storage/(emulated/\\d+|[^/]+)/")
        return storagePrefix.replace(lower, "")
    }

    private fun fingerprintKey(title: String?, album: String?, artist: String?): String =
        listOf(title, album, artist).joinToString("\u0001") { it?.trim()?.lowercase() ?: "" }

    private const val DURATION_TOLERANCE_S = 2
}
