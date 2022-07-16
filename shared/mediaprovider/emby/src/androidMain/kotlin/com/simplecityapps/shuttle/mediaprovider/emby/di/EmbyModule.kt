package com.simplecityapps.shuttle.mediaprovider.emby.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.simplecityapps.shuttle.deviceinfo.DeviceInfo
import com.simplecityapps.shuttle.mediaprovider.emby.EmbyAuthenticationManager
import com.simplecityapps.shuttle.mediaprovider.emby.EmbyMediaProvider
import com.simplecityapps.shuttle.mediaprovider.emby.EmbyPreferenceManager
import com.simplecityapps.shuttle.mediaprovider.emby.http.service.ItemsService
import com.simplecityapps.shuttle.mediaprovider.emby.http.service.UserService
import com.simplecityapps.shuttle.preferences.SecurePreferenceManager
import com.simplecityapps.shuttle.preferences.di.PreferencesModule
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Named
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module(includes = [PreferencesModule::class])
class EmbyModule {

    @Provides
    @Singleton
    @Named("JellyfinDatastore")
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        ) {
            context.preferencesDataStoreFile("emby")
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
    fun provideJellyfinPreferenceManager(securePreferenceManager: SecurePreferenceManager): EmbyPreferenceManager {
        return EmbyPreferenceManager(securePreferenceManager = securePreferenceManager)
    }

    @Provides
    @Singleton
    fun provideAuthenticationManager(userService: UserService, preferenceManager: EmbyPreferenceManager, deviceInfo: DeviceInfo): EmbyAuthenticationManager {
        return EmbyAuthenticationManager(userService = userService, preferenceManager = preferenceManager, deviceInfo = deviceInfo)
    }

    @Provides
    @Singleton
    fun provideMediaStoreMediaProvider(authenticationManager: EmbyAuthenticationManager, preferenceManager: EmbyPreferenceManager, itemsService: ItemsService): EmbyMediaProvider {
        return EmbyMediaProvider(authenticationManager = authenticationManager, embyPreferenceManager = preferenceManager, itemsService = itemsService)
    }
}
