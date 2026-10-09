package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.IGNORE
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.simplecityapps.localmediaprovider.local.data.room.entity.PendingFavouriteData
import com.simplecityapps.localmediaprovider.local.data.room.entity.SONG_COLUMNS
import com.simplecityapps.localmediaprovider.local.data.room.entity.SONG_IDENTITY_QUERY
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongDataUpdate
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongIdentityData
import com.simplecityapps.localmediaprovider.local.data.room.entity.toSongDataUpdate
import com.simplecityapps.mediaprovider.SongPathRemap
import com.simplecityapps.mediaprovider.repository.songs.SongStatsRestore
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.withAlbumIdentities
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/** One row of [SongDataDao.countVisibleByProvider]. */
data class ProviderSongCount(
    val mediaProvider: MediaProviderType,
    val count: Int
)

@Dao
abstract class SongDataDao {
    /** One song's lyrics, which the song lists leave out; null where it has none (or no such song). */
    @Query("SELECT lyrics FROM songs WHERE id = :id")
    abstract suspend fun lyrics(id: Long): String?

    @Transaction
    @Query("SELECT $SONG_COLUMNS FROM songs")
    abstract suspend fun get(): List<SongData>

    /** One provider's songs as stored, whatever the user's filters, for the importer to diff against. */
    @Transaction
    @Query("SELECT $SONG_COLUMNS FROM songs WHERE mediaProvider = :mediaProvider")
    abstract suspend fun getByProvider(mediaProvider: MediaProviderType): List<SongData>

    @Transaction
    @Query("SELECT $SONG_COLUMNS FROM songs ORDER BY albumArtist, album, track")
    abstract fun getAllSongData(): Flow<List<SongData>>

    /** The songs not excluded, and at least [minDurationMs] long (or of no known duration): what the library shows. */
    @Query("SELECT COUNT(*) FROM songs WHERE blacklisted = 0 AND (duration <= 0 OR duration >= :minDurationMs)")
    abstract fun countVisible(minDurationMs: Int): Flow<Int>

    /** [countVisible] per media provider; a provider with no visible songs has no row. */
    @Query("SELECT mediaProvider, COUNT(*) AS count FROM songs WHERE blacklisted = 0 AND (duration <= 0 OR duration >= :minDurationMs) GROUP BY mediaProvider")
    abstract fun countVisibleByProvider(minDurationMs: Int): Flow<List<ProviderSongCount>>

    /** The whole library, each song holding its album identity among the others. */
    fun getAll(): Flow<List<Song>> = getAllSongData().map { list -> list.map { songData -> songData.toSong() }.withAlbumIdentities() }

    /** Every song's album identity columns, for resolving the library's album identities without reading every song whole. */
    @Query(SONG_IDENTITY_QUERY)
    abstract suspend fun identityData(): List<SongIdentityData>

    /** The library's identity generation, or null in a database opened without its triggers (see `IdentityGenerationTriggers`). */
    @Query("SELECT generation FROM identity_generation WHERE id = 0")
    abstract suspend fun identityGeneration(): Long?

    @Transaction
    @Query("SELECT $SONG_COLUMNS FROM songs WHERE id IN (:ids)")
    abstract fun getSongDataByIds(ids: List<Long>): Flow<List<SongData>>

    /**
     * The songs with [ids] (each once, however often it's listed, in no particular order), read by id rather than from
     * the whole library. They don't hold their album identities: the caller adds them from the library's index.
     * Queried in chunks, as SQLite before 3.32 (below API 31) binds at most 999 variables a statement.
     */
    fun getByIds(ids: List<Long>): Flow<List<Song>> {
        val chunks = ids.distinct().chunked(MAX_BOUND_VARIABLES)
        if (chunks.isEmpty()) return flowOf(emptyList())
        return combine(chunks.map(::getSongDataByIds)) { lists -> lists.flatMap { list -> list.map { songData -> songData.toSong() } } }
    }

    @Transaction
    @Query("SELECT $SONG_COLUMNS FROM songs WHERE id IN (:ids)")
    abstract suspend fun songDataByIds(ids: List<Long>): List<SongData>

