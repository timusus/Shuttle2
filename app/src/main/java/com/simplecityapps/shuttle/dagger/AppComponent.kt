package com.simplecityapps.shuttle.dagger

import au.com.simplecityapps.shuttle.imageloading.dagger.ImageLoaderModule
import com.simplecityapps.shuttle.ShuttleApp
import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjectionModule

@Component(
    modules = [
        AndroidInjectionModule::class,
        AppAssistedModule::class,
        AppModule::class,
        MainActivityModule::class,
        ImageLoaderModule::class
    ], dependencies = [
        CoreComponent::class
    ]
)
@AppScope
interface AppComponent {

    @Component.Builder
    interface Builder {
        @BindsInstance
        fun application(application: ShuttleApp): Builder
        fun coreComponent(component: CoreComponent): Builder
        fun build(): AppComponent
    }

    fun inject(shuttleApp: ShuttleApp)

}