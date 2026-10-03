package com.simplecityapps.shuttle.ui.screens.settings.backup

import android.content.Context
import android.net.Uri
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.di.ApplicationContext
import com.simplecityapps.shuttle.di.IoDispatcher
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import dev.zacsweers.metro.Inject
import java.io.IOException
import java.io.InputStream
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Library backup export/import (Settings -> Library). Backs up per-song stats (play counts,
 * positions, favourites, exclusions, dates) plus playlists; restore merges them into the library,
 * keeping whatever the device already has (see [LibraryBackupRestorer]).
 *
 * Never backed up: Room ids, credentials/tokens, SAF grant URIs, transient queue/session state,
 * artwork caches and aggregates. Restored favourites go through the same DAO path as the UI's, so remote-provider
 * ones are queued for the server.
 */
class LibraryBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MediaDatabase,
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : LibraryBackupFlow {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    override suspend fun buildBackupJson(): String? = withContext(ioDispatcher) {
        try {
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to build library backup")
            null
        }
    }

    override suspend fun writeBackup(destinationUri: String, backupJson: String): Boolean = withContext(ioDispatcher) {
        try {
            context.contentResolver.openOutputStream(Uri.parse(destinationUri))?.use { out ->
                out.write(backupJson.toByteArray(Charsets.UTF_8))
                true
            } ?: run {
                Timber.w("Could not open output stream for library backup: $destinationUri")
                false
            }
        } catch (e: IOException) {
            Timber.e(e, "Failed to write library backup to $destinationUri")
            false
        } catch (e: SecurityException) {
            Timber.e(e, "Permission denied writing library backup to $destinationUri")
            false
        }
    }

    override suspend fun readAndRestore(sourceUri: String): RestoreReport? = withContext(ioDispatcher) {
        val backup = try {
            context.contentResolver.openInputStream(Uri.parse(sourceUri))?.use { input ->
                readCapped(input, MAX_IMPORT_BYTES)?.let { json.decodeFromString<LibraryBackup>(it.toString(Charsets.UTF_8)) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to read library backup from $sourceUri")
            null
        } ?: return@withContext null
        if (backup.schemaVersion > LibraryBackup.CURRENT_SCHEMA_VERSION) {
            Timber.w("Backup schema ${backup.schemaVersion} newer than supported ${LibraryBackup.CURRENT_SCHEMA_VERSION}")
            return@withContext null
        }
        restore(backup)
    }

    private suspend fun restore(backup: LibraryBackup): RestoreReport {
        val library = songRepository.loadSongs(SongQuery.All(includeExcluded = true))
        return LibraryBackupRestorer(playlistRepository, { database.songDataDao().restoreStats(it) }).restore(backup, library)
    }

    private fun Song.toIdentity(): SongIdentity = SongIdentity(
        provider = mediaProvider.name,
        path = path,
        externalId = externalId,
        title = name,
        album = album,
        artist = LibraryBackupMatcher.fingerprintArtist(this),
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

    internal companion object {
        const val MAX_IMPORT_BYTES = 64L * 1024 * 1024

        /** All of [input], or null once it runs past [maxBytes]. */
        fun readCapped(
            input: InputStream,
            maxBytes: Long
        ): ByteArray? {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) return out.toByteArray()
                total += read
                if (total > maxBytes) {
                    Timber.w("Library backup larger than $maxBytes bytes, refusing to read it")
                    return null
                }
                out.write(buffer, 0, read)
            }
        }
    }
}
