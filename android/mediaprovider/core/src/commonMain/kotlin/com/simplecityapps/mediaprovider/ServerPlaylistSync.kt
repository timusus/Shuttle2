package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** An edit made in S2 to a server's playlist, naming the playlist by its external id and the server's songs in it by path. */
sealed interface PlaylistEdit {
    val playlistId: String

    /** An edit to the playlist's songs: one with no [songPaths] has nothing to send. */
    sealed interface Songs : PlaylistEdit {
        val songPaths: List<String>
    }

    /** The songs were added to the end of the playlist. */
    data class Add(
        override val playlistId: String,
        override val songPaths: List<String>
    ) : Songs

    /** One entry of each song was removed from the playlist, or every entry of each when [everyEntry]. */
    data class Remove(
        override val playlistId: String,
        override val songPaths: List<String>,
        val everyEntry: Boolean = false
    ) : Songs

    /** The playlist was put in a new order: [songPaths] is every one of the server's songs it holds, in that order. */
    data class Reorder(
        override val playlistId: String,
        override val songPaths: List<String>
    ) : Songs

    /** The playlist was given the title [name]. */
    data class Rename(
        override val playlistId: String,
        val name: String
    ) : PlaylistEdit

    /** The playlist was deleted. */
    data class Delete(
        override val playlistId: String
    ) : PlaylistEdit
}

/**
 * Sends the edits made in S2 to playlists imported from a media server back to that server (#916). The local edit has already
 * been made; each one is queued here, persisted so an edit made offline or just before the app is closed is still sent, and the
 * queue is sent in order, oldest first, straight away and again before the next import of that server's playlists
 * ([withEditsSent]). An edit that fails stays queued, with everything after it, for the next try; one the server refuses is
 * dropped, and so is every edit to a playlist the server no longer has.
 *
 * Once the server has an edit, the songs the playlist last held on the server (the snapshot the import reconciles against,
 * #843) change with it, so the next import treats a song added in S2 as the server's own: it isn't added twice, and it's removed
 * if someone later removes it on the server.
 *
 * Only the songs of the playlist's own server go into an edit: the server can't hold a song from anywhere else, so such a song
 * stays in the playlist in S2 alone, as before.
 *
 * Deleting a playlist drops the edits to it still queued, and once the server has deleted it, so do the songs it last held there.
 * A server that refuses to delete it (a playlist the user doesn't own) keeps it, so the next import brings it back.
 */
