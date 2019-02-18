package com.simplecityapps.shuttle.imageloading

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.shuttle.networking.ApiClient
import com.simplecityapps.shuttle.networking.lastfm.LastFmService
import java.io.InputStream


@GlideModule
class MyAppGlideModule : AppGlideModule() {

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {

        // Todo: Inject LastFm Service, and don't instantiate OKHttpClient here
        val lastFm = LastFmService(ApiClient().okHttpClient).lastFm

        registry.prepend(AlbumArtist::class.java, InputStream::class.java, AlbumArtistModelLoaderFactory(lastFm))
    }
}