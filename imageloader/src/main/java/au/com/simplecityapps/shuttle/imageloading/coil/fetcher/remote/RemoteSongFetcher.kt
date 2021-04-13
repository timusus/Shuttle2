package au.com.simplecityapps.shuttle.imageloading.coil.fetcher.remote

import androidx.core.net.toUri
import au.com.simplecityapps.shuttle.imageloading.coil.clone.HttpFetcher
import au.com.simplecityapps.shuttle.imageloading.coil.encode
import coil.bitmap.BitmapPool
import coil.decode.Options
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.size.Size
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.model.friendlyArtistName
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager

class RemoteSongFetcher(
    private val preferenceManager: GeneralPreferenceManager,
    private val httpFetcher: HttpFetcher
) : Fetcher<Song> {

    override fun handles(data: Song): Boolean {
        return !preferenceManager.artworkLocalOnly
    }

    override suspend fun fetch(pool: BitmapPool, data: Song, size: Size, options: Options): FetchResult {
        return url(data)?.let { url ->
            httpFetcher.fetch(pool, url.toUri(), size, options)
        } ?: throw IllegalStateException("Failed to retrieve artwork url for song")
    }

    override fun key(data: Song): String? {
        return url(data)
    }

    private fun url(data: Song): String? {
        return data.friendlyArtistName?.let { artist ->
            data.album?.let { album -> "https://artwork.shuttlemusicplayer.app/api/v1/artwork?artist=${artist.encode()}&album=${album.encode()}" }
        }
    }
}