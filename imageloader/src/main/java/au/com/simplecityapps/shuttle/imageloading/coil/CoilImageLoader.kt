package au.com.simplecityapps.shuttle.imageloading.coil

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.widget.ImageView
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.toBitmap
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import au.com.simplecityapps.shuttle.imageloading.coil.clone.HttpFetcher
import au.com.simplecityapps.shuttle.imageloading.coil.fetcher.MultiFetcher
import au.com.simplecityapps.shuttle.imageloading.coil.fetcher.directory.DirectoryAlbumFetcher
import au.com.simplecityapps.shuttle.imageloading.coil.fetcher.directory.DirectorySongFetcher
import au.com.simplecityapps.shuttle.imageloading.coil.fetcher.remote.RemoteAlbumArtistFetcher
import au.com.simplecityapps.shuttle.imageloading.coil.fetcher.remote.RemoteAlbumFetcher
import au.com.simplecityapps.shuttle.imageloading.coil.fetcher.remote.RemoteSongFetcher
import au.com.simplecityapps.shuttle.imageloading.coil.fetcher.tag.TagLibAlbumFetcher
import au.com.simplecityapps.shuttle.imageloading.coil.fetcher.tag.TagLibSongFetcher
import au.com.simplecityapps.shuttle.imageloading.coil.transition.ColorSetTransition
import au.com.simplecityapps.shuttle.imageloading.palette.ColorSet
import coil.ImageLoader
import coil.executeBlocking
import coil.request.Disposable
import coil.request.ImageRequest
import coil.size.Scale
import coil.transform.CircleCropTransformation
import coil.transform.RoundedCornersTransformation
import coil.transform.Transformation
import coil.transition.CrossfadeTransition
import coil.transition.Transition
import coil.util.CoilUtils
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import kotlinx.coroutines.Dispatchers
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.OkHttpClient
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class CoilImageLoader(
    private val context: Context,
    private val songRepository: SongRepository,
    httpClient: OkHttpClient,
    preferenceManager: GeneralPreferenceManager,
) : ArtworkImageLoader {

    private val imageLoader: ImageLoader by lazy {
        val callFactory: Call.Factory by lazy {
            val connectivityManager: ConnectivityManager? = context.getSystemService()
            httpClient.newBuilder()
                .addInterceptor { chain ->
                    // Don't make a network request if we're not allowed to
                    chain.proceed(
                        chain.request().newBuilder()
                            .apply {
                                if (preferenceManager.artworkWifiOnly && connectivityManager?.isActiveNetworkMetered == true) {
                                    cacheControl(
                                        CacheControl
                                            .Builder()
                                            .onlyIfCached()
                                            .build()
                                    )
                                }
                            }.build()
                    )
                }
                .addInterceptor { chain ->
                    // Add custom cache control headers to ensure our result is stored in the cache
                    val response = chain.proceed(chain.request())
                    response.newBuilder()
                        .apply {
                            header(
                                name = "Cache-Control",
                                value = CacheControl
                                    .Builder()
                                    .maxAge(14, TimeUnit.DAYS)
                                    .build()
                                    .toString()
                            )
                            removeHeader("Pragma")
                        }.build()

                }
                .cache(CoilUtils.createDefaultCache(context))
                .build()
        }

        ImageLoader.Builder(context)
            .callFactory {
                callFactory
            }
            .componentRegistry {
                val tagLibSongFetcher = TagLibSongFetcher(context)
                val directorySongFetcher = DirectorySongFetcher(context)
                val httpFetcher = HttpFetcher(callFactory)
                add(
                    MultiFetcher(
                        setOf(
                            tagLibSongFetcher,
                            directorySongFetcher,
                            RemoteSongFetcher(preferenceManager, httpFetcher)
                        )
                    )
                )
                add(
                    MultiFetcher(
                        setOf(
                            DirectoryAlbumFetcher(songRepository, directorySongFetcher),
                            TagLibAlbumFetcher(songRepository, tagLibSongFetcher),
                            RemoteAlbumFetcher(preferenceManager, httpFetcher)
                        )
                    )
                )
                add(RemoteAlbumArtistFetcher(preferenceManager, httpFetcher))
            }
            .allowHardware(false)
            .dispatcher(Dispatchers.Default)
            .build()
    }

    override fun loadArtwork(imageView: ImageView, data: Any, options: List<ArtworkImageLoader.Options>, onCompletion: ((Result<Unit>) -> Unit)?, onColorSetGenerated: ((ColorSet) -> Unit)?) {
        imageView.loadAny(data) {
            applyOptions(options = options, onColorSetGenerated = onColorSetGenerated)
        }
    }

    override fun loadBitmap(data: Any, width: Int, height: Int, options: List<ArtworkImageLoader.Options>, onCompletion: (Bitmap?) -> Unit) {
        imageLoader.enqueue(
            ImageRequest
                .Builder(context)
                .data(data)
                .size(width, height)
                .applyOptions(options = options)
                .target(
                    onSuccess = { drawable -> onCompletion(drawable.toBitmap()) },
                    onError = { drawable -> onCompletion(drawable?.toBitmap()) }
                )
                .build()
        )
    }

    override fun loadBitmap(data: Any): ByteArray? {
        val stream = ByteArrayOutputStream()
        return imageLoader.executeBlocking(
            ImageRequest
                .Builder(context)
                .data(data)
                .allowHardware(false)
                .build()
        ).drawable?.let { drawable ->
            drawable.toBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
    }

    override fun loadColorSet(data: Any, callback: (ColorSet?) -> Unit) {

    }

    override fun clear(imageView: ImageView) {
        CoilUtils.clear(imageView)
    }

    override suspend fun clearCache(context: Context?) {
        imageLoader.memoryCache.clear()
    }


    private fun ImageRequest.Builder.applyOptions(options: List<ArtworkImageLoader.Options>, onColorSetGenerated: ((ColorSet) -> Unit)? = null): ImageRequest.Builder {
        val transformations = mutableListOf<Transformation>()
        options.forEach { option ->
            when (option) {
                ArtworkImageLoader.Options.CircleCrop -> {
                    transformations.add(CircleCropTransformation())
                }
                is ArtworkImageLoader.Options.RoundedCorners -> {
                    transformations.add(RoundedCornersTransformation(option.radius.toFloat()))
                }
                is ArtworkImageLoader.Options.Priority -> {
                    // No op
                }
                is ArtworkImageLoader.Options.Crossfade -> {
                    // If we're loading a color set, the crossfade transition is defined there
                    if (!options.any { it is ArtworkImageLoader.Options.LoadColorSet }) {
                        crossfade(option.duration)
                    }
                }
                is ArtworkImageLoader.Options.Placeholder -> {
                    placeholder(option.placeholderResId)
                    error(option.placeholderResId)
                }
                is ArtworkImageLoader.Options.CenterCrop -> {
                    scale(Scale.FIT)
                }
                is ArtworkImageLoader.Options.LoadColorSet -> {
                    val delegateTransition: Transition? = options
                        .firstOrNull { crossFadeOption -> crossFadeOption is ArtworkImageLoader.Options.Crossfade }
                        ?.let { crossFadeOption ->
                            CrossfadeTransition((crossFadeOption as ArtworkImageLoader.Options.Crossfade).duration)
                        }

                    transition(ColorSetTransition(context, delegateTransition) { colorSet ->
                        onColorSetGenerated?.invoke(colorSet)
                    })
                }
            }
        }
        transformations(transformations)
        return this
    }

    private inline fun ImageView.loadAny(
        data: Any?,
        builder: ImageRequest.Builder.() -> Unit = {}
    ): Disposable {
        val request = ImageRequest.Builder(context)
            .data(data)
            .target(this)
            .apply(builder)
            .build()
        return imageLoader.enqueue(request)
    }
}
