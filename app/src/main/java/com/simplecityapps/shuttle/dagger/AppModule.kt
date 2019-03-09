package com.simplecityapps.shuttle.dagger

import android.content.Context
import com.simplecityapps.shuttle.DebugLoggingTree
import com.simplecityapps.shuttle.ShuttleApplication
import dagger.Module
import dagger.Provides

@Module(includes = [ViewModelModule::class, AppModuleBinds::class])
class AppModule {

    @AppScope
    @Provides
    fun provideContext(application: ShuttleApplication): Context {
        return application.applicationContext
    }

    @AppScope
    @Provides
    fun provideDebugLoggingTree(): DebugLoggingTree {
        return DebugLoggingTree()
    }
}