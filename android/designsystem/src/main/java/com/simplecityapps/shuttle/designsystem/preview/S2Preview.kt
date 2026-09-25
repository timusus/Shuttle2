package com.simplecityapps.shuttle.designsystem.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import com.simplecityapps.shuttle.designsystem.component.LocalPreviewArtwork
import com.simplecityapps.shuttle.designsystem.component.PreviewArtwork
import com.simplecityapps.shuttle.designsystem.theme.S2Theme
import com.simplecityapps.shuttle.fixtures.SampleAlbum
import com.simplecityapps.shuttle.fixtures.SampleArtist
import com.simplecityapps.shuttle.fixtures.SampleLibrary
import com.simplecityapps.shuttle.fixtures.SampleSong

// Previews show the invented sample library (:android:fixtures): its names from SampleLibrary, its covers through
// LocalPreviewArtwork. The fixtures are debugImplementation plus releaseCompileOnly, so these previews compile in main
// source and Android Studio renders them from the debug variant, while release builds package none of it.

/** [S2Theme] with the sample library's covers ([SampleCovers]) in scope: wrap every `@Preview` in it. */
@Composable
fun S2Preview(
    darkTheme: Boolean = isSystemInDarkTheme(),
    artwork: PreviewArtwork = SampleCovers,
    content: @Composable () -> Unit,
) {
    S2Theme(darkTheme = darkTheme) {
        CompositionLocalProvider(LocalPreviewArtwork provides artwork, content = content)
    }
}

/** The sample covers for a [SampleSong], [SampleAlbum] or [SampleArtist]; anything else keeps its placeholder. */
object SampleCovers : PreviewArtwork {
    override fun image(model: Any): ImageBitmap? = when (model) {
        is SampleSong -> SampleLibrary.cover(model.albumId)
        is SampleAlbum -> SampleLibrary.cover(model.id)
        is SampleArtist -> SampleLibrary.cover(model.coverAlbumId)
        else -> null
    }
}
