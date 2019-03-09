package com.simplecityapps.shuttle.dagger

import android.content.SharedPreferences
import com.simplecityapps.mediaprovider.repository.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.AlbumRepository
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackNotificationManager
import com.simplecityapps.playback.dagger.PlaybackModule
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueManager
import dagger.Component
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        RepositoryModule::class,
        NetworkingModule::class,
        PlaybackModule::class,
        PersistenceModule::class
    ]
)
interface CoreComponent {

    fun getSongRepository(): SongRepository

    fun getAlbumRepository(): AlbumRepository

    fun getAlbumArtistRepository(): AlbumArtistRepository

    fun getOkHttpClient(): OkHttpClient

    fun getPlaybackManager(): PlaybackManager

    fun getQueue(): QueueManager

    fun getPlaybackNotificationManager(): PlaybackNotificationManager

    fun getSharedPreferences(): SharedPreferences

    fun getPlaybackPreferenceManager(): PlaybackPreferenceManager

    @Component.Builder
    interface Builder {
        fun build(): CoreComponent
        fun repositoryModule(module: RepositoryModule): Builder
        fun playbackModule(module: PlaybackModule): Builder
        fun persistenceModule(module: PersistenceModule): Builder
    }
}