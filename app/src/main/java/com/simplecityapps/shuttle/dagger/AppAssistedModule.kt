package com.simplecityapps.shuttle.dagger

import com.squareup.inject.assisted.dagger2.AssistedModule
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@InstallIn(SingletonComponent::class)
@AssistedModule
@Module
abstract class AppAssistedModule