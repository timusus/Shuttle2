package com.simplecityapps.shuttle.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import kotlin.random.Random
import kotlin.random.asKotlinRandom

@ContributesTo(AppScope::class)
@BindingContainer
class SecureRandomModule {
    @Provides
    @SecureRandom
    fun provideSecureRandom(): Random = java.security.SecureRandom().asKotlinRandom()
}
