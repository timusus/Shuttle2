package com.simplecityapps.shuttle.di

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.MapKey
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import kotlin.reflect.KClass

/**
 * Creates a [ListenableWorker] with its injected dependencies. A worker's `@AssistedFactory` implements this and
 * contributes itself into the map with `@WorkerKey` and `@ContributesIntoMap(AppScope::class, binding<WorkerInstanceFactory<*>>())`.
 */
interface WorkerInstanceFactory<T : ListenableWorker> {
    fun create(
        appContext: Context,
        workerParams: WorkerParameters
    ): T
}

@MapKey
annotation class WorkerKey(val value: KClass<out ListenableWorker>)

/**
 * WorkManager's [WorkerFactory] for the injected workers. Returns null for any other class name, so WorkManager
 * falls back to its default reflective factory.
 */
@SingleIn(AppScope::class)
class MetroWorkerFactory @Inject constructor(
    private val factories: Map<KClass<out ListenableWorker>, Provider<WorkerInstanceFactory<*>>>
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? = factories.entries
        .firstOrNull { (workerClass, _) -> workerClass.java.name == workerClassName }
        ?.value
        ?.invoke()
        ?.create(appContext, workerParameters)
}
