package com.simplecityapps.mediaprovider.di

import com.simplecityapps.mediaprovider.ConnectivityMeteredNetwork
import com.simplecityapps.mediaprovider.MeteredNetwork
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@InstallIn(SingletonComponent::class)
@Module
abstract class StreamingModule {
    @Binds
    abstract fun bindMeteredNetwork(meteredNetwork: ConnectivityMeteredNetwork): MeteredNetwork
}
