package au.com.simplecityapps.shuttle.imageloading.coil.fetcher.remote

import androidx.core.net.toUri
import au.com.simplecityapps.shuttle.imageloading.coil.clone.HttpFetcher
import au.com.simplecityapps.shuttle.imageloading.coil.encode
import coil.bitmap.BitmapPool
import coil.decode.Options
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.size.Size
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager

class RemoteAlbumArtistFetcher(
    private val preferenceManager: GeneralPreferenceManager,
    private val httpFetcher: HttpFetcher
) : Fetcher<AlbumArtist> {

    override fun handles(data: AlbumArtist): Boolean {
        return !preferenceManager.artworkLocalOnly
    }

    override suspend fun fetch(pool: BitmapPool, data: AlbumArtist, size: Size, options: Options): FetchResult {
        return httpFetcher.fetch(pool, url(data).toUri(), size, options)
    }

    override fun key(data: AlbumArtist): String? {
        return url(data)
    }

    private fun url(data: AlbumArtist): String {
        return "https://artwork.shuttlemusicplayer.app/api/v1/artwork?artist=${data.name.encode()}"
    }
}