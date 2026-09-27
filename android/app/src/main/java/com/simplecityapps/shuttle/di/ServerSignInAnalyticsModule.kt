package com.simplecityapps.shuttle.di

import com.simplecityapps.shuttle.ui.screens.sources.servers.ServerSignInAnalytics
import com.simplecityapps.trial.MonetisationAnalytics
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

/** A server sign-in is recorded in the monetisation funnel. Each provider binds its own `ServerAuthentication`. */
@ContributesTo(AppScope::class)
@BindingContainer
object ServerSignInAnalyticsModule {
    @Provides
    fun provideServerSignInAnalytics(analytics: MonetisationAnalytics): ServerSignInAnalytics = ServerSignInAnalytics(analytics::serverConnected)
}
