package com.simplecityapps.shuttle.mediaprovider.jellyfin.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.simplecityapps.shuttle.deviceinfo.DeviceInfo
import com.simplecityapps.shuttle.mediaprovider.jellyfin.JellyfinAuthenticationManager
import com.simplecityapps.shuttle.mediaprovider.jellyfin.JellyfinMediaProvider
import com.simplecityapps.shuttle.mediaprovider.jellyfin.JellyfinPreferenceManager
import com.simplecityapps.shuttle.mediaprovider.jellyfin.http.service.ItemsService
import com.simplecityapps.shuttle.mediaprovider.jellyfin.http.service.UserService
import com.simplecityapps.shuttle.preferences.SecurePreferenceManager
import com.simplecityapps.shuttle.preferences.di.PreferencesModule
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.ExperimentalSerializationApi
import javax.inject.Named
import javax.inject.Singleton

@Module(includes = [PreferencesModule::class])
@InstallIn(SingletonComponent::class)
class MediaImportModule {

    @Provides
    @Singleton
    @Named("JellyfinDatastore")
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        ) {
            context.preferencesDataStoreFile("jellyfin")
        }
    }
    
    @Provides
    @Singleton
    fun provideItemsService(httpClient: HttpClient): ItemsService {
        return ItemsService(httpClient)
    }

    @Provides
    @Singleton
    fun provideUserService(httpClient: HttpClient): UserService {
        return UserService(httpClient)
    }

    @Provides
    @Singleton
    fun provideJellyfinPreferenceManager(securePreferenceManager: SecurePreferenceManager): JellyfinPreferenceManager {
        return JellyfinPreferenceManager(securePreferenceManager = securePreferenceManager)
    }

    @Provides
    @Singleton
    fun provideAuthenticationManager(userService: UserService, preferenceManager: JellyfinPreferenceManager, deviceInfo: DeviceInfo): JellyfinAuthenticationManager {
        return JellyfinAuthenticationManager(userService = userService, preferenceManager = preferenceManager, deviceInfo = deviceInfo)
    }


    @Provides
    @Singleton
    fun provideMediaStoreMediaProvider(authenticationManager: JellyfinAuthenticationManager, preferenceManager: JellyfinPreferenceManager, itemsService: ItemsService): JellyfinMediaProvider {
        return JellyfinMediaProvider(authenticationManager = authenticationManager, jellyfinPreferenceManager = preferenceManager, itemsService = itemsService)
    }
}