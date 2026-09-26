package com.simplecityapps.imageloading.glide.loader.local

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.simplecityapps.imageloading.glide.loader.common.SongArtworkProvider
import com.simplecityapps.shuttle.model.Song
import java.io.InputStream

/**
 * Folder art for MediaStore songs on Android 13+, via MediaStore's audio thumbnail (see [openMediaStoreAudioThumbnail]).
 */
@RequiresApi(Build.VERSION_CODES.Q)
class MediaStoreSongLocalArtworkModelLoader(
    private val context: Context,
    private val localArtworkModelLoader: LocalArtworkModelLoader
) : ModelLoader<Song, InputStream> {
    override fun buildLoadData(
        model: Song,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<InputStream> = localArtworkModelLoader.buildLoadData(MediaStoreSongLocalArtworkProvider(context, model), width, height, options)

    override fun handles(model: Song): Boolean = model.mediaStoreId() != null

    class Factory(private val context: Context) : ModelLoaderFactory<Song, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Song, InputStream> = MediaStoreSongLocalArtworkModelLoader(context, multiFactory.build(LocalArtworkProvider::class.java, InputStream::class.java) as LocalArtworkModelLoader)

        override fun teardown() {
        }
    }

    class MediaStoreSongLocalArtworkProvider(
        private val context: Context,
        song: Song
    ) : SongArtworkProvider(song),
        LocalArtworkProvider {
        override suspend fun getInputStream(): InputStream? = song.mediaStoreId()?.let { mediaStoreId -> context.contentResolver.openMediaStoreAudioThumbnail(mediaStoreId) }
    }
}
