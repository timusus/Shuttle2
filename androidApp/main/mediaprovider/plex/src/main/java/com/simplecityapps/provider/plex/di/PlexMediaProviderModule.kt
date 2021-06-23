package com.simplecityapps.provider.plex.di

import android.content.Context
import androidx.core.content.getSystemService
import com.simplecityapps.networking.retrofit.NetworkResultAdapterFactory
import com.simplecityapps.provider.plex.CredentialStore
import com.simplecityapps.provider.plex.PlexAuthenticationManager
import com.simplecityapps.provider.plex.PlexMediaInfoProvider
import com.simplecityapps.provider.plex.PlexMediaProvider
import com.simplecityapps.provider.plex.http.ItemsService
import com.simplecityapps.provider.plex.http.UserService
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
open class PlexMediaProviderModule {

    @Provides
    @Singleton
    @Named("PlexRetrofit")
    fun provideRetrofit(@ApplicationContext context: Context, okHttpClient: OkHttpClient, moshi: Moshi): Retrofit {
        return Retrofit.Builder()
            .baseUrl("http://localhost/") // unused
            .addCallAdapterFactory(NetworkResultAdapterFactory(context.getSystemService()))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(
                okHttpClient
                    .newBuilder()
                    .readTimeout(90, TimeUnit.SECONDS)
                    .build()
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideUserService(@Named("PlexRetrofit") retrofit: Retrofit): UserService {
        return retrofit.create()
    }

    @Provides
    @Singleton
    fun provideItemsService(@Named("PlexRetrofit") retrofit: Retrofit): ItemsService {
        return retrofit.create()
    }

    @Provides
    @Singleton
    fun provideCredentialStore(securePreferenceManager: SecurePreferenceManager): CredentialStore {
        return CredentialStore(securePreferenceManager)
    }

    @Provides
    @Singleton
    fun providePlexAuthenticationManager(userService: UserService, credentialStore: CredentialStore): PlexAuthenticationManager {
        return PlexAuthenticationManager(userService, credentialStore)
    }

    @Provides
    @Singleton
    fun providePlexMediaProvider(authenticationManager: PlexAuthenticationManager, itemsService: ItemsService): PlexMediaProvider {
        return PlexMediaProvider(authenticationManager, itemsService)
    }

    @Provides
    @Singleton
    fun providePlexMediaPathProvider(authenticationManager: PlexAuthenticationManager): PlexMediaInfoProvider {
        return PlexMediaInfoProvider(authenticationManager)
    }
}