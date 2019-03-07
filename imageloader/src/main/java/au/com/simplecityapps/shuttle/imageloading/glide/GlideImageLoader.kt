package au.com.simplecityapps.shuttle.imageloading.glide

import android.widget.ImageView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import au.com.simplecityapps.shuttle.imageloading.glide.module.GlideApp
import au.com.simplecityapps.shuttle.imageloading.glide.provider.AlbumArtistArtworkProvider
import au.com.simplecityapps.shuttle.imageloading.glide.provider.AlbumArtworkProvider
import au.com.simplecityapps.shuttle.imageloading.glide.provider.ArtworkProvider
import au.com.simplecityapps.shuttle.imageloading.glide.provider.SongArtworkProvider
import au.com.simplecityapps.shuttle.imageloading.networking.lastfm.LastFmService
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.model.Song

class GlideImageLoader(private val lastFm: LastFmService.LastFm) : ArtworkImageLoader {

    override fun loadArtwork(imageView: ImageView, albumArtist: AlbumArtist, vararg options: ArtworkImageLoader.Options) {
        loadArtwork(imageView, AlbumArtistArtworkProvider(lastFm, albumArtist), *options)
    }

    override fun loadArtwork(imageView: ImageView, album: Album, vararg options: ArtworkImageLoader.Options) {
        loadArtwork(imageView, AlbumArtworkProvider(lastFm, album), *options)
    }

    override fun loadArtwork(imageView: ImageView, song: Song, vararg options: ArtworkImageLoader.Options) {
        loadArtwork(imageView, SongArtworkProvider(lastFm, song), *options)
    }

    private fun loadArtwork(imageView: ImageView, artworkProvider: ArtworkProvider, vararg options: ArtworkImageLoader.Options) {
        val glideRequest = GlideApp
            .with(imageView.context)
            .load(artworkProvider)
            .diskCacheStrategy(DiskCacheStrategy.ALL)

        options.forEach { option ->
            when (option) {
                is ArtworkImageLoader.Options.CircleCrop -> {
                    glideRequest.apply(RequestOptions.circleCropTransform())
                }
                is ArtworkImageLoader.Options.RoundedCorners -> {
                    glideRequest.apply(RequestOptions.bitmapTransform(MultiTransformation(mutableListOf(CenterCrop(), RoundedCorners(option.radius)))))
                }
            }
        }

        glideRequest.into(imageView)
    }

    override fun clear(imageView: ImageView) {
        GlideApp
            .with(imageView.context)
            .clear(imageView)
    }
}