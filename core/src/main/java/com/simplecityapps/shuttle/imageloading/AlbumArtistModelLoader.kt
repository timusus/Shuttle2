package com.simplecityapps.shuttle.imageloading

import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.data.HttpUrlFetcher
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.signature.ObjectKey
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.shuttle.networking.lastfm.LastFmService
import com.simplecityapps.shuttle.networking.lastfm.model.LastFmArtist
import retrofit2.Call
import java.io.IOException
import java.io.InputStream
import java.net.SocketException

class AlbumArtistModelLoader(private val lastFm: LastFmService.LastFm) : ModelLoader<AlbumArtist, InputStream> {

    override fun buildLoadData(model: AlbumArtist, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? {
        return ModelLoader.LoadData<InputStream>(ObjectKey(model), AlbumArtistDataFetcher(lastFm, model))
    }

    override fun handles(model: AlbumArtist): Boolean {
        return true
    }
}

class AlbumArtistDataFetcher(
    private val lastFm: LastFmService.LastFm,
    private val albumArtist: AlbumArtist
) : DataFetcher<InputStream> {

    private var httpUrlFetcher: HttpUrlFetcher? = null

    private var call: Call<LastFmArtist>? = null

    override fun getDataClass(): Class<InputStream> {
        return InputStream::class.java
    }

    override fun cleanup() {
        httpUrlFetcher?.cleanup()
    }

    override fun getDataSource(): DataSource {
        return DataSource.REMOTE
    }

    override fun cancel() {
        call?.cancel()
        httpUrlFetcher?.cancel()
    }

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
        // Todo: Store retrieved artwork in our database
        (albumArtist.artworkUri ?: run {
            call = lastFm.getLastFmArtist(albumArtist.name)
            try {
                call!!.execute().body()?.imageUrl
            } catch (e: SocketException) {
                null
            } catch (e: IOException) {
                null
            }
        })?.let { url ->
            httpUrlFetcher = HttpUrlFetcher(GlideUrl(url), 2500)
            httpUrlFetcher!!.loadData(priority, callback)
        } ?: callback.onLoadFailed(GlideException("Album Artist url not found"))
    }
}