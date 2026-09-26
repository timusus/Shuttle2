package com.simplecityapps.shuttle.debug.livelog

import com.simplecityapps.shuttle.ui.screens.settings.LiveLogEntryPoint
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Fills the optional bindings `AppBindsModule` declares for the debug-only Live log screen. */
@Module
@InstallIn(SingletonComponent::class)
abstract class LiveLogDebugModule {
    @Binds
    @Singleton
    abstract fun bindLiveLogBuffer(impl: InMemoryLiveLogBuffer): LiveLogBuffer

    @Binds
    @Singleton
    abstract fun bindLiveLogSink(impl: InMemoryLiveLogBuffer): LiveLogSink

    @Binds
    abstract fun bindLiveLogEntryPoint(impl: LiveLogEntryPointImpl): LiveLogEntryPoint
}
