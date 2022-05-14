package com.simplecityapps.shuttle.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Qualifier
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class CoroutineModule {

    @Singleton
    @Provides
    fun coroutineExceptionHandler(): CoroutineExceptionHandler {
        return CoroutineExceptionHandler { _, throwable ->
            Timber.e(throwable)
        }
    }

    @Singleton
    @Provides
    @AppSupervisorJob
    fun appSupervisorJob(): Job {
        return SupervisorJob()
    }

    @Singleton
    @Provides
    @AppCoroutineScope
    fun provideAppCoroutineScope(@AppSupervisorJob job: Job, coroutineExceptionHandler: CoroutineExceptionHandler): CoroutineScope {
        return CoroutineScope(Dispatchers.Main + job + coroutineExceptionHandler)
    }
}

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class AppCoroutineScope

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class AppSupervisorJob
