package com.simplecityapps.shuttle.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber

@InstallIn(SingletonComponent::class)
@Module
class CoroutineModule {
    @Singleton
    @Provides
    fun coroutineExceptionHandler(): CoroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable)
    }

    @Singleton
    @Provides
    @AppSupervisorJob
    fun appSupervisorJob(): Job = SupervisorJob()

    @Singleton
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

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class AppCoroutineScope

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class AppSupervisorJob

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class IoDispatcher