    /** [getByIds], read once. */
    suspend fun loadByIds(ids: List<Long>): List<Song> = ids.distinct().chunked(MAX_BOUND_VARIABLES).flatMap { chunk -> songDataByIds(chunk).map { songData -> songData.toSong() } }

    @Insert(onConflict = IGNORE)
    abstract suspend fun insert(songData: List<SongData>): List<Long>

    @Update(onConflict = IGNORE, entity = SongData::class)
    abstract suspend fun update(songData: List<SongDataUpdate>): Int

    @Update(onConflict = IGNORE, entity = SongData::class)
    abstract suspend fun update(songData: SongDataUpdate): Int

    @Delete
    abstract suspend fun delete(songData: List<SongData>): Int

    /**
     * Stores an import's diff in one transaction. [updates] are written without their favourite or play stats (see
     * [SongDataUpdate]); a remote-provider song's favourite and play stats, as its server reports them, are then merged in
     * by [mergeServerFavourites] and [mergeServerPlayStats]. An
     * insert is a song new to the library, so it has no `pending_favourites` row and is stored with the server's state.
     */
    @Transaction
    open suspend fun insertUpdateAndDelete(
        inserts: List<SongData>,
        updates: List<SongData>,
        deletes: List<SongData>
    ): Triple<Int, Int, Int> {
        val insertCount = insert(inserts)
        val updateCount = update(updates.map { it.toSongDataUpdate() })
        val remoteUpdates = updates.filter { it.mediaProvider.remote }
        mergeServerFavourites(remoteUpdates)
        mergeServerPlayStats(remoteUpdates)
        val deleteCount = delete(deletes)
        return Triple(insertCount.count { id -> id != -1L }, updateCount, deleteCount)
    }

    /**
     * Takes each of [songs]' favourite from its server (#497): a server favourite keeps the time it already has, or takes
     * the server's ([SongData.favouritedAt]); anything else is cleared. A song with a `pending_favourites` row is skipped:
     * the local toggle not yet sent wins over the server's older state. Read inside the caller's transaction, so a toggle
     * can't land between the outbox check and the write.
     */
    private suspend fun mergeServerFavourites(songs: List<SongData>) {
        val (favourites, others) = songs.partition { it.favouritedAt != null }
        others.map { it.id }.chunked(MAX_BOUND_VARIABLES).forEach { chunk -> clearServerUnfavourites(chunk) }
        // Grouped by time: a Jellyfin or Emby sync stamps all its favourites with one
        favourites.groupBy { it.favouritedAt!! }.forEach { (favouritedAt, group) ->
            group.map { it.id }.chunked(MAX_BOUND_VARIABLES - 1).forEach { chunk -> stampServerFavourites(chunk, favouritedAt) }
        }
    }

    /**
     * Folds each of [songs]' server play stats into the row (#772): the larger play count and the later last-played time.
     * The app reports its own plays to the server, so the server's count already includes them; taking the maximum, rather
     * than adding, never double-counts, and the SQL form means a play finishing during the sync isn't overwritten.
     */
    private suspend fun mergeServerPlayStats(songs: List<SongData>) {
        songs.filter { it.playCount > 0 || it.lastPlayed != null }.forEach { song -> applyServerPlayStats(song.id, song.playCount, song.lastPlayed) }
    }

    @Query(
        """
        UPDATE songs SET
            playCount = MAX(playCount, :playCount),
            lastPlayed = COALESCE(MAX(lastPlayed, :lastPlayed), lastPlayed, :lastPlayed)
        WHERE id = :id
        """
    )
    abstract suspend fun applyServerPlayStats(
        id: Long,
        playCount: Int,
        lastPlayed: Instant?
    )

    @Query("UPDATE songs SET favouritedAt = NULL WHERE id IN (:ids) AND favouritedAt IS NOT NULL AND id NOT IN (SELECT songId FROM pending_favourites)")
    abstract suspend fun clearServerUnfavourites(ids: List<Long>): Int

    @Query("UPDATE songs SET favouritedAt = :favouritedAt WHERE id IN (:ids) AND favouritedAt IS NULL AND id NOT IN (SELECT songId FROM pending_favourites)")
    abstract suspend fun stampServerFavourites(
        ids: List<Long>,
        favouritedAt: Instant
    ): Int

