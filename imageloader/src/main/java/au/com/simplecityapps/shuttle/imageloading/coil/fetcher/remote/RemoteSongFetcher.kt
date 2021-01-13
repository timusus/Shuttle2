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
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager

class RemoteSongFetcher(
    private val preferenceManager: GeneralPreferenceManager,
    private val httpFetcher: HttpFetcher
) : Fetcher<Song> {

    override fun handles(data: Song): Boolean {
        return !preferenceManager.artworkLocalOnly
    }

    override suspend fun fetch(pool: BitmapPool, data: Song, size: Size, options: Options): FetchResult {
        return httpFetcher.fetch(pool, url(data).toUri(), size, options)
    }

    override fun key(data: Song): String? {
        return url(data)
    }

    private fun url(data: Song): String {
        return "https://artwork.shuttlemusicplayer.app/api/v1/artwork?artist=${data.albumArtist.encode()}&album=${data.album.encode()}"
    }
}