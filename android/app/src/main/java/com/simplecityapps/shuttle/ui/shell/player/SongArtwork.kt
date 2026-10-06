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
import com.simplecityapps.shuttle.model.albumArtistStandIn
import com.simplecityapps.shuttle.settings.NowPlayingImage

/**
 * A song's artwork, loaded by Coil from the song itself, or from its album artist for [NowPlayingImage.ArtistImage] (#952);
 * the song placeholder shows until it loads, and stays if it can't.
 */
@Composable
internal fun SongArtwork(
    song: Song,
    modifier: Modifier = Modifier,
    size: ArtworkSize = ArtworkSize.Small,
    image: NowPlayingImage = NowPlayingImage.AlbumArt,
) {
    val model: Any = remember(song, image) {
        when (image) {
            NowPlayingImage.AlbumArt -> song
            NowPlayingImage.ArtistImage -> song.albumArtistStandIn() ?: song
        }
    }
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
