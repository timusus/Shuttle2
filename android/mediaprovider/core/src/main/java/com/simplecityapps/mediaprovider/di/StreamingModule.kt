package com.simplecityapps.mediaprovider.di

import com.simplecityapps.mediaprovider.ConnectivityMeteredNetwork
import com.simplecityapps.mediaprovider.MeteredNetwork
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo

@ContributesTo(AppScope::class)
@BindingContainer
abstract class StreamingModule {
    @Binds
    abstract fun bindMeteredNetwork(meteredNetwork: ConnectivityMeteredNetwork): MeteredNetwork
}
