package com.simplecityapps.provider.emby.di

import android.content.Context
import androidx.core.content.getSystemService
import com.simplecityapps.networking.retrofit.NetworkResultAdapterFactory
import com.simplecityapps.provider.emby.*
import com.simplecityapps.provider.emby.http.EmbyTranscodeService
import com.simplecityapps.provider.emby.http.ItemsService
import com.simplecityapps.provider.emby.http.LoginCredentials
import com.simplecityapps.provider.emby.http.UserService
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
open class EmbyMediaProviderModule {

    @Provides
    @Singleton
    @Named("EmbyRetrofit")
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
    fun provideUserService(@Named("EmbyRetrofit") retrofit: Retrofit): UserService {
        return retrofit.create()
    }

    @Provides
    @Singleton
    fun provideItemsService(@Named("EmbyRetrofit") retrofit: Retrofit): ItemsService {
        return retrofit.create()
    }

    @Provides
    @Singleton
    fun provideTranscodeService(@Named("EmbyRetrofit") retrofit: Retrofit): EmbyTranscodeService {
        return retrofit.create()
    }

    @Provides
    @Singleton
    fun provideCredentialStore(securePreferenceManager: SecurePreferenceManager): CredentialStore {
        return CredentialStore(securePreferenceManager).apply {
            if (BuildConfig.DEBUG) {
                if (loginCredentials == null) {
                    loginCredentials = LoginCredentials("tim", "")
                    address = "https://emby.mediaserver.timmalseed.dev"
                }
            }
        }
    }

    @Provides
    @Singleton
    fun provideEmbyAuthenticationManager(userService: UserService, credentialStore: CredentialStore): EmbyAuthenticationManager {
        return EmbyAuthenticationManager(userService, credentialStore)
    }

    @Provides
    @Singleton
    fun provideEmbyMediaProvider(authenticationManager: EmbyAuthenticationManager, itemsService: ItemsService): EmbyMediaProvider {
        return EmbyMediaProvider(authenticationManager, itemsService)
    }

    @Provides
    @Singleton
    fun provideEmbyMediaPathProvider(authenticationManager: EmbyAuthenticationManager, embyTranscodeService: EmbyTranscodeService): EmbyMediaInfoProvider {
        return EmbyMediaInfoProvider(authenticationManager, embyTranscodeService)
    }
}