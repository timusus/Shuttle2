package com.simplecityapps.mediaprovider

import androidx.annotation.VisibleForTesting
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MediaImporter(
    private val strings: MediaImportStrings,
    private val songRepository: SongRepository,
    private val playlistStore: ImportedPlaylistStore,
    private val preferenceManager: GeneralPreferenceManager,
    /**
     * Runs after each import, told whether every source's songs now hold every tag this build reads: what moving the
     * stored album keys to the album identity rule (#637) waits for.
     */
    private val afterImport: suspend (songTagsCurrent: Boolean) -> Unit,
    /** What each source's sync start is taken from: a fake in tests. */
    private val clock: Clock = Clock.System
) : SongImportStateProvider {
    private val logger = Logger.tagged("MediaImporter")

    /** Held for the length of an import, so a second [import] finds it taken and returns rather than scanning again. */
    private val importLock = Mutex()

    /** Set by every [import] before it tries [importLock], so an import already running runs one more pass once it's done. */
    private val rescanRequested = AtomicBoolean(false)

    /** Set by an [import] the user's folder change asked for, before [rescanRequested], and taken by the pass that runs it. */
    private val foldersChangedRequested = AtomicBoolean(false)

    /** Test seam: runs after the last pass has found no request pending, just before [importLock] is released. */
    @VisibleForTesting
    internal var beforeUnlock: (suspend () -> Unit)? = null

    val isImporting: Boolean get() = importLock.isLocked

    /** Held while [_providerImportStates] and [_songImportState] change, so the overall state is always that of the latest states. */
    private val stateLock = Mutex()

    private val _songImportState = MutableStateFlow<SongImportState>(SongImportState.Idle)

    /** The running import's progress, or how the last one ended, so a collector that arrives mid-import sees where it's at. */
    override val songImportState: StateFlow<SongImportState> = _songImportState.asStateFlow()

    private val _providerImportStates = MutableStateFlow<Map<MediaProviderType, SongImportState>>(emptyMap())

    override val providerImportStates: StateFlow<Map<MediaProviderType, SongImportState>> = _providerImportStates.asStateFlow()

    private val _importsCompleted = MutableStateFlow(0)

    override val importsCompleted: StateFlow<Int> = _importsCompleted.asStateFlow()

    private val providers = CopyOnWriteSet<MediaProvider>()

    /**
     * The sources an import reads. Safe to change from any thread while one runs, which reads them as they were when it
     * started; a source removed for good goes through [removeProvider], which stops its running import too.
     */
    val mediaProviders: MutableSet<MediaProvider> get() = providers

    /** Each source's import in the running pass, so [removeProvider] can stop it. Held under [providerJobsLock]. */
    private val providerJobs = mutableMapOf<MediaProviderType, Job>()

    private val providerJobsLock = Mutex()

    /** Holds back the deletes of a full import that look like a source failing rather than shrinking. */
    private val deleteGuard = DeleteGuard(preferenceManager)

    var importCount: Int = 0

    /**
     * Whether a source's songs were imported before this build's [SONG_TAGS_VERSION], so they lack tags it reads, and no
     * import has run under this version yet: the one launch re-import is due. A full import is how a provider brings its
     * songs' tags up to date, however it decides which songs to read again, and an import updates songs in place by path,
     * so a re-import fills them in without changing an id. A source whose import fails stays outdated, and catches
     * up on its own next import rather than by importing everything again at each launch.
     */
    val songTagsOutdated: Boolean
        get() = preferenceManager.songTagsRescanVersion < SONG_TAGS_VERSION && providers.snapshot.any { preferenceManager.songTagsOutdated(it.type) }

    /**
     * Reads every source in full, as the user asked (a rescan, a change of folders, the first import): an
     * [IndexedMediaProvider] looks in every folder rather than trusting its index alone. [foldersChanged] says the user just
     * changed which of this device's folders are read, so a mass removal of this device's songs is theirs and applies at
     * once rather than waiting on the next import.
     */
    suspend fun import(foldersChanged: Boolean = false) {
        if (providers.snapshot.isEmpty()) {
            logger.debug { "Import failed, media providers empty" }
            return
        }

        if (foldersChanged) foldersChangedRequested.store(true)
        rescanRequested.store(true)
        runRequestedImports()?.let { throw it }
    }

    /**
     * Brings each source up to date as [SyncPolicy] says for [trigger] (#771): only what changed on a server since its last
     * sync, all of it now and then, and nothing on return to the app for a source synced in the last few minutes. Quiet,
     * unlike [import]: it shows no progress, which would replace the library with the scanning state, only that a source
     * changed or (but for a return to the app, which finds a server offline too often to say so) failed. An import or sync
     * already running makes it return at once; an [import] asked for while it runs follows it. Nothing is synced before the
     * library's first import, or while one is due for new tags: the launch asks for that import itself, and a sync that got
     * the lock first would only read every source in full a second time.
     */
    suspend fun sync(trigger: SyncTrigger) {
        if (providers.snapshot.isEmpty()) return
        if (preferenceManager.lastMediaImportDate == null || songTagsOutdated) {
            logger.debug { "A full import is due, skipping the $trigger sync" }
            return
        }

        if (!importLock.tryLock()) {
            logger.debug { "Import already in progress, skipping the $trigger sync" }
            return
        }
        try {
            syncAll(trigger)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Sync failed" }
        } finally {
            importLock.unlock()
        }
        // An import asked for while this ran found the lock held and left its request for whoever unlocks next; it logs its
        // own failure
        runRequestedImports()
    }

    /** Runs a full import for as long as one is requested and no other import holds [importLock]; the last pass's failure. */
    private suspend fun runRequestedImports(): Exception? {
        var failure: Exception? = null
        // Checked again after each unlock: a request made between the last check and the unlock found the lock still held,
        // so whichever import sees it next runs it
        while (rescanRequested.load()) {
            if (!importLock.tryLock()) {
                logger.debug { "Import already in progress, requesting a follow-up pass" }
                break
            }
            try {
                while (rescanRequested.exchange(false)) {
                    failure =
                        try {
                            importAll(foldersChanged = foldersChangedRequested.exchange(false))
                            null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // Only a request made during this pass runs another, so a failing import doesn't loop
                            logger.error(e) { "Import failed" }
                            e
                        }
                }
                beforeUnlock?.invoke()
            } finally {
                importLock.unlock()
            }
        }
        return failure
    }

    private suspend fun importAll(foldersChanged: Boolean) {
        logger.debug { "Starting import.." }
        preferenceManager.songTagsRescanVersion = SONG_TAGS_VERSION
        importProviders(
            providers.snapshot.map { mediaProvider -> mediaProvider to SyncPlan.Full },
            showProgress = true,
            quietFailures = false,
            foldersChanged = foldersChanged,
            thorough = true
        ) { _, _ -> true }
    }

    private suspend fun syncAll(trigger: SyncTrigger) {
        val now = clock.now()
        val plans =
            providers.snapshot.mapNotNull { mediaProvider ->
                SyncPolicy.plan(
                    trigger = trigger,
                    incremental = mediaProvider is IncrementalMediaProvider,
                    lastSyncStart = preferenceManager.lastSyncStart(mediaProvider.type.name),
                    lastFullSyncStart = preferenceManager.lastFullSyncStart(mediaProvider.type.name),
                    songTagsOutdated = preferenceManager.songTagsOutdated(mediaProvider.type),
                    now = now
                )?.let { plan -> mediaProvider to plan }
            }
        if (plans.isEmpty()) {
            logger.debug { "Every source synced recently, skipping the $trigger sync" }
            return
        }
        logger.debug { "Starting $trigger sync: ${plans.joinToString { (mediaProvider, plan) -> "${mediaProvider.type} $plan" }}" }
        // A delta that changed nothing leaves the playlists as they were, bar the daily sync, which catches a playlist
        // edited on the server without touching its songs
        importProviders(plans, showProgress = false, quietFailures = trigger == SyncTrigger.Foreground, foldersChanged = false, thorough = false) { plan, result ->
            result != null && (plan == SyncPlan.Full || trigger == SyncTrigger.Periodic || result.inserts + result.updates > 0)
        }
    }

    /**
     * Imports each provider in [plans], all at once and each on its own, so one failing or removed ([removeProvider]) leaves
     * the others to finish. Once they all have, records the import if any of them finished, then throws the first provider's
     * exception. See [importProvider]. [thorough] has an [IndexedMediaProvider] look past its index.
     */
    private suspend fun importProviders(
        plans: List<Pair<MediaProvider, SyncPlan>>,
        showProgress: Boolean,
        quietFailures: Boolean,
        foldersChanged: Boolean,
        thorough: Boolean,
        playlistsDue: (SyncPlan, SongImportResult?) -> Boolean
    ) {
        val time = TimeSource.Monotonic.markNow()

        // Each provider's timings, filled in by its own song and playlist passes, so the summary line below can attribute a slow import (#866)
        val timingsByProvider = plans.associate { (mediaProvider, _) -> mediaProvider.type to ImportTimings() }

        val failures = mutableListOf<Exception>()
        var finished = 0
        try {
            withContext(Dispatchers.IO) {
                supervisorScope {
                    plans.mapNotNull { (mediaProvider, plan) ->
                        val job =
                            launch(start = CoroutineStart.LAZY) {
                                val failure = importProvider(mediaProvider, plan, timingsByProvider.getValue(mediaProvider.type), showProgress, quietFailures, userRemoval = foldersChanged && !mediaProvider.type.remote, thorough = thorough, playlistsDue = playlistsDue)
                                providerJobsLock.withLock { if (failure != null) failures += failure else finished++ }
                            }
                        job.takeIf { track(mediaProvider, job) }?.apply { start() }
                    }.joinAll()
                }
            }
        } finally {
            withContext(NonCancellable) { providerJobsLock.withLock { providerJobs.clear() } }
        }

        // What the sources that finished stored counts as imported, though another threw: a source that failed stays
        // outdated, so afterImport waits on it all the same
        var afterImportTime = Duration.ZERO
        if (finished > 0) {
            preferenceManager.lastMediaImportDate = clock.now()
            importCount++

            val afterImportMark = TimeSource.Monotonic.markNow()
            afterImport(providers.snapshot.none { preferenceManager.songTagsOutdated(it.type) })
            afterImportTime = afterImportMark.elapsedNow()
        }
        failures.firstOrNull()?.let { throw it }

        // One line per provider per run, so a slow import can be attributed to its phases (#866)
        timingsByProvider.forEach { (type, timings) ->
            logger.debug {
                "$type import phases: findSongs ${timings.findSongs.inWholeMilliseconds}ms, " +
                    "song diff and db write ${timings.dbWrite.inWholeMilliseconds}ms, " +
                    "findPlaylists ${timings.findPlaylists.inWholeMilliseconds}ms"
            }
        }
        logger.debug { "Import complete in ${time.elapsedNow().inWholeMilliseconds}ms (afterImport ${afterImportTime.inWholeMilliseconds}ms)" }
    }

    /**
     * Records [job] as [mediaProvider]'s import, so [removeProvider] can stop it, unless the provider was removed since the
     * pass read the sources: then cancels it, and it never runs.
     */
    private suspend fun track(
        mediaProvider: MediaProvider,
        job: Job
    ): Boolean = providerJobsLock.withLock {
        (mediaProvider in providers.snapshot).also { tracked -> if (tracked) providerJobs[mediaProvider.type] = job else job.cancel() }
    }

    /**
     * Fetches and stores [mediaProvider]'s songs as [plan] says, then its playlists if [playlistsDue] says so, given its plan
     * and its stored songs (null when that failed). Shows the fetch's progress only if [showProgress]. A fetch that stores
     * nothing new is reported only if [showProgress], which is what reloads what shows the library; one that fails is, unless
     * [quietFailures], which logs it and leaves the source's status as it was. [userRemoval] applies a mass removal of its
     * songs at once ([import]); [thorough] has an [IndexedMediaProvider] look past its index. However it ends, its progress
     * doesn't outlast it. The exception it threw, if it did.
     */
    private suspend fun importProvider(
        mediaProvider: MediaProvider,
        plan: SyncPlan,
        timings: ImportTimings,
        showProgress: Boolean,
        quietFailures: Boolean,
        userRemoval: Boolean,
        thorough: Boolean,
        playlistsDue: (SyncPlan, SongImportResult?) -> Boolean
    ): Exception? {
        val type = mediaProvider.type
        // What a cancelled import puts back: it didn't end, so it says nothing of how the source stands
        val before = _providerImportStates.value[type]?.takeUnless { it is SongImportState.ImportProgress }
        var cancelled = false
        try {
            if (showProgress) {
                publish(type, mediaProvider.importProgress(MessageProgress(if (type.remote) ImportPhase.Connecting else ImportPhase.Fetching, progress = null)))
            }
            var stored: SongImportResult? = null
            importSongs(mediaProvider, plan, timings, userRemoval, thorough).collect { event ->
                when (event) {
                    is FlowEvent.Progress -> {
                        if (showProgress) publish(type, mediaProvider.importProgress(event.data))
                    }

                    is FlowEvent.Success -> {
                        stored = event.result
                        val changed = event.result.inserts + event.result.updates + event.result.deletes > 0
                        // A quiet sync that stored nothing stays silent, unless it clears an earlier failure.
                        val clearsError = (_providerImportStates.value[type] as? SongImportState.ImportComplete)?.error != null
                        if (showProgress || changed || clearsError) {
                            complete(type, error = null)
                        }
                    }

                    is FlowEvent.Failure -> {
                        if (quietFailures) {
                            logger.warn { "$type sync failed, leaving its status as it was: ${event.message}" }
                        } else {
                            complete(type, event.message)
                        }
                    }
                }
            }
            if (playlistsDue(plan, stored)) {
                importPlaylists(mediaProvider, timings).collect { event ->
                    if (event is FlowEvent.Failure) logger.warn { "$type playlist import failed: ${event.message}" }
                }
            }
            return null
        } catch (e: CancellationException) {
            cancelled = true
            throw e
        } catch (e: Exception) {
            logger.error(e) { "$type import failed" }
            if (!quietFailures) complete(type, strings.importError)
            return e
        } finally {
            // The library doesn't stay scanning for it. Cancelled, it goes back to how it stood, rather than reading as a
            // success that reloads what shows the library; ended without saying how, it's done
            withContext(NonCancellable) {
                if (_providerImportStates.value[type] is SongImportState.ImportProgress) {
                    when {
                        !cancelled -> complete(type, error = null)
                        before == null -> updateStates { states -> states - type }
                        else -> updateStates { states -> states + (type to before) }
                    }
                }
            }
        }
    }

    /**
     * Stops reading [mediaProvider]: takes it out of the sources and cancels its running import, returning once that has
     * ended, so nothing it read is stored after this returns. Its import state goes with it.
     */
    suspend fun removeProvider(mediaProvider: MediaProvider) {
        // Taken out first: a pass that read the sources before this sees it gone when it tracks its job ([track])
        providers -= mediaProvider
        providerJobsLock.withLock { providerJobs[mediaProvider.type] }?.cancelAndJoin()
        updateStates { states -> states - mediaProvider.type }
    }

    private suspend fun publish(
        type: MediaProviderType,
        state: SongImportState
    ) = updateStates { states -> states + (type to state) }

    /** Reports how [type]'s import ended, [error] if it failed, and counts it in [importsCompleted]. */
    private suspend fun complete(
        type: MediaProviderType,
        error: String?
    ) = updateStates(completed = true) { states -> states + (type to SongImportState.ImportComplete(type, error)) }

    /** Swaps in [transform] of each provider's state, and the overall state they come to ([overallImportState]), counting one more of [importsCompleted] if [completed]. */
    private suspend fun updateStates(
        completed: Boolean = false,
        transform: (Map<MediaProviderType, SongImportState>) -> Map<MediaProviderType, SongImportState>
    ) {
        stateLock.withLock {
            val states = transform(_providerImportStates.value)
            _providerImportStates.value = states
            _songImportState.value = overallImportState(states)
            if (completed) _importsCompleted.value++
        }
    }

    /** [progress] as the import state shows it: described in the user's words, with its count. */
    private fun MediaProvider.importProgress(progress: MessageProgress) = SongImportState.ImportProgress(type, strings.describe(progress, provider = type.name), progress.progress)

    data class SongImportResult(
        val mediaProviderType: MediaProviderType,
        val inserts: Int,
        val updates: Int,
        val deletes: Int
    )

    /**
     * Fetches [mediaProvider]'s songs as [requested] says and stores them: a full listing replaces what's stored, removing
     * what it no longer holds but for what [deleteGuard] holds back (a mass removal only if it isn't a [userRemoval], or all
     * of it for a listing that came up short); an incremental one is stored over it, or is made full when nothing is stored.
     * Once stored, the sync's start is noted for the next incremental sync to ask from, and a full sync's for the next full
     * one, unless the guard is waiting on another full pass after it: then that's the next sync. A [thorough] full listing of an
     * [IndexedMediaProvider] looks past its index.
     */
    private fun importSongs(
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
                        val guarded = guardDeletes(mediaProvider, existingSongs.size, event.result.size, songDiff.deletes, userRemoval, event.missing, fullPass = plan == SyncPlan.Full)
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
                            if (guarded.awaitsFullPass) {
                                // A held mass removal, or a listing that left songs out, waits on the next full sync, so that's
                                // the next sync rather than a week on. The songs it held weren't read, so the tags version stays
                                // as it was. Not after an incremental pass: it deletes nothing, and what its listing left out a full
                                // one leaves out too, so bringing the full sync forward gains nothing.
                                preferenceManager.setLastFullSyncStart(mediaProvider.type.name, null)
                            } else {
                                preferenceManager.setLastFullSyncStart(mediaProvider.type.name, start)
                                // Every song read again, so this source's songs hold every tag this build reads
                                preferenceManager.setSongTagsVersion(mediaProvider.type.name, SONG_TAGS_VERSION)
                            }
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

    data class PlaylistImportResult(
        val mediaProviderType: MediaProviderType
    )

    private fun importPlaylists(mediaProvider: MediaProvider, timings: ImportTimings): Flow<FlowEvent<PlaylistImportResult, MessageProgress>> = flow {
        // Straight from the database: the songs this pass just stored (or the last pass did) may not be in the shared list yet
        val existingSongs = songRepository.loadProviderSongs(mediaProvider.type)

        val findPlaylistsMark = TimeSource.Monotonic.markNow()
        mediaProvider.findPlaylists(existingSongs).collect { event ->
            when (event) {
                is FlowEvent.Progress -> {
                    emit(FlowEvent.Progress<PlaylistImportResult, MessageProgress>(event.data))
                }

                is FlowEvent.Success -> {
                    event.result.forEach { playlistUpdateData ->
                        if (playlistUpdateData.songs.isNotEmpty()) {
                            playlistStore.storePlaylist(playlistUpdateData)
                        }
                    }
                }

                is FlowEvent.Failure -> {
                    emit(event)
                }
            }
        }
        timings.findPlaylists = findPlaylistsMark.elapsedNow()
        emit(FlowEvent.Success(PlaylistImportResult(mediaProvider.type)))
    }

    /**
     * How long each phase of one provider's import took, logged as the per-provider summary line at the end of an import
     * (#866). A phase that never ran (playlists not due, a fetch that failed) stays at zero.
     */
    private class ImportTimings {
        var findSongs: Duration = Duration.ZERO
        var dbWrite: Duration = Duration.ZERO
        var findPlaylists: Duration = Duration.ZERO
    }

    /** A playlist [mediaProviderType] found, holding the [songs] of its source, which [externalId] identifies within that provider. */
    data class PlaylistUpdateData(
        val mediaProviderType: MediaProviderType,
        val name: String,
        val songs: List<Song>,
        val externalId: String
    )

    companion object {
        /**
         * Raised when the importer starts storing a tag it didn't before, so the songs stored already are read again once
         * ([songTagsOutdated]). 1: the raw artist and album tags and ids of #637.
         */
        const val SONG_TAGS_VERSION = 2

        /** Whether [type]'s songs were last imported before [SONG_TAGS_VERSION]: recorded when its import is stored. */
        fun GeneralPreferenceManager.songTagsOutdated(type: MediaProviderType): Boolean = songTagsVersion(type.name) < SONG_TAGS_VERSION
    }
}
