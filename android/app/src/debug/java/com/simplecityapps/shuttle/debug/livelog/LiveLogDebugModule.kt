package com.simplecityapps.shuttle.debug.livelog

import com.simplecityapps.shuttle.ui.screens.settings.LiveLogEntryPoint
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import java.util.Optional

/**
 * Binds the debug-only Live log screen. Release has no binding for the two `Optional`s, so their injection
 * points fall back to their `Optional.empty()` defaults.
 */
@BindingContainer
@ContributesTo(AppScope::class)
interface LiveLogDebugModule {
    @Binds
    fun bindLiveLogBuffer(impl: InMemoryLiveLogBuffer): LiveLogBuffer

    companion object {
        @Provides
        fun provideLiveLogSink(impl: InMemoryLiveLogBuffer): Optional<LiveLogSink> = Optional.of(impl)

        @Provides
        fun provideLiveLogEntryPoint(impl: LiveLogEntryPointImpl): Optional<LiveLogEntryPoint> = Optional.of(impl)
    }
}
