package com.simplecityapps.shuttle.di

import com.simplecityapps.playback.di.CastModule
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [CastModule::class]
)
class TestCastModule
