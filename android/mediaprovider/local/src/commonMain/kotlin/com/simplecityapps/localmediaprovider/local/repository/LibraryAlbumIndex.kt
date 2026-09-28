package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongIdentityData
import com.simplecityapps.shuttle.model.AlbumIndex
import com.simplecityapps.shuttle.model.AlbumIndexProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The library's one [AlbumIndex], shared by everything that needs album identities (Home's sections, play history, a
 * song read by id, an album's songs, Android Auto). It's built from the songs' identity columns ([identityData], not
 * whole songs) when first asked for, and kept until [songsChanged] says the songs table changed (Room's invalidation
 * flow for it): asking again while the library is unchanged costs nothing.
 *
 * Room tells of a change a moment after the write, so an index read straight after one can be the one before it. What
 * must see a write (the one-time move of stored keys after an import) builds its own from fresh rows instead.
 */
class LibraryAlbumIndex(
    scope: CoroutineScope,
    songsChanged: Flow<*>,
    private val identityData: suspend () -> List<SongIdentityData>
) : AlbumIndexProvider {
    /** Bumped on each change to the songs table: an index built at an older generation is stale. */
    private val generation = MutableStateFlow(0L)

    private val mutex = Mutex()
    private var built: Pair<Long, AlbumIndex>? = null

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { songsChanged.collect { generation.update { it + 1 } } }
    }

    override suspend fun albumIndex(): AlbumIndex = mutex.withLock {
        val current = generation.value
        built?.takeIf { (at, _) -> at == current }?.second
            ?: AlbumIndex(identityData().map { it.toTags() }).also { built = current to it }
    }

    /** The index now, and again after each change to the songs table. */
    val updates: Flow<AlbumIndex> = generation.map { albumIndex() }
}

/** The [LibraryAlbumIndex] over this database's songs table, rebuilt when Room says the table changed. */
fun MediaDatabase.libraryAlbumIndex(scope: CoroutineScope): LibraryAlbumIndex = LibraryAlbumIndex(
    scope,
    invalidationTracker.createFlow(SONGS_TABLE, emitInitialState = false),
    songDataDao()::identityData
)

private const val SONGS_TABLE = "songs"
