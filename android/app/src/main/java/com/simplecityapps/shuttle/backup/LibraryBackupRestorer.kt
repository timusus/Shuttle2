package com.simplecityapps.shuttle.backup

import com.simplecityapps.localmediaprovider.local.data.room.dao.SongStatsRestore
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import com.simplecityapps.shuttle.ui.screens.settings.backup.LibraryBackup
import com.simplecityapps.shuttle.ui.screens.settings.backup.LibraryBackupMatcher
import com.simplecityapps.shuttle.ui.screens.settings.backup.RestoreReport
import kotlinx.coroutines.flow.first

/**
 * Merges a [LibraryBackup] into the library: matched songs get their stats merged (see
 * [LibraryBackupMatcher.mergeStats]), and playlists are created or extended with the backup members they
 * lack. Nothing the device already has is removed, so restoring is repeatable.
 *
 * [writeStats] receives every song whose merged stats differ from the device, to write in one transaction. The
 * stats are merged again in SQL against the row as it is by then. A new playlist takes the backup's sort order;
 * an existing one keeps its own.
 */
class LibraryBackupRestorer(
    private val playlistRepository: PlaylistRepository,
    private val writeStats: suspend (List<SongStatsRestore>) -> Unit
) {
    suspend fun restore(
        backup: LibraryBackup,
        library: List<Song>
    ): RestoreReport {
        val matches = LibraryBackupMatcher.matchAll(backup.songs.map { it.identity }, library)

        var unmatched = 0
        val merged = LinkedHashMap<Long, Pair<Song, LibraryBackupMatcher.MergedStats>>()
        backup.songs.forEach { backedUp ->
            val current = matches[backedUp.identity]?.song
            if (current == null) {
                unmatched++
                return@forEach
            }
            // Entries resolving to one song fold into a single merge, on top of what the earlier ones brought.
            val base = merged[current.id]?.second?.let { current.withStats(it) } ?: current
            merged[current.id] = current to LibraryBackupMatcher.mergeStats(base, backedUp)
        }
        val restores = merged.values.filterNot { (current, stats) -> LibraryBackupMatcher.statsEqual(current, stats) }.map { (current, stats) ->
            SongStatsRestore(
                song = current,
                playCount = stats.playCount,
                lastPlayed = stats.lastPlayed,
                lastCompleted = stats.lastCompleted,
                playbackPosition = stats.playbackPosition,
                dateAdded = stats.dateAdded,
                excluded = stats.excluded,
                favouritedAt = stats.favouritedAt
            )
        }
        if (restores.isNotEmpty()) writeStats(restores)

        var playlistsRestored = 0
        var membersSkipped = 0
        val unresolved = mutableListOf<String>()
        backup.playlists.forEach { backedUp ->
            val provider = runCatching { MediaProviderType.valueOf(backedUp.provider) }.getOrNull()
                ?: MediaProviderType.Shuttle
            val memberSongs = backedUp.members.mapNotNull { ref ->
                matches[ref]?.song ?: run {
                    membersSkipped++
                    null
                }
            }
            if (memberSongs.isEmpty()) {
                unresolved.add(backedUp.name)
                return@forEach
            }
            // Read afresh each time: an earlier backup playlist may have created this one.
            val known = playlistRepository.getPlaylists(PlaylistQuery.All(null)).first()
            val target = known.firstOrNull { backedUp.externalId != null && it.mediaProvider == provider && it.externalId == backedUp.externalId }
                ?: known.firstOrNull { it.mediaProvider == provider && it.name.equals(backedUp.name, ignoreCase = true) }
            if (target == null) {
                val created = playlistRepository.createPlaylist(backedUp.name, provider, LibraryBackupMatcher.missingMembers(emptySet(), memberSongs), backedUp.externalId)
                runCatching { PlaylistSongSortOrder.valueOf(backedUp.sortOrder) }.getOrNull()?.let { sortOrder ->
                    playlistRepository.updatePlaylistSortOder(created, sortOrder, backedUp.sortDescending)
                }
                playlistsRestored++
            } else {
                val missing = LibraryBackupMatcher.missingMembers(playlistRepository.getMemberSongIds(target), memberSongs)
                if (missing.isNotEmpty()) {
                    playlistRepository.addToPlaylist(target, missing)
                    playlistsRestored++
                }
            }
        }

        return RestoreReport(
            songsMatched = backup.songs.size - unmatched,
            songsUnmatched = unmatched,
            statsWritten = restores.size,
            playlistsRestored = playlistsRestored,
            playlistsUnresolved = unresolved,
            membersSkipped = membersSkipped
        )
    }

    private fun Song.withStats(stats: LibraryBackupMatcher.MergedStats) = copy(
        playCount = stats.playCount,
        lastPlayed = stats.lastPlayed,
        lastCompleted = stats.lastCompleted,
        playbackPosition = stats.playbackPosition,
        dateAdded = stats.dateAdded,
        blacklisted = stats.excluded,
        favouritedAt = stats.favouritedAt
    )
}
