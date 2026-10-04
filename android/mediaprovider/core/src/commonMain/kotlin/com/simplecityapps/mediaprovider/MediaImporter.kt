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
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
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

    /** Test seam: runs after the last pass has found no request pending, just before [importLock] is released. */
    @VisibleForTesting
    internal var beforeUnlock: (suspend () -> Unit)? = null

    val isImporting: Boolean get() = importLock.isLocked

    private val _songImportState = MutableStateFlow<SongImportState>(SongImportState.Idle)

    /** The running import's progress, or how the last one ended, so a collector that arrives mid-import sees where it's at. */
    override val songImportState: StateFlow<SongImportState> = _songImportState.asStateFlow()

    private val _providerImportStates = MutableStateFlow<Map<MediaProviderType, SongImportState>>(emptyMap())

    override val providerImportStates: StateFlow<Map<MediaProviderType, SongImportState>> = _providerImportStates.asStateFlow()

    val mediaProviders: MutableSet<MediaProvider> = mutableSetOf()

    var importCount: Int = 0

    /**
     * Whether a source's songs were imported before this build's [SONG_TAGS_VERSION], so they lack tags it reads, and no
     * import has run under this version yet: the one launch re-import is due. Every provider but MediaStore re-reads each
     * song on each import (MediaStore does while its own songs are outdated), and an import updates songs in place by
     * path, so a re-import fills them in without changing an id. A source whose import fails stays outdated, and catches
     * up on its own next import rather than by importing everything again at each launch.
     */
    val songTagsOutdated: Boolean
        get() = preferenceManager.songTagsRescanVersion < SONG_TAGS_VERSION && mediaProviders.any { preferenceManager.songTagsOutdated(it.type) }

    suspend fun import() {
        if (mediaProviders.isEmpty()) {
            logger.debug { "Import failed, media providers empty" }
            return
        }

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
        if (mediaProviders.isEmpty()) return
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
                            importAll()
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

    private suspend fun importAll() {
        logger.debug { "Starting import.." }
        preferenceManager.songTagsRescanVersion = SONG_TAGS_VERSION
        importProviders(mediaProviders.map { mediaProvider -> mediaProvider to SyncPlan.Full }, showProgress = true, quietFailures = false) { _, _ -> true }
    }

    private suspend fun syncAll(trigger: SyncTrigger) {
        val now = clock.now()
        val plans =
            mediaProviders.mapNotNull { mediaProvider ->
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
        importProviders(plans, showProgress = false, quietFailures = trigger == SyncTrigger.Foreground) { plan, result ->
            result != null && (plan == SyncPlan.Full || trigger == SyncTrigger.Periodic || result.inserts + result.updates > 0)
        }
    }

    /**
     * Fetches and stores the songs of each provider in [plans], all at once, then the playlists of those [playlistsDue]
     * says, given the provider's plan and its stored songs (null when that failed). Shows each fetch's progress only if
     * [showProgress]. A fetch that stores nothing new is reported only if [showProgress], which is what reloads what shows
     * the library; one that fails is, unless [quietFailures], which logs it and leaves the source's status as it was.
     */
    private suspend fun importProviders(
        plans: List<Pair<MediaProvider, SyncPlan>>,
        showProgress: Boolean,
        quietFailures: Boolean,
        playlistsDue: (SyncPlan, SongImportResult?) -> Boolean
    ) {
        val time = TimeSource.Monotonic.markNow()

        if (showProgress) {
            plans.forEach { (mediaProvider, _) ->
                val start = MessageProgress(if (mediaProvider.type.remote) ImportPhase.Connecting else ImportPhase.Fetching, progress = null)
                publish(mediaProvider.type, mediaProvider.importProgress(start))
            }
        }

        withContext(Dispatchers.IO) {
            val playlistProviders =
                plans.map { (mediaProvider, plan) ->
                    async {
                        var stored: SongImportResult? = null
                        importSongs(mediaProvider, plan).collect { event ->
                            when (event) {
                                is FlowEvent.Progress -> {
                                    if (showProgress) publish(mediaProvider.type, mediaProvider.importProgress(event.data))
                                }

                                is FlowEvent.Success -> {
                                    stored = event.result
                                    val changed = event.result.inserts + event.result.updates + event.result.deletes > 0
                                    // A quiet sync that stored nothing stays silent, unless it clears an earlier failure.
                                    val clearsError = (_providerImportStates.value[mediaProvider.type] as? SongImportState.ImportComplete)?.error != null
                                    if (showProgress || changed || clearsError) {
                                        publish(mediaProvider.type, SongImportState.ImportComplete(mediaProvider.type, error = null))
                                    }
                                }

                                is FlowEvent.Failure -> {
                                    if (quietFailures) {
                                        logger.warn { "${mediaProvider.type} sync failed, leaving its status as it was: ${event.message}" }
                                    } else {
                                        publish(mediaProvider.type, SongImportState.ImportComplete(mediaProvider.type, event.message))
                                    }
                                }
                            }
                        }
                        mediaProvider.takeIf { playlistsDue(plan, stored) }
                    }
                }.awaitAll().filterNotNull()

            playlistProviders.map { mediaProvider ->
                async {
                    importPlaylists(mediaProvider).collect { event ->
                        if (event is FlowEvent.Failure) logger.warn { "${mediaProvider.type} playlist import failed: ${event.message}" }
                    }
                }
            }.awaitAll()
        }

        preferenceManager.lastMediaImportDate = clock.now()
        importCount++

        afterImport(mediaProviders.none { preferenceManager.songTagsOutdated(it.type) })

        logger.debug { "Import complete in ${time.elapsedNow().inWholeMilliseconds}ms)" }
    }

    private fun publish(type: MediaProviderType, state: SongImportState) {
        _songImportState.value = state
        _providerImportStates.update { states -> states + (type to state) }
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
     * what it no longer holds; an incremental one is stored over it, or is made full when nothing is stored. Once stored,
     * the sync's start is noted for the next incremental sync to ask from.
     */
    private fun importSongs(
        mediaProvider: MediaProvider,
        requested: SyncPlan
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
                SyncPlan.Full -> mediaProvider.findSongs(existingSongs)
                is SyncPlan.Incremental -> (mediaProvider as IncrementalMediaProvider).findSongsChangedSince(existingSongs, plan.since)
            }
        songs.collect { event ->
            when (event) {
                is FlowEvent.Progress -> {
                    emit(FlowEvent.Progress<SongImportResult, MessageProgress>(event.data))
                }

                is FlowEvent.Success -> {
                    try {
                        emit(FlowEvent.Progress<SongImportResult, MessageProgress>(MessageProgress(ImportPhase.Saving(event.result.size), null)))
                        val songDiff = SongDiff(existingSongs, event.result, deleteMissing = plan == SyncPlan.Full).apply()
                        val result =
                            songRepository.insertUpdateAndDelete(
                                inserts = songDiff.inserts,
                                updates = songDiff.updates,
                                deletes = songDiff.deletes,
                                mediaProviderType = mediaProvider.type
                            )
                        mediaProvider.songsStored()
                        preferenceManager.setLastSyncStart(mediaProvider.type.name, start)
                        if (plan == SyncPlan.Full) {
                            preferenceManager.setLastFullSyncStart(mediaProvider.type.name, start)
                            // Every song read again, so this source's songs hold every tag this build reads
                            preferenceManager.setSongTagsVersion(mediaProvider.type.name, SONG_TAGS_VERSION)
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
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to update song repository" }
                        emit(FlowEvent.Failure(strings.importError))
                    }
                }

                is FlowEvent.Failure -> {
                    emit(event)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

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

    private fun importPlaylists(mediaProvider: MediaProvider): Flow<FlowEvent<PlaylistImportResult, MessageProgress>> = flow {
        // Straight from the database: the songs this pass just stored (or the last pass did) may not be in the shared list yet
        val existingSongs = songRepository.loadProviderSongs(mediaProvider.type)

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
        emit(FlowEvent.Success(PlaylistImportResult(mediaProvider.type)))
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
