package com.simplecityapps.provider.jellyfin.di

import android.content.Context
import androidx.core.content.getSystemService
import com.simplecityapps.networking.retrofit.NetworkResultAdapterFactory
import com.simplecityapps.provider.jellyfin.CredentialStore
import com.simplecityapps.provider.jellyfin.JellyfinAuthenticationManager
import com.simplecityapps.provider.jellyfin.JellyfinMediaInfoProvider
import com.simplecityapps.provider.jellyfin.JellyfinMediaProvider
import com.simplecityapps.provider.jellyfin.http.ItemsService
import com.simplecityapps.provider.jellyfin.http.JellyfinTranscodeService
import com.simplecityapps.provider.jellyfin.http.UserService
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
open class JellyfinMediaProviderModule {

    @Provides
    @Singleton
    @Named("JellyfinRetrofit")
    fun provideRetrofit(@ApplicationContext context: Context, okHttpClient: OkHttpClient, moshi: Moshi): Retrofit {
        return Retrofit.Builder()
            .baseUrl("http://localhost/") // unused
            .addCallAdapterFactory(NetworkResultAdapterFactory(context.getSystemService()))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(
                okHttpClient
                    .newBuilder()
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideUserService(@Named("JellyfinRetrofit") retrofit: Retrofit): UserService {
        return retrofit.create()
    }

    @Provides
    @Singleton
    fun provideItemsService(@Named("JellyfinRetrofit") retrofit: Retrofit): ItemsService {
        return retrofit.create()
    }

    @Provides
    @Singleton
    fun provideTranscodeService(@Named("JellyfinRetrofit") retrofit: Retrofit): JellyfinTranscodeService {
        return retrofit.create()
    }

    @Provides
    @Singleton
    fun provideCredentialStore(securePreferenceManager: SecurePreferenceManager): CredentialStore {
        return CredentialStore(securePreferenceManager)
    }

    @Provides
    @Singleton
    fun provideJellyfinAuthenticationManager(userService: UserService, credentialStore: CredentialStore): JellyfinAuthenticationManager {
        return JellyfinAuthenticationManager(userService, credentialStore)
    }

    @Provides
    @Singleton
    fun provideJellyfinMediaProvider(authenticationManager: JellyfinAuthenticationManager, itemsService: ItemsService): JellyfinMediaProvider {
        return JellyfinMediaProvider(authenticationManager, itemsService)
    }

    @Provides
    @Singleton
    fun provideJellyfinMediaPathProvider(authenticationManager: JellyfinAuthenticationManager, transcodeService: JellyfinTranscodeService): JellyfinMediaInfoProvider {
        return JellyfinMediaInfoProvider(authenticationManager, transcodeService)
    }
}