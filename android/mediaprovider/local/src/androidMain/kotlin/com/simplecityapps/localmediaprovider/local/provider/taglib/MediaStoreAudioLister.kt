package com.simplecityapps.localmediaprovider.local.provider.taglib

import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.MediaStore
import com.simplecityapps.localmediaprovider.local.data.room.dao.MediaStoreFileDao
import com.simplecityapps.localmediaprovider.local.data.room.entity.MediaStoreFileData
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

/**
 * MediaStore.MediaColumns.GENERATION_MODIFIED (API 30), spelled out so a projection can name it without a version check of
 * its own. MediaStore raises a row's generation on every change to it, so a row whose generation is the one last read
 * is unchanged.
 */
internal const val GENERATION_MODIFIED = "generation_modified"

/** A MediaStore audio row and its [GENERATION_MODIFIED]: 0 where it wasn't read (before API 30). */
internal data class MediaStoreAudioRow(
    val file: MediaStoreAudioFile,
    val generation: Long
)

/**
 * MediaStore's audio rows ([MEDIA_STORE_AUDIO_SELECTION]), every folder's: the folder rules apply after. Each call returns
 * null if MediaStore can't be queried, for example without the audio permission.
 */
internal interface MediaStoreAudioSource {
    /** `MediaStore.getVersion`: a new one is a rebuilt index, whose ids and generations aren't the old ones. */
    fun version(): String?

    /** Every row, with its generation if [withGeneration] (API 30). */
    fun rows(withGeneration: Boolean): List<MediaStoreAudioRow>?

    /** Every row's generation, by its id: two numbers a row, a fraction of what the whole listing costs to read. */
    fun generations(): Map<Long, Long>?

    /** The rows whose generation is above [generation]: those added or changed since a listing that went up to it. */
    fun rowsChangedAfter(generation: Long): List<MediaStoreAudioRow>?

    /** The rows with [ids], those still there. */
    fun rowsWithIds(ids: Collection<Long>): List<MediaStoreAudioRow>?
}

/** The listing the last stored import read, by MediaStore id, and the MediaStore [version] it came from. */
internal data class StoredMediaStoreListing(
    val version: String,
    val rows: Map<Long, MediaStoreAudioRow>
)

/** Where the listing is kept between imports. */
internal interface MediaStoreListingStore {
    /** The listing last saved, or null if there's none. */
    suspend fun load(): StoredMediaStoreListing?

    suspend fun save(change: MediaStoreListingChange)
}

/** What a listing read changed, for the store to save once the songs found from it are stored. */
internal sealed interface MediaStoreListingChange {
    val version: String

    /** MediaStore was read whole: [rows] replace the stored listing. */
    data class Whole(
        override val version: String,
        val rows: List<MediaStoreAudioRow>
    ) : MediaStoreListingChange

    /** Only what changed was read: [deletes] are the ids gone, [upserts] the rows read again. */
    data class Partial(
        override val version: String,
        val deletes: Set<Long>,
        val upserts: List<MediaStoreAudioRow>
    ) : MediaStoreListingChange
}

/** A listing: every audio file MediaStore has, and what to save so the next read can be partial (null: nothing). */
internal data class MediaStoreScan(
    val files: List<MediaStoreAudioFile>,
    val change: MediaStoreListingChange?
)

/**
 * More rows than this changed under a generation already read (a volume mounted again, say) are read with the whole
 * listing, one query, rather than by id, a query for each few hundred.
 */
internal const val MAX_ROWS_READ_BY_ID = 1000

/**
 * MediaStore's listing, read in part where it can be (#875): with a stored listing of the same MediaStore [version], the
 * listing is the stored rows less those gone, with those whose generation moved read again. Each row's generation is
 * compared rather than one high-water mark alone, because MediaStore keeps a generation per volume: a volume mounted
 * again, or one whose generations run below another's, still has its rows read. Whole if [whole], if [incremental] is
 * off (before API 30, MediaStore has no generations), without a stored listing, or when MediaStore's version changed.
 * Null if MediaStore can't be queried.
 */
