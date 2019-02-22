package com.simplecityapps.shuttle.dagger

import android.content.Context
import com.simplecityapps.shuttle.ShuttleApp
import dagger.Module
import dagger.Provides

@Module(includes = [ViewModelModule::class])
class AppModule {

    @AppScope
    @Provides
    fun provideContext(application: ShuttleApp): Context {
        return application
    }
}