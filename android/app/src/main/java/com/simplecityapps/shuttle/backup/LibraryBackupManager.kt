package com.simplecityapps.shuttle.backup

import android.content.Context
import android.net.Uri
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.di.ApplicationContext
import com.simplecityapps.shuttle.di.IoDispatcher
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.KeyValueStore
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.settings.Setting
import com.simplecityapps.shuttle.ui.screens.settings.SettingsEffects
import com.simplecityapps.shuttle.ui.screens.settings.backup.BackedUpPlaylist
import com.simplecityapps.shuttle.ui.screens.settings.backup.BackedUpSong
import com.simplecityapps.shuttle.ui.screens.settings.backup.LibraryBackup
import com.simplecityapps.shuttle.ui.screens.settings.backup.LibraryBackupFlow
import com.simplecityapps.shuttle.ui.screens.settings.backup.LibraryBackupMatcher
import com.simplecityapps.shuttle.ui.screens.settings.backup.LibraryBackupRestorer
import com.simplecityapps.shuttle.ui.screens.settings.backup.RestoreReport
import com.simplecityapps.shuttle.ui.screens.settings.backup.SongIdentity
import dev.zacsweers.metro.Inject
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromStream
import timber.log.Timber

/**
 * Library backup export/import (Settings -> Library). Backs up per-song stats (play counts,
 * positions, favourites, exclusions, dates), playlists and the allowlisted preferences ([BackedUpSettings]).
 * Restore merges stats and playlists into the library, keeping whatever the device already has (see
 * [LibraryBackupRestorer]); preferences are replaced.
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
    private val keyValueStore: KeyValueStore,
    private val effects: SettingsEffects,
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
                    playlists = backedPlaylists,
                    settings = BackedUpSettings.export(keyValueStore)
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
                json.decodeFromStream<LibraryBackup>(CappedInputStream(input, MAX_IMPORT_BYTES))
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
        val report = LibraryBackupRestorer(playlistRepository, { database.songDataDao().restoreStats(it) }).restore(backup, library)
        return backup.settings?.let { report.copy(settingsRestored = restoreSettings(it)) } ?: report
    }

    /** Replaces the preferences, then runs each changed setting's side effect, so it applies without a restart. */
    private fun restoreSettings(values: Map<String, JsonPrimitive>): Int {
        val before = BackedUpSettings.settings.associateWith { it.read(keyValueStore) }
        val written = BackedUpSettings.restore(keyValueStore, values)
        BackedUpSettings.settings.forEach { setting -> applyIfChanged(setting, before[setting]) }
        return written
    }

    private fun <T> applyIfChanged(setting: Setting<T>, previous: Any?) {
        val current = setting.read(keyValueStore)
        if (current != previous) effects.onSettingChanged(setting, current)
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
    }

    /** Fails with a [BackupTooLargeException] once more than [maxBytes] have been read. */
    internal class CappedInputStream(
        input: InputStream,
        private val maxBytes: Long
    ) : FilterInputStream(input) {
        private var total = 0L

        override fun read(): Int = super.read().also { if (it >= 0) count(1) }

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int
        ): Int = super.read(b, off, len).also { if (it > 0) count(it) }

        private fun count(n: Int) {
            total += n
            if (total > maxBytes) throw BackupTooLargeException(maxBytes)
        }
    }

    internal class BackupTooLargeException(maxBytes: Long) : IOException("Library backup larger than $maxBytes bytes")
}
