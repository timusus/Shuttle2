package au.com.simplecityapps.shuttle.imageloading.glide.loader.local

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import au.com.simplecityapps.shuttle.imageloading.glide.loader.common.AlbumArtworkProvider
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.query.SongQuery
import java.io.InputStream
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

/**
 * Folder art for MediaStore albums on Android 13+, via MediaStore's audio thumbnail of the album's first MediaStore song
 * (see [openMediaStoreAudioThumbnail]).
 */
@RequiresApi(Build.VERSION_CODES.Q)
class MediaStoreAlbumLocalArtworkModelLoader(
    private val context: Context,
    private val localArtworkModelLoader: LocalArtworkModelLoader,
    private val songRepository: SongRepository
) : ModelLoader<Album, InputStream> {
    override fun buildLoadData(
        model: Album,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<InputStream> = localArtworkModelLoader.buildLoadData(MediaStoreAlbumLocalArtworkProvider(context, model, songRepository), width, height, options)

    override fun handles(model: Album): Boolean = MediaProviderType.MediaStore in model.mediaProviders

    class Factory(
        private val context: Context,
        private val songRepository: SongRepository
    ) : ModelLoaderFactory<Album, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Album, InputStream> = MediaStoreAlbumLocalArtworkModelLoader(context, multiFactory.build(LocalArtworkProvider::class.java, InputStream::class.java) as LocalArtworkModelLoader, songRepository)

        override fun teardown() {
        }
    }

    class MediaStoreAlbumLocalArtworkProvider(
        private val context: Context,
        private val album: Album,
        private val songRepository: SongRepository
    ) : AlbumArtworkProvider(album),
        LocalArtworkProvider {
        override fun getInputStream(): InputStream? = runBlocking {
            songRepository.getSongs(SongQuery.AlbumGroupKeys(listOf(SongQuery.AlbumGroupKey(album.groupKey))))
                .firstOrNull()
                ?.firstNotNullOfOrNull { song -> song.mediaStoreId() }
                ?.let { mediaStoreId -> context.contentResolver.openMediaStoreAudioThumbnail(mediaStoreId) }
        }
    }
}
