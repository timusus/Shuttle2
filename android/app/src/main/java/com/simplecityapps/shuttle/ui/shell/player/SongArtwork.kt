package com.simplecityapps.shuttle.ui.shell.player

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.simplecityapps.shuttle.designsystem.component.Artwork
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.model.Song

/** A song's artwork, loaded by Glide from the song itself; the song placeholder shows until it loads, and stays if it can't. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
internal fun SongArtwork(
    song: Song,
    modifier: Modifier = Modifier,
    size: ArtworkSize = ArtworkSize.Small,
) {
    var loaded by remember(song) { mutableStateOf(false) }
    val listener = remember(song) {
        object : RequestListener<Drawable> {
            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: Target<Drawable>,
                isFirstResource: Boolean,
            ): Boolean = false

            override fun onResourceReady(
                resource: Drawable,
                model: Any,
                target: Target<Drawable>?,
                dataSource: DataSource,
                isFirstResource: Boolean,
            ): Boolean {
                loaded = true
                return false
            }
        }
    }
    Artwork(ArtworkPlaceholder.Song, modifier, size = size) {
        // Composed under the placeholder's tone until the image arrives, so a song without art keeps the placeholder shape.
        if (!loaded) ArtworkPlaceholderShape(size)
        GlideImage(
            model = song,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        ) { it.listener(listener) }
    }
}

@Composable
private fun ArtworkPlaceholderShape(size: ArtworkSize) {
    Artwork(ArtworkPlaceholder.Song, Modifier.fillMaxSize(), size = size)
}
