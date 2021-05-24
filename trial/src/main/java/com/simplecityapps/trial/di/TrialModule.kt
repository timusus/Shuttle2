package com.simplecityapps.trial.di

import android.content.Context
import androidx.core.content.getSystemService
import com.simplecityapps.networking.retrofit.NetworkResultAdapterFactory
import com.simplecityapps.trial.DeviceService
import com.simplecityapps.trial.TrialManager
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Credentials
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class TrialModule {

    @Provides
    @Singleton
    @Named("TrialRetrofit")
    fun provideRetrofit(@ApplicationContext context: Context, okHttpClient: OkHttpClient, moshi: Moshi): Retrofit {
        return Retrofit.Builder()
//            .baseUrl("https://api.shuttlemusicplayer.app/")
            .baseUrl("http://192.168.1.107:8080/")
            .addCallAdapterFactory(NetworkResultAdapterFactory(context.getSystemService()))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(okHttpClient.newBuilder().authenticator { route, response ->
//                if (route?.address?.url?.host == "api.shuttlemusicplayer.app") {
                    response.request
                        .newBuilder()
                        .header("Authorization", Credentials.basic("s2", "my_auth_password"))
                        .build()
//                } else {
//                    response.request
//                }
            }.build())
            .build()
    }

    @Provides
    @Singleton
    fun provideDeviceService(@Named("TrialRetrofit") retrofit: Retrofit): DeviceService {
        return retrofit.create(DeviceService::class.java)
    }

    @Provides
    @Singleton
    fun provideTrialManager(@ApplicationContext context: Context, moshi: Moshi, deviceService: DeviceService): TrialManager {
        return TrialManager(context, moshi, deviceService)
    }
}