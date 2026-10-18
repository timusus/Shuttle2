package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.TimeSource

/** The passes [MediaImporter] runs once it holds the import lock: a full import, or a sync as [SyncPolicy] plans it. */
internal class ImportPasses(
    private val providers: CopyOnWriteSet<MediaProvider>,
    private val preferenceManager: GeneralPreferenceManager,
    private val clock: Clock,
    private val afterImport: suspend (songTagsCurrent: Boolean) -> Unit,
    private val providerJobs: ProviderJobs,
    private val providerImporter: ProviderImporter
) {
    private val logger = Logger.tagged("MediaImporter")

    suspend fun importAll(foldersChanged: Boolean) {
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

    suspend fun syncAll(
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
     * Imports each provider in [plans], all at once and each on its own, so one failing or removed ([MediaImporter.removeProvider]) leaves
     * the others to finish. Once they all have, records the import if any of them finished, and returns how each one ended, in
     * the order of [plans]. See [ProviderImporter.import]. [thorough] has an [IndexedMediaProvider] look past its index.
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

        val outcomes =
            providerJobs.runAll(plans) { mediaProvider, plan ->
                providerImporter.import(mediaProvider, plan, timingsByProvider.getValue(mediaProvider.type), showProgress, quietFailures, userRemoval = foldersChanged && !mediaProvider.type.remote, thorough = thorough, playlistsDue = playlistsDue)
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
}
