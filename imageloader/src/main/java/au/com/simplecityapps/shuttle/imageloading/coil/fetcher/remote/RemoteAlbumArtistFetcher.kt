package au.com.simplecityapps.shuttle.imageloading.coil.fetcher.remote

import android.net.Uri
import au.com.simplecityapps.shuttle.imageloading.coil.clone.HttpUriFetcher
import au.com.simplecityapps.shuttle.imageloading.coil.encode
import coil.bitmap.BitmapPool
import coil.decode.Options
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.size.Size
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.model.friendlyNameOrArtistName
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager

class RemoteAlbumArtistFetcher(
    private val preferenceManager: GeneralPreferenceManager,
    private val httpFetcher: HttpUriFetcher
) : Fetcher<AlbumArtist> {

    override fun handles(data: AlbumArtist): Boolean {
        return !data.friendlyNameOrArtistName.equals("Unknown", true)
                && !preferenceManager.artworkLocalOnly
    }

    override suspend fun fetch(pool: BitmapPool, data: AlbumArtist, size: Size, options: Options): FetchResult {
        return httpFetcher.fetch(pool, url(data), size, options)
    }

    override fun key(data: AlbumArtist): String {
        return httpFetcher.key(url(data))
    }

    private fun url(data: AlbumArtist): Uri {
        return Uri.parse("https://api.shuttlemusicplayer.app/v1/artwork?artist=${data.friendlyNameOrArtistName.encode()}")
    }
}