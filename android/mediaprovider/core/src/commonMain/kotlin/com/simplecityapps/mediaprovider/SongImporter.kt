package com.simplecityapps.mediaprovider

import com.simplecityapps.mediaprovider.MediaImporter.SongImportResult
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.lastOrNull

/**
 * How long each phase of one provider's import took, logged as the per-provider summary line at the end of an import
 * (#866). A phase that never ran (playlists not due, a fetch that failed) stays at zero.
 */
internal class ImportTimings {
    var findSongs: Duration = Duration.ZERO
    var dbWrite: Duration = Duration.ZERO
    var findPlaylists: Duration = Duration.ZERO
}

/** The song half of one provider's import: fetches its songs, works out what to add, change and remove, and stores that. */
internal class SongImporter(
    private val strings: MediaImportStrings,
    private val songRepository: SongRepository,
    private val preferenceManager: GeneralPreferenceManager,
    private val clock: Clock,
    /** Holds back the deletes of a full import that look like a source failing rather than shrinking. */
    private val deleteGuard: DeleteGuard
) {
    private val logger = Logger.tagged("MediaImporter")

    /**
     * Fetches [mediaProvider]'s songs as [requested] says and stores them: a full listing replaces what's stored, removing
     * what it no longer holds but for what [deleteGuard] holds back (a mass removal only if it isn't a [userRemoval], or all
     * of it for a listing that came up short); an incremental one is stored over it, or is made full when nothing is stored.
     * Once stored, the sync's start is noted for the next incremental sync to ask from, and a full sync's for the next full
     * one, unless the guard is waiting on another full pass after it: then that's the next sync. A [thorough] full listing of an
     * [IndexedMediaProvider] looks past its index.
     */
    fun import(
        mediaProvider: MediaProvider,
        requested: SyncPlan,
        timings: ImportTimings,
        userRemoval: Boolean,
        thorough: Boolean
    ): Flow<FlowEvent<SongImportResult, MessageProgress>> = flow {
        // Before the request, so whatever changes on the source while it runs is fetched again next time
        val start = clock.now()
        val storedSongs = songRepository.loadProviderSongs(mediaProvider.type)

        val existingSongs =
            try {
                remapLegacySongs(mediaProvider, storedSongs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Diffing without the remap would delete the songs it failed to move, and their history with them
                logger.error(e) { "Failed to remap legacy songs" }
                emit(FlowEvent.Failure(strings.importError))
                return@flow
            }

        // Nothing stored (a source signed into again, or a sync that never stored), so nothing for a delta to apply to
        val plan = if (storedSongs.isEmpty()) SyncPlan.Full else requested
        val songs =
            when (plan) {
                SyncPlan.Full -> if (thorough && mediaProvider is IndexedMediaProvider) mediaProvider.findSongsThoroughly(existingSongs) else mediaProvider.findSongs(existingSongs)
                is SyncPlan.Incremental -> (mediaProvider as IncrementalMediaProvider).findSongsChangedSince(existingSongs, plan.since)
            }
        val findSongsMark = TimeSource.Monotonic.markNow()
        songs.collect { event ->
            when (event) {
                is FlowEvent.Progress -> {
                    emit(FlowEvent.Progress<SongImportResult, MessageProgress>(event.data))
                }

                is FlowEvent.Success -> {
                    timings.findSongs = findSongsMark.elapsedNow()
                    val dbWriteMark = TimeSource.Monotonic.markNow()
                    try {
                        emit(FlowEvent.Progress<SongImportResult, MessageProgress>(MessageProgress(ImportPhase.Saving(event.result.size), null)))
                        val songDiff = SongDiff(existingSongs, event.result, deleteMissing = plan == SyncPlan.Full).apply()
                        // A remote listing pages by offset, so a shifted page can skip a song that's still there (#934)
                        val fullDeletes = if (plan == SyncPlan.Full) confirmedRemovals(mediaProvider, songDiff.deletes) else emptyList()
                        // An incremental sync's removals, read from the source's path listing when its count says it needs one
                        val removed = (plan as? SyncPlan.Incremental)?.let { songsRemovedOnSource(mediaProvider as IncrementalMediaProvider, existingSongs, songDiff.inserts) }
                        val guarded =
                            when {
                                plan == SyncPlan.Full -> guardDeletes(mediaProvider, existingSongs.size, event.result.size, fullDeletes, userRemoval, event.missing, fullPass = true)

                                removed != null -> guardDeletes(mediaProvider, existingSongs.size, existingSongs.size - removed.songs.size + songDiff.inserts.size, removed.songs, userRemoval, removed.missing, fullPass = false)

                                // Nothing listed, so nothing to remove, and what the last listing held back stays as it was
                                else -> DeleteGuard.Decision(apply = emptyList(), heldUnreadable = 0, heldMassRemoval = emptyList())
                            }
                        val result =
                            songRepository.insertUpdateAndDelete(
                                inserts = songDiff.inserts,
                                updates = songDiff.updates,
                                deletes = guarded.apply,
                                mediaProviderType = mediaProvider.type
                            )
                        timings.dbWrite = dbWriteMark.elapsedNow()
                        mediaProvider.songsStored()
                        preferenceManager.setLastSyncStart(mediaProvider.type.name, start)
                        if (plan == SyncPlan.Full) {
                            // A full pass tries every file it hasn't stored, so its count of files left unread is the whole of it (#840)
                            preferenceManager.setSkippedFiles(mediaProvider.type.name, mediaProvider.skippedFiles.size)
                            if (guarded.awaitsFullPass) {
                                // A held mass removal, or a listing that left songs out, waits on the next full sync, so that's
                                // the next sync rather than a week on. The songs it held weren't read, so the tags version stays
                                // as it was.
                                preferenceManager.setLastFullSyncStart(mediaProvider.type.name, null)
                            } else {
                                preferenceManager.setLastFullSyncStart(mediaProvider.type.name, start)
                                // Every song read again, so this source's songs hold every tag this build reads
                                preferenceManager.setSongTagsVersion(mediaProvider.type.name, MediaImporter.SONG_TAGS_VERSION)
                            }
                        } else if (guarded.awaitsFullPass || (removed?.unfetched ?: 0) > 0) {
                            // Likewise after an incremental sync, which also brings it forward for songs the source holds that
                            // it never fetched (unchanged since before they were in reach): until a full sync stores them, the
                            // count disagrees and every incremental sync would list the paths again. The tags version stays.
                            preferenceManager.setLastFullSyncStart(mediaProvider.type.name, null)
                        }
                        emit(
                            FlowEvent.Success(
                                SongImportResult(
                                    inserts = result.first,
                                    updates = result.second,
                                    deletes = result.third,
                                    mediaProviderType = mediaProvider.type
                                )
                            )
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to update song repository" }
                        emit(FlowEvent.Failure(strings.importError))
                    }
                }

                is FlowEvent.Failure -> {
                    timings.findSongs = findSongsMark.elapsedNow()
                    emit(event)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * What a source's path listing says of an incremental sync: the [songs] it no longer holds, how many songs short of what
     * it says it holds the listing came to ([missing]), and how many it holds that are neither stored nor were just fetched
     * ([unfetched]).
     */
    private class RemovedOnSource(val songs: List<Song>, val missing: Int, val unfetched: Int)

    /**
     * Which of [existingSongs] [mediaProvider] no longer holds, for an incremental sync, which can't tell from the songs it
     * fetched (#845), or null when it lists nothing. The [inserted] songs were new, so if the source's count is what's
     * stored plus those (plus the songs its last full listing came up short by, which its count includes), nothing has gone
     * and that's one request. Otherwise its path listing says: a failed one is null, and one that left songs out has
     * [missing][RemovedOnSource.missing] as many, which [DeleteGuard] holds the removal back for as it does a full listing's.
     * Songs missing from that listing are confirmed by a second, since a shifting page can skip one that's still there (#934).
     */
    private suspend fun songsRemovedOnSource(
        mediaProvider: IncrementalMediaProvider,
        existingSongs: List<Song>,
        inserted: List<Song>
    ): RemovedOnSource? {
        val expected = existingSongs.size + inserted.size + preferenceManager.listingShortfall(mediaProvider.type.name)
        if (mediaProvider.countSongs() == expected) return null
        val listing = mediaProvider.findSongPaths().lastOrNull()
        if (listing !is FlowEvent.Success) {
            logger.warn { "Couldn't list the songs ${mediaProvider.type} holds; keeping the ones it may have removed until it can be" }
            return null
        }
        val held = listing.result.toHashSet()
        // Offset pages shift when a song goes mid-listing, skipping another that's still there and totalling as if nothing
        // were skipped. So a song absent from one listing is removed only if a second one lacks it too.
        if (existingSongs.any { song -> song.path !in held }) {
            val confirmation = mediaProvider.findSongPaths().lastOrNull()
            if (confirmation !is FlowEvent.Success) {
                logger.warn { "Couldn't confirm the songs ${mediaProvider.type} no longer holds; keeping them until it can be" }
                return null
            }
            held += confirmation.result
        }
        val known = (existingSongs.asSequence() + inserted.asSequence()).map { song -> song.path }.toHashSet()
        val unfetched = held.count { path -> path !in known }
        if (unfetched > 0) {
            logger.warn { "${mediaProvider.type} holds $unfetched songs an incremental sync didn't fetch; the next sync is a full one" }
        }
        return RemovedOnSource(existingSongs.filter { song -> song.path !in held }, listing.missing, unfetched)
    }

    /**
     * Which of a full listing's [deletes] to go on with. A [PathListingMediaProvider]'s pages shift when a song goes
     * mid-listing, skipping another that's still there and totalling as if nothing were skipped (#934), so a song is
     * removed only if a second listing lacks it too; none is if that fails. Other sources' are as listed.
     */
    private suspend fun confirmedRemovals(
        mediaProvider: MediaProvider,
        deletes: List<Song>
    ): List<Song> {
        if (deletes.isEmpty() || mediaProvider !is PathListingMediaProvider) return deletes
        val confirmation = mediaProvider.findSongPaths().lastOrNull()
        if (confirmation !is FlowEvent.Success) {
            logger.warn { "Couldn't confirm the songs ${mediaProvider.type} no longer holds; keeping them until it can be" }
            return emptyList()
        }
        val held = confirmation.result.toHashSet()
        return deletes.filter { song -> song.path !in held }
    }

    /** Which of [deletes] [deleteGuard] lets [mediaProvider]'s import apply, logging what it held back. */
    private fun guardDeletes(
        mediaProvider: MediaProvider,
        existingCount: Int,
        foundCount: Int,
        deletes: List<Song>,
        userRemoval: Boolean,
        missing: Int,
        fullPass: Boolean
    ): DeleteGuard.Decision {
        val decision = deleteGuard.deletesToApply(mediaProvider.type, existingCount, foundCount, deletes, mediaProvider.unreadableRoots, userRemoval, missing, fullPass)
        if (missing > 0 && decision.listingComplete) {
            logger.info { "${mediaProvider.type} listed $missing fewer songs than it holds, as its last full import did; taking the listing as complete" }
        }
        if (!decision.listingComplete) {
            logger.warn { "${mediaProvider.type} listed fewer songs than it holds; keeping ${decision.heldIncomplete} it didn't list until a full import lists them all" }
        }
        if (decision.heldUnreadable > 0) {
            logger.info { "Keeping ${decision.heldUnreadable} ${mediaProvider.type} songs under roots it couldn't read: ${mediaProvider.unreadableRoots}" }
        }
        if (decision.heldMassRemoval.isNotEmpty()) {
            logger.warn {
                "${mediaProvider.type} found $foundCount songs, which would remove ${decision.heldMassRemoval.size + decision.apply.size} of " +
                    "its $existingCount; keeping ${decision.heldMassRemoval.size} until the next full import finds them gone too"
            }
        }
        return decision
    }

    /**
     * Moves songs the provider stored under an old identity to their current path before the diff, so the diff updates
     * those rows rather than deleting them and inserting new ones without their history.
     */
    private suspend fun remapLegacySongs(
        mediaProvider: MediaProvider,
        songs: List<Song>
    ): List<Song> {
        val remaps = mediaProvider.remapLegacySongs(songs)
        if (remaps.isEmpty()) return songs
        val paths = songRepository.remapPaths(remaps, mediaProvider.type).associate { remap -> remap.songId to remap.path }
        logger.info { "Moved ${paths.size} of ${remaps.size} matched ${mediaProvider.type} songs to their new paths" }
        return songs.map { song -> paths[song.id]?.let { path -> song.copy(path = path) } ?: song }
    }
}
