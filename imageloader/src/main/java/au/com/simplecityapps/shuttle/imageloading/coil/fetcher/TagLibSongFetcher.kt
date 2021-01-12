package au.com.simplecityapps.shuttle.imageloading.coil.fetcher

import android.content.Context
import android.net.Uri
import coil.bitmap.BitmapPool
import coil.decode.DataSource
import coil.decode.Options
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.size.Size
import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.mediaprovider.model.Song
import okio.buffer
import okio.source
import java.io.File

class TagLibSongFetcher(private val context: Context) : Fetcher<Song> {

    override suspend fun fetch(pool: BitmapPool, data: Song, size: Size, options: Options): FetchResult {
        val uri: Uri = if (data.path.startsWith("content://")) {
            Uri.parse(data.path)
        } else {
            Uri.fromFile(File(data.path))
        }
        return context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            KTagLib.getArtwork(pfd.detachFd())?.inputStream()?.let { inputStream ->
                SourceResult(
                    source = inputStream.source().buffer(),
                    mimeType = "image/*",
                    dataSource = DataSource.DISK
                )
            }
        } ?: throw IllegalArgumentException("Image not found")
    }

    override fun key(data: Song): String? {
        return "${data.albumArtist}:${data.album}"
    }
}