class ServerPlaylistSync(
    writers: Set<ServerPlaylistWriter>,
    private val preferenceManager: GeneralPreferenceManager,
    private val scope: CoroutineScope
) {
    private val writers = writers.associateBy { writer -> writer.type }

    /** Guards each read-modify-write of the queue: edits are added while it's being sent. */
    private val queueLock = Mutex()

    /** One sender per server at a time, so edits go in order; an import holds it while it reads that server's playlists. */
    private val sendLocks = this.writers.mapValues { Mutex() }

    fun handles(type: MediaProviderType): Boolean = type in writers

    /** Queues [edit] to [type]'s playlist and starts sending the queue. */
    suspend fun enqueue(
        type: MediaProviderType,
        edit: PlaylistEdit
    ) {
        if (!handles(type) || (edit is PlaylistEdit.Songs && edit.songPaths.isEmpty())) {
            return
        }
        queueLock.withLock {
            val queued = pending(type)
            // Nothing queued for a playlist about to be deleted needs sending
            save(type, if (edit is PlaylistEdit.Delete) queued.filterNot { it.playlistId == edit.playlistId } + edit else queued + edit)
        }
        scope.launch { send(type) }
    }

    /** Sends [type]'s queued edits, in order, until one fails. */
    suspend fun send(type: MediaProviderType) {
        sendLocks[type]?.withLock { sendQueued(type) }
    }

    /**
     * Sends [type]'s queued edits, then runs [block] (the import of its playlists) before any more are sent, so the import reads
     * what the server holds after them and the snapshot it saves can't race a send.
     */
    suspend fun <T> withEditsSent(
        type: MediaProviderType,
        block: suspend () -> T
    ): T {
        val lock = sendLocks[type] ?: return block()
        return lock.withLock {
            sendQueued(type)
            block()
        }
    }

    /**
     * The playlists of [type] with edits still to send. The import leaves them as they are in S2: the server doesn't hold those
     * edits yet, so reading its playlist now would undo them.
     */
    suspend fun pendingPlaylistIds(type: MediaProviderType): Set<String> = queueLock.withLock { pending(type) }.mapTo(mutableSetOf()) { edit -> edit.playlistId }

    private suspend fun sendQueued(type: MediaProviderType) {
        val writer = writers[type] ?: return
        while (true) {
            // Only this sender removes edits, and new ones go on the end, so the head is still this edit when it's dropped
            val edit = queueLock.withLock { pending(type).firstOrNull() } ?: return
            when (val result = writer.write(edit)) {
                is PlaylistWriteResult.Success -> {
                    updateServerSongs(type, edit.playlistId) { songs -> songs.after(edit, result.value) }
                    queueLock.withLock { save(type, pending(type).drop(1)) }
                }

                PlaylistWriteResult.Failed -> {
                    logger.debug { "Couldn't send an edit to $type playlist ${edit.playlistId}; keeping it queued" }
                    return
                }

                PlaylistWriteResult.PlaylistGone -> {
                    logger.debug { "$type playlist ${edit.playlistId} is gone; dropping its edits" }
                    queueLock.withLock { save(type, pending(type).filterNot { queued -> queued.playlistId == edit.playlistId }) }
                }

                PlaylistWriteResult.Refused -> {
                    logger.debug { "$type refused an edit to playlist ${edit.playlistId}; dropping it" }
                    queueLock.withLock { save(type, pending(type).drop(1)) }
                }
            }
        }
    }

    /** Makes [edit] on the server; a success holds the paths of the songs it removed, if any. */
    private suspend fun ServerPlaylistWriter.write(edit: PlaylistEdit): PlaylistWriteResult<List<String>> = when (edit) {
        is PlaylistEdit.Add -> add(edit.playlistId, edit.songPaths).then { PlaylistWriteResult.Success(emptyList()) }

        is PlaylistEdit.Remove -> entries(edit.playlistId).then { entries ->
            val removed = entries.toRemove(edit)
            if (removed.isEmpty()) {
                PlaylistWriteResult.Success(emptyList())
            } else {
                remove(edit.playlistId, removed.map { entry -> entry.entryId }).then { PlaylistWriteResult.Success(removed.map { entry -> entry.songPath }) }
            }
        }

        is PlaylistEdit.Reorder -> entries(edit.playlistId).then { entries -> reorder(edit.playlistId, entries, entries.reordered(edit.songPaths)) }

        is PlaylistEdit.Rename -> rename(edit.playlistId, edit.name).then { PlaylistWriteResult.Success(emptyList()) }

        is PlaylistEdit.Delete -> delete(edit.playlistId).then { PlaylistWriteResult.Success(emptyList()) }
    }

    /** Moves the entries from [current] into [target]'s order, one entry at a time, front to back. */
    private suspend fun ServerPlaylistWriter.reorder(
        playlistId: String,
        current: List<ServerPlaylistEntry>,
        target: List<ServerPlaylistEntry>
    ): PlaylistWriteResult<List<String>> {
        val order = current.toMutableList()
        target.forEachIndexed { index, entry ->
            if (order[index] != entry) {
                val moved = move(playlistId, entry.entryId, index, after = target.getOrNull(index - 1)?.entryId)
                if (moved !is PlaylistWriteResult.Success) {
                    return moved.then { PlaylistWriteResult.Success(emptyList()) }
                }
                order.remove(entry)
                order.add(index, entry)
            }
        }
        return PlaylistWriteResult.Success(emptyList())
    }

    /** Sets the songs [playlistId] holds on the server to [update] of those it held: none at all, once it's deleted (null). */
    private fun updateServerSongs(
        type: MediaProviderType,
        playlistId: String,
        update: (List<String>) -> List<String>?
    ) {
        val songs = preferenceManager.playlistServerSongs(type.name)
        val updated = update(songs[playlistId].orEmpty())
        preferenceManager.setPlaylistServerSongs(type.name, if (updated == null) songs - playlistId else songs + (playlistId to updated))
    }

    private fun pending(type: MediaProviderType): List<PlaylistEdit> = preferenceManager.pendingPlaylistEdits(type.name)
        ?.lineSequence()
        ?.mapNotNull { line -> line.decodeEdit() }
        ?.toList()
        .orEmpty()

    private fun save(
        type: MediaProviderType,
        edits: List<PlaylistEdit>
    ) = preferenceManager.setPendingPlaylistEdits(type.name, edits.joinToString("\n") { edit -> edit.encode() })
}

