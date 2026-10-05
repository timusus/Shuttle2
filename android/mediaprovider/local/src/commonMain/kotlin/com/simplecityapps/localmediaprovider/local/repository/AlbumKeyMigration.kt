package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.PinnedCollectionDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.PlayEventDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.ResumePointDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.SongDataDao
import com.simplecityapps.localmediaprovider.local.data.room.entity.PinnedCollectionData
import com.simplecityapps.localmediaprovider.local.provider.splitArtistTag
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.model.AlbumIndex
import com.simplecityapps.shuttle.model.AlbumKeyChange
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager

/**
 * Moves the stored album and album artist keys (the play history's album and album artist contexts, the resume points and
 * the pinned albums) when the keys songs have change, once per change ([ALBUM_KEYS_VERSION]):
 * 1. [moveToIdentityRule]: the keys stored before the album identity rule (#637).
 * 2. [splitLocalArtists]: the local songs stored before #880 split a multi-artist ARTIST tag.
 *
 * It runs after the first import that leaves every source's tags current (the MusicBrainz and server album ids the rule
 * reads arrive with that import). A key it can't map (no song had it, or it doesn't read back) is kept as it is and
 * logged. Each key moves in one statement and a key already moved maps to itself, so a run cut short (the process killed
 * part way) just finishes on the next one.
 *
 * Known limit: an album whose songs were all re-split already (by a file re-read since #880) keeps its stale "A / B" key,
 * as no unsplit song is left to derive the mapping from ([AlbumKeyChange]).
 */
class AlbumKeyMigration(
    private val songDataDao: SongDataDao,
    private val playEventDao: PlayEventDao,
    private val resumePointDao: ResumePointDao,
    private val pinnedCollectionDao: PinnedCollectionDao,
    private val preferenceManager: GeneralPreferenceManager
) {
    private val logger = Logger.tagged("AlbumKeyMigration")

    /** Runs each move that's due: not yet run, and [songTagsCurrent], every source's songs holding every tag read. */
    suspend fun migrateIfDue(songTagsCurrent: Boolean) {
        if (!songTagsCurrent) return
        if (preferenceManager.albumKeysVersion < IDENTITY_RULE_KEYS) {
            moveToIdentityRule()
            preferenceManager.albumKeysVersion = IDENTITY_RULE_KEYS
        }
        if (preferenceManager.albumKeysVersion < SPLIT_ARTIST_KEYS) {
            splitLocalArtists()
            preferenceManager.albumKeysVersion = SPLIT_ARTIST_KEYS
        }
    }

    /** Moves each key stored before the album identity rule (#637) through [com.simplecityapps.shuttle.model.AlbumKeyRekey]. */
    internal suspend fun moveToIdentityRule() {
        // Read fresh, not from the shared index: the import that made this due wrote a moment ago, and Room may not have
        // told the index yet
        val rekey = AlbumIndex(songDataDao.identityData().map { it.toTags() }).rekey
        moveKeys("to the album identity rule", rekey::context)
    }

    /**
     * Splits the artists of the local songs stored before #880 as a read of their files does now ([splitArtistTag]), and
     * moves the keys to follow: a song with no album artist tag whose ARTIST reads "A / B" belongs to A's album now, not
     * "A / B"'s. An import keeps an unchanged file's stored tags, so without this its song would only split once the
     * file changes. The keys move first, matched by song before and after the split ([AlbumKeyChange]), then the
     * artists: a run cut short before the artists are written finds the keys moved already.
     */
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
        /** The album identity rule's (#637) keys. */
        const val IDENTITY_RULE_KEYS = 1

        /** The keys of the local songs' split artists (#880). */
        const val SPLIT_ARTIST_KEYS = 2

        const val ALBUM_KEYS_VERSION = SPLIT_ARTIST_KEYS
    }
}
