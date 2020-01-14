package com.simplecityapps.shuttle.dagger

import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.dagger.PlaybackModule
import com.simplecityapps.playback.dagger.PlaybackServiceModule
import com.simplecityapps.shuttle.ShuttleApplication
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.widgets.WidgetModule
import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjectionModule
import okhttp3.OkHttpClient

@Component(
    modules = [
        AndroidInjectionModule::class,
        AppAssistedModule::class,
        AppModule::class,
        MainActivityModule::class,
        PlaybackModule::class,
        PlaybackServiceModule::class,
        WidgetModule::class,
        RepositoryModule::class,
        NetworkingModule::class,
        PersistenceModule::class,
        TagLibModule::class
    ]
)
@AppScope
interface AppComponent {

    @Component.Builder
    interface Builder {
        @BindsInstance
        fun application(application: ShuttleApplication): Builder

        fun repositoryModule(module: RepositoryModule): Builder
        fun playbackModule(module: PlaybackModule): Builder
        fun persistenceModule(module: PersistenceModule): Builder
        fun build(): AppComponent
    }

    fun inject(shuttleApplication: ShuttleApplication)

    fun okHttpClient(): OkHttpClient

    fun generalPreferenceManager(): GeneralPreferenceManager

    fun songRepository(): SongRepository
}