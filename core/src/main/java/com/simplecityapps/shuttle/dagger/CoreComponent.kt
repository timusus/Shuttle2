package com.simplecityapps.shuttle.dagger

import com.simplecityapps.mediaprovider.repository.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.AlbumRepository
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.queue.PlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.core.dagger.NetworkingModule
import com.simplecityapps.shuttle.core.dagger.PlaybackModule
import com.simplecityapps.shuttle.core.dagger.RepositoryModule
import dagger.Component
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        RepositoryModule::class,
        NetworkingModule::class,
        PlaybackModule::class
    ]
)
interface CoreComponent {

    fun getSongRepository(): SongRepository

    fun getAlbumRepository(): AlbumRepository

    fun getAlbumArtistRepository(): AlbumArtistRepository

    fun getOkHttpClient(): OkHttpClient

    fun getPlaybackManager(): PlaybackManager

    fun getQueue(): QueueManager

    @Component.Builder interface Builder {
        fun build(): CoreComponent
        fun repositoryModule(module: RepositoryModule): Builder
    }
}