package au.com.simplecityapps.shuttle.imageloading.coil.fetcher.remote

import android.net.Uri
import androidx.core.net.toUri
import au.com.simplecityapps.shuttle.imageloading.coil.clone.HttpUriFetcher
import au.com.simplecityapps.shuttle.imageloading.coil.encode
import coil.bitmap.BitmapPool
import coil.decode.Options
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.size.Size
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.friendlyAlbumArtistOrArtistName
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager

class RemoteAlbumFetcher(
    private val preferenceManager: GeneralPreferenceManager,
    private val httpFetcher: HttpUriFetcher
) : Fetcher<Album> {

    override fun handles(data: Album): Boolean {
        return data.name != null
                && !data.name.equals("Unknown", true)
                && !data.friendlyAlbumArtistOrArtistName.equals("Unknown", true)
                && !preferenceManager.artworkLocalOnly
    }

    override suspend fun fetch(pool: BitmapPool, data: Album, size: Size, options: Options): FetchResult {
        return httpFetcher.fetch(pool, url(data), size, options)
    }

    override fun key(data: Album): String {
        return httpFetcher.key(url(data))
    }

    private fun url(data: Album): Uri {
        return "https://api.shuttlemusicplayer.app/v1/artwork?artist=${data.friendlyAlbumArtistOrArtistName.encode()}&album=${data.name!!.encode()}".toUri()
    }
}