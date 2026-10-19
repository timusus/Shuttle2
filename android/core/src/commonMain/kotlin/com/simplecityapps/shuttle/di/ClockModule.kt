package com.simplecityapps.shuttle.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import kotlin.time.Clock

@ContributesTo(AppScope::class)
@BindingContainer
class ClockModule {
    @Provides
    fun provideClock(): Clock = Clock.System
}
