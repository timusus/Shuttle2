package com.simplecityapps.shuttle.compose.ui.components.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.tooling.preview.Device
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.simplecityapps.shuttle.common.mediaprovider.MockData
import com.simplecityapps.shuttle.compose.ui.components.ThemedPreviewProvider
import com.simplecityapps.shuttle.compose.ui.theme.Theme
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.library.ArtistListViewModel
import com.simplecityapps.shuttle.ui.library.toArtistViews

@Composable
fun ArtistList(
    viewModel: ArtistListViewModel
) {
    val viewState = viewModel.viewState.collectAsState()
}

@Composable
fun ArtistList(
    viewState: ArtistListViewModel.ViewState
) {
    when (viewState) {
        is ArtistListViewModel.ViewState.Ready -> {
            LazyColumn() {
                items(viewState.artists) { artistView ->
                    Column {
                        Text(text = artistView.title)
                        Text(text = artistView.songs.size.toString())
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, device = Devices.PIXEL_4)
@Composable
fun SongCollectionPreview(@PreviewParameter(ThemedPreviewProvider::class) darkTheme: Boolean) {
    Theme(isDark = darkTheme) {
        ArtistList(
            ArtistListViewModel.ViewState.Ready(
                MockData.songs.toArtistViews()
            )
        )
    }
}


fun List<Song>.albumCount() = distinctBy { song -> song.album }.count()
fun List<Song>.artistCount() = flatMap { song -> song.artists }.distinct().count()
fun List<Song>.albumArtistCount() = distinctBy { song -> song.albumArtist }.count()