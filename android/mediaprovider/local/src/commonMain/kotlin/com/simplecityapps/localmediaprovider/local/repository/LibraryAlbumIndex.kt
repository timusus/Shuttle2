package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.entity.IDENTITY_GENERATION_TABLE
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongIdentityData
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.model.AlbumIndex
import com.simplecityapps.shuttle.model.AlbumIndexProvider
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The library's one [AlbumIndex], shared by everything that needs album identities (Home's sections, play history, a
 * song read by id, an album's songs, Android Auto). It's built from the songs' identity columns ([identityData], not
 * whole songs) when first asked for, and kept while the library's identity [generation] holds: it moves when a song is
 * added, removed or has an identity column change (an import, a tag edit, a delete), never for a play or a favourite,
 * so asking again costs one small read. The generation is read on each ask, so an index read straight after a write
 * sees it. With no generation (a database opened without its triggers) it's rebuilt on every ask.
 */
class LibraryAlbumIndex(
    identityChanged: Flow<*>,
    private val generation: suspend () -> Long?,
    private val identityData: suspend () -> List<SongIdentityData>
) : AlbumIndexProvider {
    private val mutex = Mutex()
    private var built: Pair<Long, AlbumIndex>? = null

    override suspend fun albumIndex(): AlbumIndex = mutex.withLock {
        val current = generation()
        built?.takeIf { (at, _) -> at == current }?.second
            ?: build().also { index -> built = current?.let { it to index } }
    }

    private suspend fun build(): AlbumIndex {
        val started = TimeSource.Monotonic.markNow()
        val rows = identityData()
        return AlbumIndex(rows.map { it.toTags() }).also { logger.debug { "Album index of ${rows.size} songs built in ${started.elapsedNow()}" } }
    }

    /** The index now, and again after each change to the library's album identities. */
    val updates: Flow<AlbumIndex> = identityChanged.map { albumIndex() }.distinctUntilChanged { old, new -> old === new }

    private companion object {
        val logger = Logger.tagged("LibraryAlbumIndex")
    }
}

/** The [LibraryAlbumIndex] over this database's songs, rebuilt when their identity generation moves. */
fun MediaDatabase.libraryAlbumIndex(): LibraryAlbumIndex = LibraryAlbumIndex(
    invalidationTracker.createFlow(IDENTITY_GENERATION_TABLE),
    songDataDao()::identityGeneration,
    songDataDao()::identityData
)
