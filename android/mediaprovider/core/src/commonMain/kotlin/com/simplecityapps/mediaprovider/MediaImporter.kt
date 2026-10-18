package com.simplecityapps.mediaprovider

import androidx.annotation.VisibleForTesting
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.SourceStateStore
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex

class MediaImporter(
    private val strings: MediaImportStrings,
    songRepository: SongRepository,
    playlistStore: ImportedPlaylistStore,
    private val sourceState: SourceStateStore,
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

    private val states = ImportStateTracker(strings)

    /** The running import's progress, or how the last one ended, so a collector that arrives mid-import sees where it's at. */
    override val songImportState: StateFlow<SongImportState> = states.songImportState

    override val providerImportStates: StateFlow<Map<MediaProviderType, SongImportState>> = states.providerImportStates

    override val importsCompleted: StateFlow<Int> = states.importsCompleted

    private val providers = CopyOnWriteSet<MediaProvider>()

    /**
     * The sources an import reads. Safe to change from any thread while one runs, which reads them as they were when it
     * started; a source removed for good goes through [removeProvider], which stops its running import too.
     */
    val mediaProviders: MutableSet<MediaProvider> get() = providers

    private val providerJobs = ProviderJobs(providers)

    private val passes =
        ImportPasses(
            providers,
            sourceState,
            clock,
            afterImport,
            providerJobs,
            ProviderImporter(
                strings,
                sourceState,
                clock,
                SongImporter(strings, songRepository, sourceState, clock, DeleteGuard(sourceState)),
                PlaylistImporter(songRepository, playlistStore, sourceState, playlistSync),
                playlistSync,
                states
            )
        )

    /**
     * Whether a source's songs were imported before this build's [SONG_TAGS_VERSION], so they lack tags it reads, and no
     * import has run under this version yet: the one launch re-import is due. A full import is how a provider brings its
     * songs' tags up to date, however it decides which songs to read again, and an import updates songs in place by path,
     * so a re-import fills them in without changing an id. A source whose import fails stays outdated, and catches
     * up on its own next import rather than by importing everything again at each launch.
     */
    val songTagsOutdated: Boolean
        get() = sourceState.songTagsRescanVersion < SONG_TAGS_VERSION && providers.snapshot.any { sourceState.songTagsOutdated(it.type) }

    /**
     * Reads every source in full, as the user asked (a rescan, a change of folders, the first import): an
     * [IndexedMediaProvider] looks in every folder rather than trusting its index alone. [foldersChanged] says the user just
     * changed which of this device's folders are read, so a mass removal of this device's songs is theirs and applies at
     * once rather than waiting on the next import.
     */
    suspend fun import(foldersChanged: Boolean = false) {
        if (!requestImport(foldersChanged)) return
        runRequestedImports()?.let { throw it }
    }

    /**
     * Like [import], but does not return while another import holds the lock: it waits for that import, and for the pass its
     * request asks for, so a caller that shows the import running (a foreground notification) stays up until it's done.
     */
    suspend fun importAndWait(foldersChanged: Boolean = false) {
        if (!requestImport(foldersChanged)) return
        runRequestedImports(awaitRunning = true)?.let { throw it }
    }

    /**
     * Asks for a full import without running it: whichever import is running or next starts runs a pass with it, so a
     * request that unique work coalesced away isn't dropped. Returns false when there is no source to read.
     */
    fun requestImport(foldersChanged: Boolean): Boolean {
        if (providers.snapshot.isEmpty()) {
            logger.debug { "Import failed, media providers empty" }
            return false
        }

        if (foldersChanged) foldersChangedRequested.store(true)
        rescanRequested.store(true)
        return true
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
        if (sourceState.lastMediaImportDate == null || songTagsOutdated) {
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
                passes.syncAll(trigger, startOnce)
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
     * [onStart] is called before each pass. With [awaitRunning] the first turn waits for the lock rather than leaving the
     * request to its holder, so this returns only once no import is running.
     */
    private suspend fun runRequestedImports(
        onStart: suspend () -> Unit = {},
        awaitRunning: Boolean = false
    ): Exception? {
        var failure: Exception? = null
        var waitForLock = awaitRunning
        // Checked again after each unlock: a request made between the last check and the unlock found the lock still held,
        // so whichever import sees it next runs it
        while (waitForLock || rescanRequested.load()) {
            if (waitForLock) {
                importLock.lock()
                waitForLock = false
            } else if (!importLock.tryLock()) {
                logger.debug { "Import already in progress, requesting a follow-up pass" }
                break
            }
            try {
                while (rescanRequested.exchange(false)) {
                    onStart()
                    failure =
                        try {
                            passes.importAll(foldersChanged = foldersChangedRequested.exchange(false))
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

    /**
     * Stops reading [mediaProvider]: takes it out of the sources and cancels its running import, returning once that has
     * ended, so nothing it read is stored after this returns. Its import state goes with it.
     */
    suspend fun removeProvider(mediaProvider: MediaProvider) {
        // Taken out first: a pass that read the sources before this sees it gone when it tracks its job ([ProviderJobs])
        providers -= mediaProvider
        providerJobs.cancelAndJoin(mediaProvider.type)
        states.remove(mediaProvider.type)
    }
}
