package com.simplecityapps.mediaprovider

import androidx.annotation.VisibleForTesting
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.SourceReachability
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MediaImporter(
    private val strings: MediaImportStrings,
    songRepository: SongRepository,
    playlistStore: ImportedPlaylistStore,
    private val preferenceManager: GeneralPreferenceManager,
    /**
     * Runs after each import, told whether every source's songs now hold every tag this build reads: what moving the
     * stored album keys to the album identity rule (#637) waits for.
     */
    private val afterImport: suspend (songTagsCurrent: Boolean) -> Unit,
    /** Sends the edits made in S2 to a server's playlists before that server's playlists are read again (#916). */
    private val playlistSync: ServerPlaylistSync? = null,
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

    private val songImporter = SongImporter(strings, songRepository, preferenceManager, clock, DeleteGuard(preferenceManager))

    private val playlistImporter = PlaylistImporter(songRepository, playlistStore, preferenceManager, playlistSync)

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
     *
     * Returns how it went ([SyncResult]). [onStart] is called once there is work to do, just before it starts, and not at all
     * for a sync that is skipped, so a caller can show that it's running only when it is.
     */
    suspend fun sync(
        trigger: SyncTrigger,
        onStart: suspend () -> Unit = {}
    ): SyncResult {
        if (providers.snapshot.isEmpty()) return SyncResult.Skipped
        if (preferenceManager.lastMediaImportDate == null || songTagsOutdated) {
            logger.debug { "A full import is due, skipping the $trigger sync" }
            return SyncResult.Skipped
        }

        if (!importLock.tryLock()) {
            logger.debug { "Import already in progress, skipping the $trigger sync" }
            return SyncResult.Skipped
        }
        var started = false
        val startOnce: suspend () -> Unit = {
            if (!started) {
                started = true
                onStart()
            }
        }
        val result =
            try {
                syncAll(trigger, startOnce)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(e) { "Sync failed" }
                SyncResult.Aborted(e)
            } finally {
                importLock.unlock()
            }
        // An import asked for while this ran found the lock held and left its request for whoever unlocks next. It's work
        // this sync did, so it starts the caller's foreground state if nothing has, and its failure is the sync's
        val followUpFailure = runRequestedImports(startOnce)
        return if (followUpFailure != null && result !is SyncResult.Aborted) SyncResult.Aborted(followUpFailure) else result
    }

    /**
     * Runs a full import for as long as one is requested and no other import holds [importLock]; the last pass's failure.
     * [onStart] is called before each pass.
     */
    private suspend fun runRequestedImports(onStart: suspend () -> Unit = {}): Exception? {
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
                    onStart()
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
            .values
            .firstNotNullOfOrNull { outcome -> (outcome as? ProviderSyncOutcome.Failed)?.cause }
            ?.let { failure -> throw failure }
    }

    private suspend fun syncAll(
        trigger: SyncTrigger,
        onStart: suspend () -> Unit
    ): SyncResult {
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
            return SyncResult.Skipped
        }
        logger.debug { "Starting $trigger sync: ${plans.joinToString { (mediaProvider, plan) -> "${mediaProvider.type} $plan" }}" }
        onStart()
        // A delta that changed nothing leaves the playlists as they were, bar the daily sync, which catches a playlist
        // edited on the server without touching its songs
        val outcomes =
            importProviders(plans, showProgress = false, quietFailures = trigger == SyncTrigger.Foreground, foldersChanged = false, thorough = false) { plan, result ->
                result != null && (plan == SyncPlan.Full || trigger == SyncTrigger.Periodic || result.inserts + result.updates + result.deletes > 0)
            }
        return SyncResult.Ran(outcomes)
    }

    /**
     * Imports each provider in [plans], all at once and each on its own, so one failing or removed ([removeProvider]) leaves
     * the others to finish. Once they all have, records the import if any of them finished, and returns how each one ended, in
     * the order of [plans]. See [importProvider]. [thorough] has an [IndexedMediaProvider] look past its index.
     */
    private suspend fun importProviders(
        plans: List<Pair<MediaProvider, SyncPlan>>,
        showProgress: Boolean,
        quietFailures: Boolean,
        foldersChanged: Boolean,
        thorough: Boolean,
        playlistsDue: (SyncPlan, SongImportResult?) -> Boolean
    ): Map<MediaProviderType, ProviderSyncOutcome> {
        val time = TimeSource.Monotonic.markNow()

        // Each provider's timings, filled in by its own song and playlist passes, so the summary line below can attribute a slow import (#866)
        val timingsByProvider = plans.associate { (mediaProvider, _) -> mediaProvider.type to ImportTimings() }

        val outcomes = mutableMapOf<MediaProviderType, ProviderSyncOutcome>()
        try {
            withContext(Dispatchers.IO) {
                supervisorScope {
                    plans.mapNotNull { (mediaProvider, plan) ->
                        val job =
                            launch(start = CoroutineStart.LAZY) {
                                val outcome =
                                    try {
                                        importProvider(mediaProvider, plan, timingsByProvider.getValue(mediaProvider.type), showProgress, quietFailures, userRemoval = foldersChanged && !mediaProvider.type.remote, thorough = thorough, playlistsDue = playlistsDue)
                                    } catch (e: CancellationException) {
                                        withContext(NonCancellable) { providerJobsLock.withLock { outcomes[mediaProvider.type] = ProviderSyncOutcome.Cancelled } }
                                        throw e
                                    }
                                providerJobsLock.withLock { outcomes[mediaProvider.type] = outcome }
                            }
                        if (track(mediaProvider, job)) {
                            job.start()
                            job
                        } else {
                            // Removed since the pass read the sources: it never runs
                            providerJobsLock.withLock { outcomes[mediaProvider.type] = ProviderSyncOutcome.Cancelled }
                            null
                        }
                    }.joinAll()
                }
            }
        } finally {
            withContext(NonCancellable) { providerJobsLock.withLock { providerJobs.clear() } }
        }

        // What the sources that finished stored counts as imported, though another threw: a source that failed stays
        // outdated, so afterImport waits on it all the same
        var afterImportTime = Duration.ZERO
        val finished = outcomes.values.count { outcome -> outcome is ProviderSyncOutcome.Success || (outcome is ProviderSyncOutcome.Failed && outcome.cause == null) }
        if (finished > 0) {
            preferenceManager.lastMediaImportDate = clock.now()

            val afterImportMark = TimeSource.Monotonic.markNow()
            afterImport(providers.snapshot.none { preferenceManager.songTagsOutdated(it.type) })
            afterImportTime = afterImportMark.elapsedNow()
        }

        // One line per provider per run, so a slow import can be attributed to its phases (#866)
        timingsByProvider.forEach { (type, timings) ->
            logger.debug {
                "$type import phases: findSongs ${timings.findSongs.inWholeMilliseconds}ms, " +
                    "song diff and db write ${timings.dbWrite.inWholeMilliseconds}ms, " +
                    "findPlaylists ${timings.findPlaylists.inWholeMilliseconds}ms"
            }
        }
        logger.debug { "Import complete in ${time.elapsedNow().inWholeMilliseconds}ms (afterImport ${afterImportTime.inWholeMilliseconds}ms)" }
        return plans.mapNotNull { (mediaProvider, _) -> outcomes[mediaProvider.type]?.let { outcome -> mediaProvider.type to outcome } }.toMap()
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
     * doesn't outlast it. How it ended: a failure carries the exception it threw, if it did; cancelled, it's rethrown.
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
    ): ProviderSyncOutcome {
        val type = mediaProvider.type
        var failed: ProviderSyncOutcome.Failed? = null
        // What a cancelled import puts back: it didn't end, so it says nothing of how the source stands
        val before = _providerImportStates.value[type]?.takeUnless { it is SongImportState.ImportProgress }
        var cancelled = false
        try {
            if (showProgress) {
                publish(type, mediaProvider.importProgress(MessageProgress(if (type.remote) ImportPhase.Connecting else ImportPhase.Fetching, progress = null)))
            }
            var stored: SongImportResult? = null
            songImporter.import(mediaProvider, plan, timings, userRemoval, thorough).collect { event ->
                when (event) {
                    is FlowEvent.Progress -> {
                        if (showProgress) publish(type, mediaProvider.importProgress(event.data))
                    }

                    is FlowEvent.Success -> {
                        stored = event.result
                        val completedAt = clock.now()
                        preferenceManager.setSourceReachability(type.name, SourceReachability(error = null, checkedAt = completedAt))
                        preferenceManager.setSourceUpdated(type.name, completedAt)
                        val changed = event.result.inserts + event.result.updates + event.result.deletes > 0
                        // A quiet sync that stored nothing stays silent, unless it clears an earlier failure.
                        val clearsError = (_providerImportStates.value[type] as? SongImportState.ImportComplete)?.error != null
                        if (showProgress || changed || clearsError) {
                            complete(type, error = null)
                        }
                    }

                    is FlowEvent.Failure -> {
                        failed = ProviderSyncOutcome.Failed(event.message ?: strings.importError)
                        if (quietFailures) {
                            logger.warn { "$type sync failed, leaving its status as it was: ${event.message}" }
                        } else {
                            preferenceManager.setSourceReachability(type.name, SourceReachability(error = event.message, checkedAt = clock.now()))
                            complete(type, event.message)
                        }
                    }
                }
            }
            if (playlistsDue(plan, stored)) {
                // A full sync, or one that added songs a playlist might now match, reads every playlist again
                val reuseVersions = plan != SyncPlan.Full && stored != null && stored.inserts == 0
                val import = suspend {
                    playlistImporter.import(mediaProvider, timings, songsStored = stored != null, reuseVersions = reuseVersions).collect { event ->
                        if (event is FlowEvent.Failure) logger.warn { "$type playlist import failed: ${event.message}" }
                    }
                }
                playlistSync?.withEditsSent(type) { import() } ?: import()
            }
            return failed ?: ProviderSyncOutcome.Success
        } catch (e: CancellationException) {
            cancelled = true
            throw e
        } catch (e: Exception) {
            logger.error(e) { "$type import failed" }
            if (!quietFailures) {
                preferenceManager.setSourceReachability(type.name, SourceReachability(error = strings.importError, checkedAt = clock.now()))
                complete(type, strings.importError)
            }
            return ProviderSyncOutcome.Failed(strings.importError, cause = e)
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

    data class PlaylistImportResult(
        val mediaProviderType: MediaProviderType
    )

    /**
     * The playlists a provider found: [playlists], each with the songs of its source, and [unread], the [PlaylistUpdateData.externalId]s
     * of those it listed but couldn't read all the songs of (one it read in part is in [playlists] too, so what it read is
     * added). A playlist stored from an [unread] source is never deleted, never taken as gone from it. [unchanged] are those
     * whose songs it didn't read, as the server reports the version they were last read at; they stay as they are.
     * [versions] holds the server's version of each playlist it read in full or left [unchanged], by
     * [PlaylistUpdateData.externalId], for the next sync to pass back.
     */
    data class PlaylistListing(
        val playlists: List<PlaylistUpdateData>,
        val unread: Set<String> = emptySet(),
        val unchanged: Set<String> = emptySet(),
        val versions: Map<String, String> = emptyMap()
    )

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
         * ([songTagsOutdated]). 1: the raw artist and album tags and ids of #637. 3: the codec, bit rate,
         * sample rate and channel count of Emby and Jellyfin songs (#889). 4: a local combined album artist tag
         * ("A; B") reads as its first artist (#886). 5: a local multi-value ALBUMARTIST lists each value as an album artist.
         */
        const val SONG_TAGS_VERSION = 5

        /** Whether [type]'s songs were last imported before [SONG_TAGS_VERSION]: recorded when its import is stored. */
        fun GeneralPreferenceManager.songTagsOutdated(type: MediaProviderType): Boolean = songTagsVersion(type.name) < SONG_TAGS_VERSION
    }
}