internal fun scanMediaStore(
    source: MediaStoreAudioSource,
    stored: StoredMediaStoreListing?,
    incremental: Boolean,
    whole: Boolean
): MediaStoreScan? {
    if (!incremental) return source.rows(withGeneration = false)?.let { rows -> MediaStoreScan(rows.map { row -> row.file }, change = null) }
    // Without a version, a listing can't be told apart from one of a rebuilt index, so none is kept
    val version = source.version() ?: return source.rows(withGeneration = true)?.let { rows -> MediaStoreScan(rows.map { row -> row.file }, change = null) }
    val readWhole = {
        source.rows(withGeneration = true)?.let { rows -> MediaStoreScan(rows.map { row -> row.file }, MediaStoreListingChange.Whole(version, rows)) }
    }
    if (whole || stored == null || stored.version != version) return readWhole()
    val generations = source.generations() ?: return null
    // New rows, and those whose generation moved since they were read
    val changedIds = generations.filter { (id, generation) -> stored.rows[id]?.generation != generation }.keys
    val changed =
        if (changedIds.isEmpty()) {
            emptyList()
        } else {
            // One query for what changed since the newest row read, then by id those it left out
            val highWater = stored.rows.values.maxOfOrNull { row -> row.generation } ?: 0L
            val above = source.rowsChangedAfter(highWater) ?: return null
            val aboveIds = above.mapTo(HashSet()) { row -> row.file.id }
            val rest = changedIds.filter { id -> id !in aboveIds }
            if (rest.size > MAX_ROWS_READ_BY_ID) return readWhole()
            above + (if (rest.isEmpty()) emptyList() else source.rowsWithIds(rest) ?: return null)
        }
    val changedById = changed.associateBy { row -> row.file.id }
    // A changed row that can't be read again is gone (removed between the queries), not kept as it was
    val rows = stored.rows.filterKeys { id -> id in generations && id !in changedIds } + changedById
    return MediaStoreScan(
        files = rows.values.map { row -> row.file },
        change = MediaStoreListingChange.Partial(version, deletes = stored.rows.keys - rows.keys, upserts = changedById.values.toList())
    )
}

/**
 * Lists MediaStore's audio files for the local scanner, reading only what changed since the last stored import where it
 * can ([scanMediaStore]). The listing is saved by [listingStored], once the songs found from it are: an import that fails
 * part way leaves the last one's, which the next read compares against.
 */
class MediaStoreAudioLister internal constructor(
    private val source: MediaStoreAudioSource,
    // Null: read whole every time, and keep nothing
    private val store: MediaStoreListingStore?,
    private val incremental: Boolean = store != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
) {
    @Volatile
    private var pending: MediaStoreListingChange? = null

    /** Every audio file MediaStore has, read whole if [whole]; null if MediaStore can't be queried. */
    internal suspend fun list(whole: Boolean): List<MediaStoreAudioFile>? {
        pending = null
        val stored = if (incremental && !whole) load() else null
        val scan = scanMediaStore(source, stored, incremental, whole) ?: return null
        pending = scan.change
        if (scan.change is MediaStoreListingChange.Partial) {
            Timber.i("Read ${scan.change.upserts.size} changed MediaStore rows, ${scan.change.deletes.size} gone, of ${scan.files.size}")
        }
        return scan.files
    }

    /** The songs found from the last [list] are stored: keeps its listing, for the next to read only what changed since. */
    suspend fun listingStored() {
        val change = pending ?: return
        pending = null
        val store = store ?: return
        try {
            store.save(change)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The listing kept is then an older one, which the next read compares against all the same
            Timber.e(e, "Failed to save the MediaStore listing")
        }
    }

    private suspend fun load(): StoredMediaStoreListing? = try {
        store?.load()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Failed to load the MediaStore listing; reading it whole")
        null
    }

    companion object {
        /** Reads MediaStore whole on every import. */
        fun whole(context: Context): MediaStoreAudioLister = MediaStoreAudioLister(ContentResolverMediaStoreAudioSource(context), store = null)

        /** Reads only what changed since the last import that stored its songs, keeping the listing through [dao]. */
        fun incremental(
            context: Context,
            dao: MediaStoreFileDao
        ): MediaStoreAudioLister = MediaStoreAudioLister(ContentResolverMediaStoreAudioSource(context), RoomMediaStoreListingStore(dao))
    }
}

