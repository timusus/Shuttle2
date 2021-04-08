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
import com.simplecityapps.shuttle.dagger.AppScope
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import java.util.concurrent.TimeUnit
import javax.inject.Named

@Module
open class JellyfinMediaProviderModule {

    @Provides
    @AppScope
    @Named("JellyfinRetrofit")
    fun provideRetrofit(context: Context, okHttpClient: OkHttpClient, moshi: Moshi): Retrofit {
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
    @AppScope
    fun provideUserService(@Named("JellyfinRetrofit") retrofit: Retrofit): UserService {
        return retrofit.create()
    }

    @Provides
    @AppScope
    fun provideItemsService(@Named("JellyfinRetrofit") retrofit: Retrofit): ItemsService {
        return retrofit.create()
    }

    @Provides
    @AppScope
    fun provideTranscodeService(@Named("JellyfinRetrofit") retrofit: Retrofit): JellyfinTranscodeService {
        return retrofit.create()
    }

    @Provides
    @AppScope
    fun provideCredentialStore(securePreferenceManager: SecurePreferenceManager): CredentialStore {
        return CredentialStore(securePreferenceManager)
    }

    @Provides
    @AppScope
    fun provideJellyfinAuthenticationManager(userService: UserService, credentialStore: CredentialStore): JellyfinAuthenticationManager {
        return JellyfinAuthenticationManager(userService, credentialStore)
    }

    @Provides
    @AppScope
    fun provideJellyfinMediaProvider(authenticationManager: JellyfinAuthenticationManager, itemsService: ItemsService): JellyfinMediaProvider {
        return JellyfinMediaProvider(authenticationManager, itemsService)
    }

    @Provides
    @AppScope
    fun provideJellyfinMediaPathProvider(authenticationManager: JellyfinAuthenticationManager, transcodeService: JellyfinTranscodeService): JellyfinMediaInfoProvider {
        return JellyfinMediaInfoProvider(authenticationManager, transcodeService)
    }
}