package com.simplecityapps.shuttle.ui.shell.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.simplecityapps.shuttle.designsystem.component.Artwork
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.nowPlayingArtworkModel
import com.simplecityapps.shuttle.settings.NowPlayingImage

/** A song's artwork via Coil, from its album artist for [NowPlayingImage.ArtistImage]; the placeholder stays if it can't load. */
@Composable
internal fun SongArtwork(
    song: Song,
    modifier: Modifier = Modifier,
    size: ArtworkSize = ArtworkSize.Small,
    image: NowPlayingImage = NowPlayingImage.AlbumArt,
) {
    val model: Any = remember(song, image) { nowPlayingArtworkModel(song, image == NowPlayingImage.ArtistImage) }
    var loaded by remember(model) { mutableStateOf(false) }
    Artwork(ArtworkPlaceholder.Song, modifier, size = size) {
        // Composed under the placeholder's tone until the image arrives, so a song without art keeps the placeholder shape.
        if (!loaded) ArtworkPlaceholderShape(size)
        AsyncImage(
            model = model,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            onSuccess = { loaded = true },
        )
    }
}

@Composable
private fun ArtworkPlaceholderShape(size: ArtworkSize) {
    Artwork(ArtworkPlaceholder.Song, Modifier.fillMaxSize(), size = size)
}