    @Query("SELECT id FROM songs WHERE path = :path AND mediaProvider = :mediaProvider")
    abstract suspend fun idForPath(
        path: String,
        mediaProvider: MediaProviderType
    ): Long?

    @Query("UPDATE songs SET path = :path WHERE id = :id")
    abstract suspend fun updatePath(
        id: Long,
        path: String
    ): Int

    @Query("UPDATE songs SET artists = :artists WHERE id = :id")
    abstract suspend fun updateArtists(
        id: Long,
        artists: List<String>
    ): Int

    /** Sets each song's artists, by song id, together. */
    @Transaction
    open suspend fun updateArtists(artists: Map<Long, List<String>>) {
        artists.forEach { (id, songArtists) -> updateArtists(id, songArtists) }
    }

    @Query("UPDATE playlist_song_join SET songId = :songId WHERE songId IN (:fromSongIds)")
    abstract suspend fun movePlaylistEntries(
        fromSongIds: List<Long>,
        songId: Long
    )

    /**
     * Moves each song to its remapped path, keeping its row id and so everything keyed by it. A remap whose path is
     * already another song's for the same provider (reported to [onPathTaken] with that song's id), or whose song is
     * gone, is skipped: paths are unique per provider.
     *
     * @return the remaps applied
     */
    @Transaction
    open suspend fun remapPaths(
        remaps: List<SongPathRemap>,
        mediaProviderType: MediaProviderType,
        onPathTaken: (remap: SongPathRemap, pathOwner: Long) -> Unit
    ): List<SongPathRemap> = remaps.filter { remap ->
        val pathOwner = idForPath(remap.path, mediaProviderType)
        when {
            pathOwner != null && pathOwner != remap.songId -> {
                onPathTaken(remap, pathOwner)
                false
            }

            updatePath(remap.songId, remap.path) == 0 -> false

            else -> {
                if (remap.duplicateIds.isNotEmpty()) {
                    movePlaylistEntries(remap.duplicateIds, remap.songId)
                    keepFavourite(remap.duplicateIds, remap.songId)
                }
                true
            }
        }
    }

    @Query("UPDATE songs SET playbackPosition = :playbackPosition, lastPlayed = :lastPlayed WHERE id =:id")
    abstract suspend fun updatePlaybackPosition(
        id: Long,
        playbackPosition: Int,
        lastPlayed: Instant = Clock.System.now()
    )

    /**
     * Merges a backup's stats into the row (see LibraryBackupManager) against the row as it is now, so a play finishing
     * during a restore isn't lost: counts and dates only move forward (`dateAdded` earlier), and the backup's position
     * is taken only when its `lastPlayed` is newer than the row's. Used only by library-backup restore, never by the
     * scanner, so rescan semantics are untouched.
     */
    @Query(
        """
        UPDATE songs SET
            playCount = MAX(playCount, :playCount),
            playbackPosition = CASE WHEN :lastPlayed IS NOT NULL AND (lastPlayed IS NULL OR :lastPlayed > lastPlayed) THEN :playbackPosition ELSE playbackPosition END,
            lastPlayed = COALESCE(MAX(lastPlayed, :lastPlayed), lastPlayed, :lastPlayed),
            lastCompleted = COALESCE(MAX(lastCompleted, :lastCompleted), lastCompleted, :lastCompleted),
            dateAdded = COALESCE(MIN(dateAdded, :dateAdded), dateAdded, :dateAdded)
        WHERE id = :id
        """
    )
    abstract suspend fun restoreStats(
        id: Long,
        playCount: Int,
        lastPlayed: Instant?,
        lastCompleted: Instant?,
        playbackPosition: Int,
        dateAdded: Instant?
    )

