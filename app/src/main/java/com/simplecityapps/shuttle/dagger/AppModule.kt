package com.simplecityapps.shuttle.dagger

import android.content.Context
import com.simplecityapps.shuttle.ShuttleApplication
import com.simplecityapps.shuttle.debug.DebugLoggingTree
import dagger.Module
import dagger.Provides

@Module(includes = [AppModuleBinds::class])
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