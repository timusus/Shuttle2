package com.simplecityapps.shuttle.dagger

import com.simplecityapps.playback.dagger.PlaybackServiceModule
import com.simplecityapps.shuttle.ShuttleApplication
import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjectionModule

@Component(
    modules = [
        AndroidInjectionModule::class,
        AppAssistedModule::class,
        AppModule::class,
        MainActivityModule::class,
        PlaybackServiceModule::class
    ], dependencies = [
        CoreComponent::class
    ]
)
@AppScope
interface AppComponent {

    @Component.Builder
    interface Builder {
        @BindsInstance
        fun application(application: ShuttleApplication): Builder
        fun coreComponent(component: CoreComponent): Builder
        fun build(): AppComponent
    }

    fun inject(shuttleApplication: ShuttleApplication)
}