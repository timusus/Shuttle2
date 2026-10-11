package com.simplecityapps.playback.lyrics.di

import com.simplecityapps.playback.lyrics.LrclibClient
import com.simplecityapps.playback.lyrics.LrclibHttp
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@BindingContainer
@ContributesTo(AppScope::class)
object LrclibIosModule {
    @SingleIn(AppScope::class)
    @Provides
    fun provideLrclibHttp(): LrclibHttp = LrclibHttp(LrclibClient.createHttpClient())
}
