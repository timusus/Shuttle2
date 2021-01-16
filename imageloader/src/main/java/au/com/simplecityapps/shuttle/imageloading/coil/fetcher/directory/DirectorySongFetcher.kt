package au.com.simplecityapps.shuttle.imageloading.coil.fetcher.directory

import android.content.Context
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import coil.bitmap.BitmapPool
import coil.decode.DataSource
import coil.decode.Options
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.size.Size
import com.simplecityapps.mediaprovider.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source
import java.io.File
import java.util.regex.Pattern

class DirectorySongFetcher(private val context: Context) : Fetcher<Song> {

    private val pattern by lazy { Pattern.compile("(folder|cover|album).*\\.(jpg|jpeg|png)", Pattern.CASE_INSENSITIVE) }

    override suspend fun fetch(pool: BitmapPool, data: Song, size: Size, options: Options): FetchResult {
        return withContext(Dispatchers.Default) {
            val parentDocumentFile = if (DocumentsContract.isDocumentUri(context, data.path.toUri())) {
                val parent = data.path.substringBeforeLast("%2F", "")
                if (parent.isNotEmpty()) {
                    DocumentFile.fromTreeUri(context, parent.toUri())
                } else {
                    null
                }
            } else {
                File(data.path).parentFile?.let { parent ->
                    DocumentFile.fromFile(parent)
                }
            }

            parentDocumentFile?.listFiles()
                ?.filter {
                    it.type?.startsWith("image") == true
                            && it.length() > 1024
                            && pattern.matcher(it.name ?: "").matches()
                }
                ?.maxByOrNull { it.length() }
                ?.let { documentFile ->
                    context.contentResolver.openInputStream(documentFile.uri)?.let { inputStream ->
                        SourceResult(
                            inputStream.source().buffer(),
                            documentFile.type,
                            DataSource.DISK
                        )
                    }
                }
        } ?: throw IllegalStateException("Image not found")
    }

    override fun key(data: Song): String? {
        return "${data.albumArtist}:${data.album}"
    }
}