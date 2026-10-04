package com.simplecityapps.shuttle.shared.di

import com.simplecityapps.shuttle.scrobbling.IsLastFmConfigured
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

/** iOS has no scrobbling (`ScrobblingViewModel` is excluded from [com.simplecityapps.shuttle.shared.IosAppGraph]), so Last.fm is never configured. */
@ContributesTo(AppScope::class)
@BindingContainer
object IosScrobblingModule {
    @Provides
    fun isLastFmConfigured(): IsLastFmConfigured = IsLastFmConfigured { false }
}
