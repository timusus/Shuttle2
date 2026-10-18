package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.PinnedCollectionDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.PlayEventDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.ResumePointDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.SongDataDao
import com.simplecityapps.localmediaprovider.local.data.room.entity.PinnedCollectionData
import com.simplecityapps.mediaprovider.splitArtistTag
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.model.AlbumIndex
import com.simplecityapps.shuttle.model.AlbumKeyChange
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.persistence.SourceStateStore

/** Moves stored album keys to the current identity rule and artist split, once per change; a run cut short finishes on the next. */
class AlbumKeyMigration(
    private val songDataDao: SongDataDao,
    private val playEventDao: PlayEventDao,
    private val resumePointDao: ResumePointDao,
    private val pinnedCollectionDao: PinnedCollectionDao,
    private val sourceState: SourceStateStore
) {
    private val logger = Logger.tagged("AlbumKeyMigration")

    /** Runs each move that's due: not yet run, and [songTagsCurrent], every source's songs holding every tag read. */
    suspend fun migrateIfDue(songTagsCurrent: Boolean) {
        if (!songTagsCurrent) return
        if (sourceState.albumKeysVersion < IDENTITY_RULE_KEYS) {
            moveToIdentityRule()
            sourceState.albumKeysVersion = IDENTITY_RULE_KEYS
        }
        if (sourceState.albumKeysVersion < SPLIT_ARTIST_KEYS) {
            splitLocalArtists()
            sourceState.albumKeysVersion = SPLIT_ARTIST_KEYS
        }
    }

    /** Moves each key stored before the album identity rule through [com.simplecityapps.shuttle.model.AlbumKeyRekey]. */
    internal suspend fun moveToIdentityRule() {
        // Read fresh, not from the shared index: the import that made this due wrote a moment ago, and Room may not have
        // told the index yet
        val rekey = AlbumIndex(songDataDao.identityData().map { it.toTags() }).rekey
        moveKeys("to the album identity rule", rekey::context)
    }

    /** Splits stored local artists as a file read does now, moving the album keys first so a run cut short can resume. */
    internal suspend fun splitLocalArtists() {
        val songs = songDataDao.identityData()
        val splitArtists = songs
            .filter { song -> !song.mediaProvider.remote }
            .mapNotNull { song -> song.artists.flatMap(::splitArtistTag).takeIf { it != song.artists }?.let { song.id to it } }
            .toMap()
        if (splitArtists.isEmpty()) return
        val change = AlbumKeyChange(
            before = AlbumIndex(songs.map { it.toTags() }),
            after = AlbumIndex(songs.map { song -> splitArtists[song.id]?.let { song.copy(artists = it) }?.toTags() ?: song.toTags() })
        )
        moveKeys("to the split artists", change::context)
        songDataDao.updateArtists(splitArtists)
    }

    private suspend fun moveKeys(
        rule: String,
        current: (PlayContext) -> PlayContext?
    ) {
        var moved = 0
        var kept = 0

        suspend fun moveKey(
            what: String,
            type: String,
            id: String,
            moveTo: suspend (key: String) -> Unit
        ) {
            val context = PlayContext.decode(type, id).takeIf { it != PlayContext.None }
            val now = context?.let(current)
            when {
                now == null -> {
                    kept++
                    logger.warn { "Kept a $what $type key no song has" }
                }

                now.id != id -> {
                    moveTo(now.id!!)
                    moved++
                }
            }
        }

        listOf(PlayContext.TYPE_ALBUM, PlayContext.TYPE_ALBUM_ARTIST).forEach { type ->
            playEventDao.contextIds(type).forEach { id -> moveKey("play history", type, id) { to -> playEventDao.moveContext(type, id, to) } }
            resumePointDao.contextIds(type).forEach { id -> moveKey("resume point", type, id) { to -> resumePointDao.move(type, id, to) } }
        }
        pinnedCollectionDao.ofType(PinnedCollectionData.CollectionType.Album).forEach { pinned ->
            moveKey("pinned", PlayContext.TYPE_ALBUM, pinned.collectionId) { to ->
                pinnedCollectionDao.move(pinned.collectionType, pinned.collectionId, pinned.mediaProviderType, to)
            }
        }
        logger.info { "Moved $moved stored album keys $rule, kept $kept" }
    }

    companion object {
        /** Album keys after the album identity rule. */
        const val IDENTITY_RULE_KEYS = 1

        /** Album keys after the local songs' ARTIST tags are split. */
        const val SPLIT_ARTIST_KEYS = 2

        const val ALBUM_KEYS_VERSION = SPLIT_ARTIST_KEYS
    }
}
