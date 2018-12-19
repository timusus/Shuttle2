package com.simplecityapps.shuttle.dagger

import com.simplecityapps.shuttle.ShuttleApp
import com.simplecityapps.shuttle.core.dagger.RepositoryModule
import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjectionModule
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        AndroidInjectionModule::class,
        AppAssistedModule::class,
        AppModule::class,
        RepositoryModule::class,
        MainActivityModule::class
    ]
)

interface AppComponent {

    @Component.Builder
    interface Builder {

        @BindsInstance
        fun application(application: ShuttleApp): Builder

        fun build(): AppComponent
    }

    fun inject(shuttleApp: ShuttleApp)
}