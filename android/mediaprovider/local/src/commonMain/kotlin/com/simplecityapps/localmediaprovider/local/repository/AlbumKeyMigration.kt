package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.PinnedCollectionDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.PlayEventDao
import com.simplecityapps.localmediaprovider.local.data.room.entity.PinnedCollectionData
import com.simplecityapps.localmediaprovider.local.data.room.entity.albumKeyRekey
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.model.AlbumKeyRekey
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager

/**
 * Moves the album and album artist keys stored before the album identity rule (#637) to the keys their songs have now,
 * once: the play history's album and album artist contexts, and the pinned albums. It runs after the first import that
 * leaves every source's tags current (the MusicBrainz and server album ids the rule reads arrive with that import), and
 * each key moves through [AlbumKeyRekey]. A key it can't map (no song had it, or it doesn't read back) is kept as it is
 * and logged. Each key moves in one statement and a key already moved maps to itself, so a run cut short (the process
 * killed part way) just finishes on the next one. To be deleted with [AlbumKeyRekey].
 */
class AlbumKeyMigration(
    private val playEventDao: PlayEventDao,
    private val pinnedCollectionDao: PinnedCollectionDao,
    private val preferenceManager: GeneralPreferenceManager
) {
    private val logger = Logger.tagged("AlbumKeyMigration")

    /** Runs the move when it's due: not yet run, and [songTagsCurrent], every source's songs holding every tag read. */
    suspend fun migrateIfDue(songTagsCurrent: Boolean) {
        if (!songTagsCurrent || preferenceManager.albumKeysVersion >= ALBUM_KEYS_VERSION) return
        migrate()
        preferenceManager.albumKeysVersion = ALBUM_KEYS_VERSION
    }

    internal suspend fun migrate() {
        val rekey = playEventDao.identityData().albumKeyRekey()
        var moved = 0
        var kept = 0
        listOf(PlayContext.TYPE_ALBUM, PlayContext.TYPE_ALBUM_ARTIST).forEach { type ->
            playEventDao.contextIds(type).forEach { id ->
                val context = PlayContext.decode(type, id).takeIf { it != PlayContext.None }
                val current = context?.let(rekey::context)
                when {
                    current == null -> {
                        kept++
                        logger.warn { "Kept a play history $type key no song has" }
                    }

                    current.id != id -> {
                        playEventDao.moveContext(type, id, current.id!!)
                        moved++
                    }
                }
            }
        }
        pinnedCollectionDao.ofType(PinnedCollectionData.CollectionType.Album).forEach { pinned ->
            val context = PlayContext.decode(PlayContext.TYPE_ALBUM, pinned.collectionId).takeIf { it != PlayContext.None }
            val current = context?.let(rekey::context)
            when {
                current == null -> {
                    kept++
                    logger.warn { "Kept a pinned album key no song has" }
                }

                current.id != pinned.collectionId -> {
                    pinnedCollectionDao.move(pinned.collectionType, pinned.collectionId, pinned.mediaProviderType, current.id!!)
                    moved++
                }
            }
        }
        logger.info { "Moved $moved stored album keys to the album identity rule, kept $kept" }
    }

    companion object {
        /** The album identity rule's (#637) keys. */
        const val ALBUM_KEYS_VERSION = 1
    }
}