    /**
     * Writes the merged stats of a backup restore for [restores] in one transaction: the stat columns per song, the
     * excluded flag in bulk, and the new favourites through [setFavourite], so a remote-provider song gets its
     * `pending_favourites` row like a favourite made in the UI. Favourites are only ever added here, at the backup's time.
     */
    @Transaction
    open suspend fun restoreStats(restores: List<SongStatsRestore>) {
        restores.forEach { restore ->
            restoreStats(
                id = restore.song.id,
                playCount = restore.playCount,
                lastPlayed = restore.lastPlayed,
                lastCompleted = restore.lastCompleted,
                playbackPosition = restore.playbackPosition,
                dateAdded = restore.dateAdded
            )
        }
        restores.filter { it.excluded != it.song.blacklisted }.groupBy { it.excluded }.forEach { (excluded, group) ->
            group.map { it.song.id }.chunked(MAX_BOUND_VARIABLES - 1).forEach { chunk -> setExcluded(chunk, excluded) }
        }
        val favourites = restores.filter { it.favouritedAt != null && it.song.favouritedAt == null }
            .map { it.song.copy(favouritedAt = it.favouritedAt) }
        if (favourites.isNotEmpty()) setFavourite(favourites, true)
    }

    /** [updatePlaybackPosition] and incrementing the play count as one write, for a track playing through to its end. */
    @Query("UPDATE songs SET playbackPosition = :playbackPosition, lastPlayed = :now, playCount = (SELECT songs.playCount + 1), lastCompleted = :now WHERE id =:id")
    abstract suspend fun recordPlayedThrough(
        id: Long,
        playbackPosition: Int,
        now: Instant = Clock.System.now()
    )

    /** Makes [songId] a favourite if any of [fromSongIds] (duplicates of it about to go) is one, from the earliest of them. */
    @Query("UPDATE songs SET favouritedAt = (SELECT MIN(favouritedAt) FROM songs WHERE id IN (:fromSongIds)) WHERE id = :songId AND favouritedAt IS NULL")
    abstract suspend fun keepFavourite(
        fromSongIds: List<Long>,
        songId: Long
    )

    /** Makes the songs with [ids] favourites as of [now]; one that already is keeps its time, and so its place in the list. */
    @Query("UPDATE songs SET favouritedAt = :now WHERE id IN (:ids) AND favouritedAt IS NULL")
    abstract suspend fun favourite(
        ids: List<Long>,
        now: Instant = Clock.System.now()
    ): Int

    /** Makes the song with [id] a favourite as of [favouritedAt], if it isn't one already: an Undo restoring [Song.favouritedAt] gets its original place back rather than moving to the top (#564). */
    @Query("UPDATE songs SET favouritedAt = :favouritedAt WHERE id = :id AND favouritedAt IS NULL")
    abstract suspend fun favourite(
        id: Long,
        favouritedAt: Instant
    ): Int

    @Query("UPDATE songs SET favouritedAt = NULL WHERE id IN (:ids)")
    abstract suspend fun unfavourite(ids: List<Long>): Int

    /**
     * [favourite] or [unfavourite] [songs], in chunks, as SQLite before 3.32 (below API 31) binds at most 999 variables a
     * statement. A song that already carries a [Song.favouritedAt] (an Undo restoring one just removed) is set to that
     * exact time rather than now, so it keeps its original place in the list (#564). Every remote-provider song among
     * [songs] also gets a `pending_favourites` row recording the desired state, in the same transaction, for
     * `FavouriteSender` to push to its server (#497); local songs never enqueue.
     */
    @Transaction
    open suspend fun setFavourite(
        songs: List<Song>,
        favourite: Boolean,
        now: Instant = Clock.System.now()
    ): Int {
        val count = if (!favourite) {
            songs.map { it.id }.distinct().chunked(MAX_BOUND_VARIABLES - 1).sumOf { chunk -> unfavourite(chunk) }
        } else {
            val (toRestore, toStamp) = songs.distinctBy { it.id }.partition { it.favouritedAt != null }
            val restored = toRestore.sumOf { song -> favourite(song.id, song.favouritedAt!!) }
            val stamped = toStamp.map { it.id }.chunked(MAX_BOUND_VARIABLES - 1).sumOf { chunk -> favourite(chunk, now) }
            restored + stamped
        }
        enqueuePendingFavourites(songs, favourite, now)
        return count
    }

    /**
     * One `pending_favourites` row per remote-provider song among [songs], overwriting any row already pending for it
     * so only the latest desired state survives to be sent (#497): a favourite then an unfavourite before it is sent
     * leaves a single row with the final state, not two queued operations.
     */
    private suspend fun enqueuePendingFavourites(
        songs: List<Song>,
        favourite: Boolean,
        changedAt: Instant
    ) {
        songs.distinctBy { it.id }.filter { it.mediaProvider.remote }.forEach { song ->
            enqueuePendingFavourite(PendingFavouriteData(song.id, favourite, changedAt))
        }
    }

