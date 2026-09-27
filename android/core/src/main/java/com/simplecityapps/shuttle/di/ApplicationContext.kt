package com.simplecityapps.shuttle.di

import android.app.Application
import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier

/** The application [Context]. The graph is created with the [Application]; this binds it as a context. */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class ApplicationContext

@ContributesTo(AppScope::class)
@BindingContainer
object ApplicationContextModule {
    @Provides
    @ApplicationContext
    fun provideApplicationContext(application: Application): Context = application
}
