package com.simplecityapps.shuttle.di

import com.simplecityapps.shuttle.appinitializers.AppInitializer
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import dagger.multibindings.ElementsIntoSet

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppModuleBinds::class]
)
class TestAppModuleBinds {
    @Provides
    @ElementsIntoSet
    fun provideEmptyInitializers(): Set<AppInitializer> = emptySet()
}