/** [MediaStoreAudioSource] over the content resolver. */
internal class ContentResolverMediaStoreAudioSource(
    private val context: Context
) : MediaStoreAudioSource {
    override fun version(): String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            MediaStore.getVersion(context)
        } catch (e: Exception) {
            Timber.e(e, "Failed to read the MediaStore version")
            null
        }
    } else {
        null
    }

    override fun rows(withGeneration: Boolean): List<MediaStoreAudioRow>? = query(
        projection = if (withGeneration) MEDIA_STORE_AUDIO_PROJECTION + GENERATION_MODIFIED else MEDIA_STORE_AUDIO_PROJECTION,
        selection = MEDIA_STORE_AUDIO_SELECTION,
        selectionArgs = null
    ) { cursor -> cursor.readMediaStoreAudioRows() }

    override fun generations(): Map<Long, Long>? = query(
        projection = arrayOf(MediaStore.Audio.Media._ID, GENERATION_MODIFIED),
        selection = MEDIA_STORE_AUDIO_SELECTION,
        selectionArgs = null
    ) { cursor ->
        val generations = HashMap<Long, Long>(cursor.count)
        while (cursor.moveToNext()) generations[cursor.getLong(0)] = cursor.getLong(1)
        generations
    }

    override fun rowsChangedAfter(generation: Long): List<MediaStoreAudioRow>? = query(
        projection = MEDIA_STORE_AUDIO_PROJECTION + GENERATION_MODIFIED,
        selection = "($MEDIA_STORE_AUDIO_SELECTION) AND $GENERATION_MODIFIED > ?",
        selectionArgs = arrayOf(generation.toString())
    ) { cursor -> cursor.readMediaStoreAudioRows() }

    override fun rowsWithIds(ids: Collection<Long>): List<MediaStoreAudioRow>? = ids
        // SQLite caps the variables one statement can bind
        .chunked(500)
        .flatMap { chunk ->
            query(
                projection = MEDIA_STORE_AUDIO_PROJECTION + GENERATION_MODIFIED,
                selection = "($MEDIA_STORE_AUDIO_SELECTION) AND ${MediaStore.Audio.Media._ID} IN (${chunk.joinToString(",") { "?" }})",
                selectionArgs = chunk.map { id -> id.toString() }.toTypedArray()
            ) { cursor -> cursor.readMediaStoreAudioRows() } ?: return null
        }

    private fun <T> query(
        projection: Array<String>,
        selection: String,
        selectionArgs: Array<String>?,
        read: (Cursor) -> T
    ): T? = try {
        context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, null)?.use(read)
    } catch (e: SecurityException) {
        Timber.e(e, "Failed to query MediaStore for audio files")
        null
    }
}

/** [MediaStoreListingStore] in the media database's `media_store_files`. */
internal class RoomMediaStoreListingStore(
    private val dao: MediaStoreFileDao
) : MediaStoreListingStore {
    override suspend fun load(): StoredMediaStoreListing? {
        val version = dao.version() ?: return null
        return StoredMediaStoreListing(version, dao.files().associate { data -> data.id to data.toRow() })
    }

    override suspend fun save(change: MediaStoreListingChange) {
        when (change) {
            is MediaStoreListingChange.Whole -> dao.replaceAll(change.version, change.rows.map { row -> row.toData() })
            is MediaStoreListingChange.Partial -> dao.applyChanges(change.version, change.deletes.toList(), change.upserts.map { row -> row.toData() })
        }
    }
}

private fun MediaStoreFileData.toRow(): MediaStoreAudioRow = MediaStoreAudioRow(
    file = MediaStoreAudioFile(id = id, path = path, displayName = displayName, size = size, lastModified = lastModified, mimeType = mimeType, duration = duration),
    generation = generation
)

private fun MediaStoreAudioRow.toData(): MediaStoreFileData = MediaStoreFileData(
    id = file.id,
    generation = generation,
    path = file.path,
    displayName = file.displayName,
    size = file.size,
    lastModified = file.lastModified,
    mimeType = file.mimeType,
    duration = file.duration
)
