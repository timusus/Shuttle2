package com.simplecityapps.shuttle.compose.ui.components.library

import androidx.annotation.PluralsRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.compose.R
import com.simplecityapps.shuttle.compose.ui.components.ThemedPreviewProvider
import com.simplecityapps.shuttle.compose.ui.theme.Theme
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.ui.library.GenreListViewModel

@Composable
fun GenreList(viewModel: GenreListViewModel) {

    val viewState by viewModel.viewState.collectAsState()
    GenreList(viewState)
}

@Composable
fun GenreList(viewState: GenreListViewModel.ViewState) {

    when (viewState) {
        is GenreListViewModel.ViewState.Loading -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                CircularProgressIndicator()
            }
        }
        is GenreListViewModel.ViewState.Ready -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = {
                    items(viewState.genres) { genre ->
                        Row() {
                            Column() {
                                Text(
                                    text = genre.name,
                                    style = MaterialTheme.typography.body1
                                )
                                Text(
                                    text = quantityStringResource(
                                        id = R.plurals.songsPlural,
                                        key = "count",
                                        quantity = genre.songs.size
                                    ),
                                    style = MaterialTheme.typography.body2
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

@Preview
@Composable
fun GenreListPreview(@PreviewParameter(ThemedPreviewProvider::class) darkTheme: Boolean) {
    Theme(isDark = darkTheme) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)) {
            GenreList(
                viewState = GenreListViewModel.ViewState.Ready(
                    listOf(
                        Genre("Rock", emptyList()),
                        Genre("Metal", emptyList()),
                        Genre("Electronic", emptyList()),
                    )
                )
            )
        }

    }
}

//Todo: This only works when there is one quantity placeholder. Look into how Phrase deals with this
@Composable
fun quantityStringResource(@PluralsRes id: Int, key: String, quantity: Int): String {
    return LocalContext.current.resources.getQuantityString(id, quantity).replace("{$key}", quantity.toString())
}