    @Insert(onConflict = REPLACE)
    abstract suspend fun enqueuePendingFavourite(pendingFavourite: PendingFavouriteData)

    /** The outbox, oldest change first, so the writer sends in the order the user made them. */
    @Query("SELECT * FROM pending_favourites ORDER BY changedAt, songId")
    abstract suspend fun getPendingFavourites(): List<PendingFavouriteData>

    /** The outbox, re-emitted whenever a row is added, replaced or acked. */
    @Query("SELECT * FROM pending_favourites ORDER BY changedAt, songId")
    abstract fun observePendingFavourites(): Flow<List<PendingFavouriteData>>

    /**
     * Acks [pendingFavourite] once the server has taken it: deletes its row, but only while the row is still that exact
     * entry. A toggle made while it was being sent replaced the row with a newer [PendingFavouriteData.changedAt], and
     * that newer state hasn't been sent yet, so it stays. Returns whether a row was deleted.
     */
    @Transaction
    open suspend fun ackPendingFavourite(pendingFavourite: PendingFavouriteData): Boolean = deletePendingFavourite(pendingFavourite.songId, pendingFavourite.favourite, pendingFavourite.changedAt) > 0

    @Query("DELETE FROM pending_favourites WHERE songId = :songId AND favourite = :favourite AND changedAt = :changedAt")
    abstract suspend fun deletePendingFavourite(
        songId: Long,
        favourite: Boolean,
        changedAt: Instant
    ): Int

    @Query("SELECT id FROM songs WHERE favouritedAt IS NOT NULL")
    abstract fun getFavouriteIds(): Flow<List<Long>>

    @Query("UPDATE songs SET blacklisted = :blacklisted WHERE id IN (:ids)")
    abstract suspend fun setExcluded(
        ids: List<Long>,
        blacklisted: Boolean
    ): Int

    @Query("UPDATE songs SET blacklisted = 0")
    abstract suspend fun clearExcludeList()

    @Query("DELETE FROM songs where mediaProvider = :mediaProviderType")
    abstract suspend fun deleteAll(mediaProviderType: MediaProviderType)

    @Delete
    abstract suspend fun deleteAll(songData: List<SongData>): Int

    @Query("DELETE FROM songs WHERE id = :id")
    abstract suspend fun delete(id: Long)
}

private const val MAX_BOUND_VARIABLES = 999

fun SongData.toSong(): Song = Song(
    id = id,
    name = name,
    albumArtist = albumArtist,
    artists = artists,
    album = album,
    track = track,
    disc = disc,
    duration = duration,
    date = year?.let { LocalDate(it, 1, 1) },
    genres = genres,
    path = path,
    size = size,
    mimeType = mimeType,
    lastModified = lastModified,
    lastPlayed = lastPlayed,
    lastCompleted = lastCompleted,
    playCount = playCount,
    playbackPosition = playbackPosition,
    blacklisted = excluded,
    externalId = externalId,
    mediaProvider = mediaProvider,
    replayGainTrack = replayGainTrack,
    replayGainAlbum = replayGainAlbum,
    lyrics = null,
    grouping = grouping,
    bitRate = bitRate,
    bitDepth = bitDepth,
    sampleRate = sampleRate,
    channelCount = channelCount,
    audioCodec = audioCodec,
    artworkVersion = artworkVersion,
    dateAdded = dateAdded,
    favouritedAt = favouritedAt,
    albumArtists = albumArtists,
    artistsTag = artistsTag,
    artistDisplay = artistDisplay,
    compilation = compilation,
    mbTrackId = mbTrackId,
    mbAlbumId = mbAlbumId,
    mbReleaseGroupId = mbReleaseGroupId,
    mbArtistIds = mbArtistIds,
    mbAlbumArtistIds = mbAlbumArtistIds,
    serverAlbumId = serverAlbumId,
    serverArtistIds = serverArtistIds,
    serverAlbumArtistIds = serverAlbumArtistIds
)
