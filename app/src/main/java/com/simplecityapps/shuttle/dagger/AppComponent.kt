package com.simplecityapps.shuttle.dagger

import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.dagger.PlaybackModule
import com.simplecityapps.playback.dagger.PlaybackServiceModule
import com.simplecityapps.provider.emby.di.EmbyMediaProviderModule
import com.simplecityapps.provider.jellyfin.di.JellyfinMediaProviderModule
import com.simplecityapps.provider.plex.di.PlexMediaProviderModule
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
        ArtworkServiceModule::class,
        WidgetModule::class,
        RepositoryModule::class,
        NetworkingModule::class,
        PersistenceModule::class,
        MediaProviderModule::class,
        EmbyMediaProviderModule::class,
        JellyfinMediaProviderModule::class,
        PlexMediaProviderModule::class,
        ImageLoaderModule::class
    ]
)
@AppScope
interface AppComponent {

    @Component.Builder
    interface Builder {
        @BindsInstance
        fun application(application: ShuttleApplication): Builder
        fun build(): AppComponent
    }

    fun inject(shuttleApplication: ShuttleApplication)

    fun okHttpClient(): OkHttpClient

    fun generalPreferenceManager(): GeneralPreferenceManager

    fun songRepository(): SongRepository
}