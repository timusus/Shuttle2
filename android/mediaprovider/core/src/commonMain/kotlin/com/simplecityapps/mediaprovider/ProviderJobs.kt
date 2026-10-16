package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.MediaProviderType
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Each source's import in the running pass, so [MediaImporter.removeProvider] can stop it. */
internal class ProviderJobs(
    private val providers: CopyOnWriteSet<MediaProvider>
) {
    /** Held around [jobs] and the outcomes [runAll] collects. */
    private val lock = Mutex()

    private val jobs = mutableMapOf<MediaProviderType, Job>()

    /**
     * Runs [import] for each provider in [plans], all at once and each on its own, so one failing or removed leaves the
     * others to finish. Returns how each one ended; a source removed since the pass read the sources never runs and is cancelled.
     */
    suspend fun runAll(
        plans: List<Pair<MediaProvider, SyncPlan>>,
        import: suspend (MediaProvider, SyncPlan) -> ProviderSyncOutcome
    ): Map<MediaProviderType, ProviderSyncOutcome> {
        val outcomes = mutableMapOf<MediaProviderType, ProviderSyncOutcome>()
        try {
            withContext(Dispatchers.IO) {
                supervisorScope {
                    plans.mapNotNull { (mediaProvider, plan) ->
                        val job =
                            launch(start = CoroutineStart.LAZY) {
                                val outcome =
                                    try {
                                        import(mediaProvider, plan)
                                    } catch (e: CancellationException) {
                                        withContext(NonCancellable) { lock.withLock { outcomes[mediaProvider.type] = ProviderSyncOutcome.Cancelled } }
                                        throw e
                                    }
                                lock.withLock { outcomes[mediaProvider.type] = outcome }
                            }
                        if (track(mediaProvider, job)) {
                            job.start()
                            job
                        } else {
                            // Removed since the pass read the sources: it never runs
                            lock.withLock { outcomes[mediaProvider.type] = ProviderSyncOutcome.Cancelled }
                            null
                        }
                    }.joinAll()
                }
            }
        } finally {
            withContext(NonCancellable) { lock.withLock { jobs.clear() } }
        }
        return outcomes
    }

    /** Stops [type]'s running import, returning once it has ended. */
    suspend fun cancelAndJoin(type: MediaProviderType) {
        lock.withLock { jobs[type] }?.cancelAndJoin()
    }

    /**
     * Records [job] as [mediaProvider]'s import, so [cancelAndJoin] can stop it, unless the provider was removed since the
     * pass read the sources: then cancels it, and it never runs.
     */
    private suspend fun track(
        mediaProvider: MediaProvider,
        job: Job
    ): Boolean = lock.withLock {
        (mediaProvider in providers.snapshot).also { tracked -> if (tracked) jobs[mediaProvider.type] = job else job.cancel() }
    }
}
