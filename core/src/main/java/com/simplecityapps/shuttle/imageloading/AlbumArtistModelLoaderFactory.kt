package com.simplecityapps.shuttle.imageloading

import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.shuttle.networking.lastfm.LastFmService
import java.io.InputStream

class AlbumArtistModelLoaderFactory(private val lastFm: LastFmService.LastFm) : ModelLoaderFactory<AlbumArtist, InputStream> {

    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<AlbumArtist, InputStream> {
        return AlbumArtistModelLoader(lastFm)
    }

    override fun teardown() {
    }

}