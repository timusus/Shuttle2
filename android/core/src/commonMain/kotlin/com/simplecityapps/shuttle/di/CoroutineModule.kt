package com.simplecityapps.shuttle.di

import com.simplecityapps.shuttle.logging.Logger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

@ContributesTo(AppScope::class)
@BindingContainer
class CoroutineModule {
    @SingleIn(AppScope::class)
    @Provides
    fun coroutineExceptionHandler(): CoroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        logger.error(throwable) { "Uncaught exception in the app coroutine scope" }
    }

    @SingleIn(AppScope::class)
    @Provides
    @AppSupervisorJob
    fun appSupervisorJob(): Job = SupervisorJob()

    @SingleIn(AppScope::class)
    @Provides
    @AppCoroutineScope
    fun provideAppCoroutineScope(
        @AppSupervisorJob job: Job,
        coroutineExceptionHandler: CoroutineExceptionHandler
    ): CoroutineScope = CoroutineScope(Dispatchers.Main + job + coroutineExceptionHandler)

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}

private val logger = Logger.tagged("CoroutineModule")

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class AppCoroutineScope

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class AppSupervisorJob

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class IoDispatcher
