package com.simplecityapps.playback.lyrics.di

import com.simplecityapps.playback.lyrics.LrclibClient
import com.simplecityapps.playback.lyrics.LrclibHttp
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import okhttp3.OkHttpClient

@BindingContainer
@ContributesTo(AppScope::class)
object LrclibAndroidModule {
    @SingleIn(AppScope::class)
    @Provides
    fun provideLrclibHttp(okHttpClient: OkHttpClient): LrclibHttp = LrclibHttp(LrclibClient.createHttpClient(okHttpClient))
}
