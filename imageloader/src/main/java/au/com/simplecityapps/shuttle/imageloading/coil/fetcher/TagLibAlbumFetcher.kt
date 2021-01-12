package au.com.simplecityapps.shuttle.imageloading.coil.fetcher

import coil.bitmap.BitmapPool
import coil.decode.Options
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.size.Size
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import kotlinx.coroutines.flow.firstOrNull

class TagLibAlbumFetcher(
    private val songRepository: SongRepository,
    private val tagLibSongFetcher: TagLibSongFetcher
) : Fetcher<Album> {

    override suspend fun fetch(pool: BitmapPool, data: Album, size: Size, options: Options): FetchResult {
        return songRepository.getSongs(SongQuery.Album(data.name, data.albumArtist)).firstOrNull()?.firstOrNull()?.let { song ->
            tagLibSongFetcher.fetch(pool, song, size, options)
        } ?: throw IllegalStateException("Failed to retrieve song associated with album")
    }

    override fun key(data: Album): String? {
        return "${data.albumArtist}:${data.name}"
    }
}