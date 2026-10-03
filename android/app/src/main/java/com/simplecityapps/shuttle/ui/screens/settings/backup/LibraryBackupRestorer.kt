package com.simplecityapps.shuttle.ui.screens.settings.backup

import com.simplecityapps.localmediaprovider.local.data.room.dao.SongStatsRestore
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import kotlinx.coroutines.flow.first

/**
 * Merges a [LibraryBackup] into the library: matched songs get their stats merged (see
 * [LibraryBackupMatcher.mergeStats]), and playlists are created or extended with the backup members they
 * lack. Nothing the device already has is removed, so restoring is repeatable.
 *
 * [writeStats] receives every song whose merged stats differ from the device, to write in one transaction.
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
        val restores = mutableListOf<SongStatsRestore>()
        backup.songs.forEach { backedUp ->
            val current = matches[backedUp.identity]?.song
            if (current == null) {
                unmatched++
                return@forEach
            }
            val merged = LibraryBackupMatcher.mergeStats(current, backedUp)
            if (!LibraryBackupMatcher.statsEqual(current, merged)) {
                restores += SongStatsRestore(
                    song = current,
                    playCount = merged.playCount,
                    lastPlayed = merged.lastPlayed,
                    lastCompleted = merged.lastCompleted,
                    playbackPosition = merged.playbackPosition,
                    dateAdded = merged.dateAdded,
                    excluded = merged.excluded,
                    favouritedAt = merged.favouritedAt
                )
            }
        }
        if (restores.isNotEmpty()) writeStats(restores)

        val known = playlistRepository.getPlaylists(PlaylistQuery.All(null)).first().toMutableList()
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
            val sortOrder = runCatching { PlaylistSongSortOrder.valueOf(backedUp.sortOrder) }.getOrNull()
            val target = known.firstOrNull { backedUp.externalId != null && it.mediaProvider == provider && it.externalId == backedUp.externalId }
                ?: known.firstOrNull { it.mediaProvider == provider && it.name.equals(backedUp.name, ignoreCase = true) }
            if (target == null) {
                val created = playlistRepository.createPlaylist(backedUp.name, provider, memberSongs, backedUp.externalId)
                known += created
                sortOrder?.let { playlistRepository.updatePlaylistSortOder(created, it, backedUp.sortDescending) }
            } else {
                val present = playlistRepository.getSongsForPlaylist(target).first().map { it.song }
                val missing = LibraryBackupMatcher.missingMembers(present, memberSongs)
                if (missing.isNotEmpty()) playlistRepository.addToPlaylist(target, missing)
                sortOrder?.let { playlistRepository.updatePlaylistSortOder(target, it, backedUp.sortDescending) }
            }
            playlistsRestored++
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
}
