package com.simplecityapps.shuttle.ui.screens.settings.backup

import android.content.Context
import android.net.Uri
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.di.ApplicationContext
import com.simplecityapps.shuttle.di.IoDispatcher
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import dev.zacsweers.metro.Inject
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import kotlin.time.Clock

data class RestoreReport(
    val songsMatched: Int,
    val songsUnmatched: Int,
    val statsWritten: Int,
    val playlistsRestored: Int,
    val playlistsUnresolved: List<String>,
    val membersSkipped: Int
)

/**
 * Library backup export/import (Settings -> Library). Backs up per-song stats (play counts,
 * positions, favourites, exclusions, dates) plus playlists; restore overwrites each matched song
 * with the backup's snapshot.
 *
 * Never backed up: Room ids, credentials/tokens, SAF grant URIs, transient queue/session state,
 * artwork caches and aggregates. Restoring favourites writes the DAO directly (no
 * `pending_favourites` rows), so a restore never pushes stale states to remote servers.
 */
class LibraryBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MediaDatabase,
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    suspend fun buildBackupJson(): String = withContext(ioDispatcher) {
        val songs = songRepository.loadSongs(SongQuery.All(includeExcluded = true))
        val playlists = playlistRepository.getPlaylists(PlaylistQuery.All(null)).first()
        val backedPlaylists = playlists.map { playlist ->
            val members = playlistRepository.getSongsForPlaylist(playlist).first()
            BackedUpPlaylist(
                name = playlist.name,
                provider = playlist.mediaProvider.name,
                externalId = playlist.externalId,
                sortOrder = playlist.sortOrder.name,
                sortDescending = playlist.sortDescending,
                members = members.map { it.song.toIdentity() }
            )
        }
        json.encodeToString(
            LibraryBackup.serializer(),
            LibraryBackup(
                exportedAt = Clock.System.now().toEpochMilliseconds(),
                songs = songs.map { it.toBackedUp() },
                playlists = backedPlaylists
            )
        )
    }

    suspend fun writeBackup(destination: String, backupJson: String): Boolean = withContext(ioDispatcher) {
        try {
            context.contentResolver.openOutputStream(Uri.parse(destination))?.use { out ->
                out.write(backupJson.toByteArray(Charsets.UTF_8))
                true
            } ?: run {
                Timber.w("Could not open output stream for library backup: $destination")
                false
            }
        } catch (e: IOException) {
            Timber.e(e, "Failed to write library backup to $destination")
            false
        } catch (e: SecurityException) {
            Timber.e(e, "Permission denied writing library backup to $destination")
            false
        }
    }

    suspend fun readAndRestore(source: String): RestoreReport? = withContext(ioDispatcher) {
        val backup = try {
            context.contentResolver.openInputStream(Uri.parse(source))?.use { input ->
                json.decodeFromString<LibraryBackup>(input.readBytes().toString(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to read library backup from $source")
            null
        } ?: return@withContext null
        if (backup.schemaVersion > LibraryBackup.CURRENT_SCHEMA_VERSION) {
            Timber.w("Backup schema ${backup.schemaVersion} newer than supported ${LibraryBackup.CURRENT_SCHEMA_VERSION}")
            return@withContext null
        }
        restore(backup)
    }

    private suspend fun restore(backup: LibraryBackup): RestoreReport {
        val songDataDao = database.songDataDao()
        val library = songRepository.loadSongs(SongQuery.All(includeExcluded = true))
        val matches = LibraryBackupMatcher.matchAll(backup.songs.map { it.identity }, library)

        var statsWritten = 0
        var unmatched = 0
        backup.songs.forEach { backedUp ->
            val match = matches[backedUp.identity]
            if (match == null) {
                unmatched++
                return@forEach
            }
            val current = match.song
            val merged = LibraryBackupMatcher.mergeStats(current, backedUp)
            if (!LibraryBackupMatcher.statsEqual(current, merged)) {
                songDataDao.restoreStats(
                    id = current.id,
                    playCount = merged.playCount,
                    lastPlayed = merged.lastPlayed,
                    lastCompleted = merged.lastCompleted,
                    playbackPosition = merged.playbackPosition,
                    dateAdded = merged.dateAdded
                )
                if (merged.excluded != current.blacklisted) {
                    songDataDao.setExcluded(listOf(current.id), merged.excluded)
                }
                if (current.favouritedAt != merged.favouritedAt) {
                    songDataDao.setFavouritedAt(current.id, merged.favouritedAt)
                }
                statsWritten++
            }
        }

        val existing = playlistRepository.getPlaylists(PlaylistQuery.All(null)).first()
        var playlistsRestored = 0
        var membersSkipped = 0
        val unresolved = mutableListOf<String>()
        backup.playlists.forEach { backedUp ->
            val provider = runCatching { MediaProviderType.valueOf(backedUp.provider) }.getOrNull()
                ?: MediaProviderType.Shuttle
            val memberSongs = backedUp.members.mapNotNull { ref ->
                matches[ref]?.song ?: run { membersSkipped++; null }
            }
            if (memberSongs.isEmpty()) {
                unresolved.add(backedUp.name)
                return@forEach
            }
            val target = if (backedUp.externalId != null) {
                existing.firstOrNull { it.mediaProvider == provider && it.externalId == backedUp.externalId }
            } else null
                ?: existing.firstOrNull { it.mediaProvider == provider && it.name.equals(backedUp.name, ignoreCase = true) }
            if (target == null) {
                playlistRepository.createPlaylist(backedUp.name, provider, memberSongs, backedUp.externalId)
            } else {
                playlistRepository.clearPlaylist(target)
                playlistRepository.addToPlaylist(target, memberSongs)
                runCatching { PlaylistSongSortOrder.valueOf(backedUp.sortOrder) }.getOrNull()?.let { order ->
                    playlistRepository.updatePlaylistSortOder(target, order, backedUp.sortDescending)
                }
            }
            playlistsRestored++
        }

        return RestoreReport(
            songsMatched = backup.songs.size - unmatched,
            songsUnmatched = unmatched,
            statsWritten = statsWritten,
            playlistsRestored = playlistsRestored,
            playlistsUnresolved = unresolved,
            membersSkipped = membersSkipped
        )
    }

    private fun Song.toIdentity(): SongIdentity = SongIdentity(
        provider = mediaProvider.name,
        path = path,
        externalId = externalId,
        title = name,
        album = album,
        artist = albumArtist ?: artists.joinToString(", ").ifEmpty { null },
        duration = duration,
        size = size
    )

    private fun Song.toBackedUp(): BackedUpSong = BackedUpSong(
        identity = toIdentity(),
        playCount = playCount,
        lastPlayed = lastPlayed?.toEpochMilliseconds(),
        lastCompleted = lastCompleted?.toEpochMilliseconds(),
        playbackPosition = playbackPosition,
        excluded = blacklisted,
        favouritedAt = favouritedAt?.toEpochMilliseconds(),
        dateAdded = dateAdded?.toEpochMilliseconds()
    )
}
