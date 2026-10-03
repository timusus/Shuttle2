package com.simplecityapps.shuttle.ui.screens.settings.backup

import com.simplecityapps.shuttle.model.Song
import kotlin.math.abs
import kotlin.time.Instant

/**
 * Pure library-matching and stats merge for backup restore: no Android, no Room, unit-testable.
 *
 * Match order per backup identity: exact (provider, path) -> normalized relative path (the SD-card
 * volume segment changes across installs) -> (provider, externalId) for remote items -> tag
 * fingerprint (normalized title/album/artist within one provider, duration within
 * [DURATION_TOLERANCE_MS], ties broken by file size). Each tier runs over every identity before the
 * next starts, and a library song is claimed by at most one backup entry.
 */
object LibraryBackupMatcher {

    enum class MatchKind { ExactPath, RelativePath, ExternalId, Fuzzy }

    data class SongMatch(val song: Song, val kind: MatchKind)

    fun matchAll(
        identities: List<SongIdentity>,
        library: List<Song>
    ): Map<SongIdentity, SongMatch?> {
        val distinct = identities.distinct()
        val exact = library.groupBy { it.mediaProvider.name to it.path }
        val relative = library.groupBy { it.mediaProvider.name to relativePath(it.path) }
        val byExternalId = library.filter { it.externalId != null }.groupBy { it.mediaProvider.name to it.externalId!! }
        val fuzzy = library.groupBy { fingerprintKey(it.mediaProvider.name, it.name, fingerprintArtist(it), it.album) }

        val claimed = HashSet<Long>()
        val matches = HashMap<SongIdentity, SongMatch>()

        fun pass(
            kind: MatchKind,
            candidates: (SongIdentity) -> List<Song>
        ) {
            for (identity in distinct) {
                if (identity in matches) continue
                val song = candidates(identity).firstOrNull { it.id !in claimed } ?: continue
                claimed += song.id
                matches[identity] = SongMatch(song, kind)
            }
        }

        pass(MatchKind.ExactPath) { exact[it.provider to it.path].orEmpty() }
        pass(MatchKind.RelativePath) { relative[it.provider to relativePath(it.path)].orEmpty() }
        pass(MatchKind.ExternalId) { identity -> identity.externalId?.let { byExternalId[identity.provider to it] }.orEmpty() }
        pass(MatchKind.Fuzzy) { identity ->
            if (identity.title.isNullOrBlank() && identity.album.isNullOrBlank() && identity.artist.isNullOrBlank()) {
                emptyList()
            } else {
                fuzzy[fingerprintKey(identity.provider, identity.title, identity.artist, identity.album)].orEmpty()
                    .filter { abs(it.duration - identity.duration) <= DURATION_TOLERANCE_MS }
                    .sortedWith(
                        compareByDescending<Song> { identity.size > 0 && it.size == identity.size }
                            .thenBy { abs(it.duration - identity.duration) }
                    )
            }
        }
        return identities.associateWith { matches[it] }
    }

    /** The artist the backup fingerprint carries: albumArtist ?: the joined artists. Used on both the export and library sides. */
    fun fingerprintArtist(song: Song): String? = song.albumArtist ?: song.artists.joinToString(", ").ifEmpty { null }

    /**
     * Merge restore: a backup never takes anything away from the device. Counts, last-played and
     * last-completed times keep the larger, favourite and excluded stay on if on either side, the date
     * added is the earlier, and the playback position follows whichever side was played last.
     * Negative counts, positions and times in the backup are treated as absent.
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
        val backupLastPlayed = backup.lastPlayed.toInstant()
        val backupLastCompleted = backup.lastCompleted.toInstant()
        val backupAdded = backup.dateAdded.toInstant()
        val backupPlayedIsNewer = backupLastPlayed != null && (current.lastPlayed == null || backupLastPlayed > current.lastPlayed!!)
        return MergedStats(
            playCount = maxOf(current.playCount, backup.playCount.coerceAtLeast(0)),
            lastPlayed = laterOf(current.lastPlayed, backupLastPlayed),
            lastCompleted = laterOf(current.lastCompleted, backupLastCompleted),
            playbackPosition = if (backupPlayedIsNewer) backup.playbackPosition.coerceAtLeast(0) else current.playbackPosition,
            excluded = current.blacklisted || backup.excluded,
            favouritedAt = current.favouritedAt ?: backup.favouritedAt.toInstant(),
            dateAdded = listOfNotNull(current.dateAdded, backupAdded).minOrNull()
        )
    }

    fun statsEqual(current: Song, merged: MergedStats): Boolean = current.playCount == merged.playCount &&
        current.lastPlayed == merged.lastPlayed &&
        current.lastCompleted == merged.lastCompleted &&
        current.playbackPosition == merged.playbackPosition &&
        current.dateAdded == merged.dateAdded &&
        current.blacklisted == merged.excluded &&
        current.favouritedAt == merged.favouritedAt

    /** The songs of [backup] whose ids aren't in [existingIds], in backup order, each once. */
    fun missingMembers(
        existingIds: Set<Long>,
        backup: List<Song>
    ): List<Song> {
        val present = existingIds.toMutableSet()
        return backup.filter { present.add(it.id) }
    }

    private fun Long?.toInstant(): Instant? = this?.takeIf { it >= 0 }?.let(Instant::fromEpochMilliseconds)

    private fun laterOf(
        a: Instant?,
        b: Instant?
    ): Instant? = if (a == null) {
        b
    } else if (b == null) {
        a
    } else {
        maxOf(a, b)
    }

    /** Strips a `/storage/<volume>/` prefix so volume id changes don't break matching. Handles both
     * physical volumes (`/storage/ABCD-1234/…`) and emulated storage (`/storage/emulated/0/…`). */
    internal fun relativePath(path: String): String = storagePrefix.replace(path.lowercase(), "")

    private val storagePrefix = Regex("^/storage/(emulated/\\d+|[^/]+)/")

    private fun fingerprintKey(
        provider: String,
        title: String?,
        artist: String?,
        album: String?
    ): String = listOf(provider, title, album, artist).joinToString("\u0001") { it?.trim()?.lowercase() ?: "" }

    private const val DURATION_TOLERANCE_MS = 2000
}