/** The entries [edit] removes: the first not yet removed for each of its songs, or every one of them when it removes every entry. */
internal fun List<ServerPlaylistEntry>.toRemove(edit: PlaylistEdit.Remove): List<ServerPlaylistEntry> {
    if (edit.everyEntry) {
        val paths = edit.songPaths.toSet()
        return filter { entry -> entry.songPath in paths }
    }
    val remaining = toMutableList()
    return edit.songPaths.mapNotNull { path -> remaining.firstOrNull { entry -> entry.songPath == path }?.also { entry -> remaining.remove(entry) } }
}

/**
 * These entries in the order [songPaths] gives the songs it names: the n-th entry of a song takes the n-th place [songPaths]
 * gives that song, among the places those entries held. An entry [songPaths] doesn't name (a song added on the server since, or
 * one S2 can't play) keeps its place.
 */
internal fun List<ServerPlaylistEntry>.reordered(songPaths: List<String>): List<ServerPlaylistEntry> {
    val byOccurrence = withOccurrences().associate { (entry, occurrence) -> (entry.songPath to occurrence) to entry }
    val ordered = songPaths.occurrences().mapNotNull { key -> byOccurrence[key] }
    val placed = ordered.toSet()
    val slots = indices.filter { index -> this[index] in placed }
    return toMutableList().also { result -> slots.zip(ordered).forEach { (slot, entry) -> result[slot] = entry } }
}

private fun List<ServerPlaylistEntry>.withOccurrences(): List<Pair<ServerPlaylistEntry, Int>> {
    val seen = mutableMapOf<String, Int>()
    return map { entry -> entry to seen.getOrElse(entry.songPath) { 0 }.also { seen[entry.songPath] = it + 1 } }
}

private fun List<String>.occurrences(): List<Pair<String, Int>> {
    val seen = mutableMapOf<String, Int>()
    return map { path -> path to seen.getOrElse(path) { 0 }.also { seen[path] = it + 1 } }
}

/** The songs a playlist holds on the server once it has [edit], given the [removed] songs' paths: null once it's deleted. */
private fun List<String>.after(
    edit: PlaylistEdit,
    removed: List<String>
): List<String>? = when (edit) {
    is PlaylistEdit.Add -> this + edit.songPaths
    is PlaylistEdit.Remove -> toMutableList().apply { removed.forEach { path -> remove(path) } }
    is PlaylistEdit.Reorder, is PlaylistEdit.Rename -> this
    is PlaylistEdit.Delete -> null
}

/** One line per edit, its fields separated by tabs: a name can't hold either, so its tabs and line breaks become spaces. */
private fun PlaylistEdit.encode(): String {
    val (kind, fields) = when (this) {
        is PlaylistEdit.Add -> ADD to songPaths
        is PlaylistEdit.Remove -> (if (everyEntry) REMOVE_EVERY else REMOVE) to songPaths
        is PlaylistEdit.Reorder -> REORDER to songPaths
        is PlaylistEdit.Rename -> RENAME to listOf(name.replace(Regex("[\\t\\r\\n]"), " "))
        is PlaylistEdit.Delete -> DELETE to emptyList()
    }
    return (listOf(kind, playlistId) + fields).joinToString("\t")
}

private fun String.decodeEdit(): PlaylistEdit? {
    val fields = split('\t')
    if (fields.size < 2) {
        return null
    }
    val (kind, playlistId) = fields
    val paths = fields.drop(2)
    return when (kind) {
        ADD -> PlaylistEdit.Add(playlistId, paths)
        REMOVE -> PlaylistEdit.Remove(playlistId, paths)
        REMOVE_EVERY -> PlaylistEdit.Remove(playlistId, paths, everyEntry = true)
        REORDER -> PlaylistEdit.Reorder(playlistId, paths)
        RENAME -> paths.singleOrNull()?.let { name -> PlaylistEdit.Rename(playlistId, name) }
        DELETE -> PlaylistEdit.Delete(playlistId)
        else -> null
    }
}

private const val ADD = "add"
private const val REMOVE = "remove"
private const val REMOVE_EVERY = "remove-every"
private const val REORDER = "reorder"
private const val RENAME = "rename"
private const val DELETE = "delete"

private val logger = Logger.tagged("ServerPlaylistSync")
