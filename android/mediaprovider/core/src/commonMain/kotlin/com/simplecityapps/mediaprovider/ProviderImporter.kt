package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.persistence.SourceReachability
import com.simplecityapps.shuttle.persistence.SourceStateStore
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** One source's import within a pass: its songs, then its playlists, and the status it leaves behind. */
internal class ProviderImporter(
    private val strings: MediaImportStrings,
    private val sourceState: SourceStateStore,
    private val clock: Clock,
    private val songImporter: SongImporter,
    private val playlistImporter: PlaylistImporter,
    private val playlistSync: ServerPlaylistSync?,
    private val states: ImportStateTracker
) {
    private val logger = Logger.tagged("MediaImporter")

    /** What the song pass left: the stored counts (null if it failed or stored nothing), and the failure it reported, if any. */
    private class SongPass(
        var stored: SongImportResult? = null,
        var failed: ProviderSyncOutcome.Failed? = null
    )

    /**
     * Fetches and stores [mediaProvider]'s songs as [plan] says, then its playlists if [playlistsDue] says so, given its plan
     * and its stored songs (null when that failed). Shows the fetch's progress only if [showProgress]. A fetch that stores
     * nothing new is reported only if [showProgress], which is what reloads what shows the library; one that fails is, unless
     * [quietFailures], which logs it and leaves the source's status as it was. [userRemoval] applies a mass removal of its
     * songs at once ([MediaImporter.import]); [thorough] has an [IndexedMediaProvider] look past its index. However it ends, its progress
     * doesn't outlast it. How it ended: a failure carries the exception it threw, if it did; cancelled, it's rethrown.
     */
    suspend fun import(
        mediaProvider: MediaProvider,
        plan: SyncPlan,
        timings: ImportTimings,
        showProgress: Boolean,
        quietFailures: Boolean,
        userRemoval: Boolean,
        thorough: Boolean,
        playlistsDue: (SyncPlan, SongImportResult?) -> Boolean
    ): ProviderSyncOutcome {
        val type = mediaProvider.type
        // What a cancelled import puts back: it didn't end, so it says nothing of how the source stands
        val before = states.stateOf(type)?.takeUnless { it is SongImportState.ImportProgress }
        var cancelled = false
        try {
            val pass = importSongs(mediaProvider, plan, timings, showProgress, quietFailures, userRemoval, thorough)
            if (playlistsDue(plan, pass.stored)) importPlaylists(mediaProvider, plan, timings, pass.stored)
            return pass.failed ?: ProviderSyncOutcome.Success
        } catch (e: CancellationException) {
            cancelled = true
            throw e
        } catch (e: Exception) {
            logger.error(e) { "$type import failed" }
            if (!quietFailures) {
                sourceState.setSourceReachability(type.name, SourceReachability(error = strings.importError, checkedAt = clock.now()))
                states.complete(type, strings.importError)
            }
            return ProviderSyncOutcome.Failed(strings.importError, cause = e)
        } finally {
            // The library doesn't stay scanning for it. Cancelled, it goes back to how it stood, rather than reading as a
            // success that reloads what shows the library; ended without saying how, it's done
            withContext(NonCancellable) { endProgress(mediaProvider, cancelled, before) }
        }
    }

    private suspend fun importSongs(
        mediaProvider: MediaProvider,
        plan: SyncPlan,
        timings: ImportTimings,
        showProgress: Boolean,
        quietFailures: Boolean,
        userRemoval: Boolean,
        thorough: Boolean
    ): SongPass {
        val type = mediaProvider.type
        val pass = SongPass()
        with(states) {
            if (showProgress) {
                publish(type, mediaProvider.importProgress(MessageProgress(if (type.remote) ImportPhase.Connecting else ImportPhase.Fetching, progress = null)))
            }
            songImporter.import(mediaProvider, plan, timings, userRemoval, thorough).collect { event ->
                when (event) {
                    is FlowEvent.Progress -> {
                        if (showProgress) publish(type, mediaProvider.importProgress(event.data))
                    }

                    is FlowEvent.Success -> {
                        pass.stored = event.result
                        onSongsStored(mediaProvider, event.result, showProgress)
                    }

                    is FlowEvent.Failure -> {
                        pass.failed = ProviderSyncOutcome.Failed(event.message ?: strings.importError)
                        onSongsFailed(mediaProvider, event.message, quietFailures)
                    }
                }
            }
        }
        return pass
    }

    private suspend fun onSongsStored(
        mediaProvider: MediaProvider,
        result: SongImportResult,
        showProgress: Boolean
    ) {
        val type = mediaProvider.type
        val completedAt = clock.now()
        sourceState.setSourceReachability(type.name, SourceReachability(error = null, checkedAt = completedAt))
        sourceState.setSourceUpdated(type.name, completedAt)
        val changed = result.inserts + result.updates + result.deletes > 0
        // A quiet sync that stored nothing stays silent, unless it clears an earlier failure.
        val clearsError = (states.stateOf(type) as? SongImportState.ImportComplete)?.error != null
        if (showProgress || changed || clearsError) {
            states.complete(type, error = null)
        }
    }

    private suspend fun onSongsFailed(
        mediaProvider: MediaProvider,
        message: String?,
        quietFailures: Boolean
    ) {
        val type = mediaProvider.type
        if (quietFailures) {
            logger.warn { "$type sync failed, leaving its status as it was: $message" }
        } else {
            sourceState.setSourceReachability(type.name, SourceReachability(error = message, checkedAt = clock.now()))
            states.complete(type, message)
        }
    }

    private suspend fun importPlaylists(
        mediaProvider: MediaProvider,
        plan: SyncPlan,
        timings: ImportTimings,
        stored: SongImportResult?
    ) {
        val type = mediaProvider.type
        // A full sync, or one that added songs a playlist might now match, reads every playlist again
        val reuseVersions = plan != SyncPlan.Full && stored != null && stored.inserts == 0
        val import = suspend {
            playlistImporter.import(mediaProvider, timings, songsStored = stored != null, reuseVersions = reuseVersions).collect { event ->
                if (event is FlowEvent.Failure) logger.warn { "$type playlist import failed: ${event.message}" }
            }
        }
        playlistSync?.withEditsSent(type) { import() } ?: import()
    }

    private suspend fun endProgress(
        mediaProvider: MediaProvider,
        cancelled: Boolean,
        before: SongImportState?
    ) {
        val type = mediaProvider.type
        if (states.stateOf(type) is SongImportState.ImportProgress) {
            when {
                !cancelled -> states.complete(type, error = null)
                before == null -> states.remove(type)
                else -> states.updateStates { current -> current + (type to before) }
            }
        }
    }
}
