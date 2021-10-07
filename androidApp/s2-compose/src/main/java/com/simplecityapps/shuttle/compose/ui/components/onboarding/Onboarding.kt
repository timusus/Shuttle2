package com.simplecityapps.shuttle.compose.ui.components.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.HorizontalPagerIndicator
import com.google.accompanist.pager.rememberPagerState
import com.simplecityapps.shuttle.compose.R
import com.simplecityapps.shuttle.compose.ui.components.ThemedPreviewProvider
import com.simplecityapps.shuttle.compose.ui.components.mediaprovider.MediaProviderBottomSheet
import com.simplecityapps.shuttle.compose.ui.components.mediaprovider.MediaProviderSelection
import com.simplecityapps.shuttle.compose.ui.components.mediascanner.MediaScanner
import com.simplecityapps.shuttle.compose.ui.theme.MaterialColors
import com.simplecityapps.shuttle.compose.ui.theme.Theme
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.launch

@ExperimentalMaterialApi
@Composable
fun Onboarding(viewModel: OnboardingViewModel) {
    val selectedMediaProviders by viewModel.selectedMediaProviders.collectAsState()

    Onboarding(
        selectedMediaProviders = selectedMediaProviders,
        onAddMediaProvider = { mediaProviderType ->
            viewModel.addMediaProvider(mediaProviderType)
        })
}

@ExperimentalMaterialApi
@OptIn(ExperimentalPagerApi::class, androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun Onboarding(
    selectedMediaProviders: List<MediaProviderType>,
    onAddMediaProvider: (MediaProviderType) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
    val scope = rememberCoroutineScope()

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetContent = {
            MediaProviderBottomSheet(
                mediaProviders = MediaProviderType.values().toMutableList() - selectedMediaProviders,
                onMediaProviderTypeSelected = {
                    onAddMediaProvider(it)
                    scope.launch {
                        sheetState.hide()
                    }
                }
            )
        }) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialColors.background)
        ) {
            val pagerState = rememberPagerState(pageCount = 2)
            HorizontalPager(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = pagerState,
                dragEnabled = false
            ) { page ->
                when (page) {
                    0 -> {
                        MediaProviderSelection(
                            viewModel = hiltViewModel(),
                            onAddMediaProviderClicked = {
                                scope.launch {
                                    sheetState.animateTo(ModalBottomSheetValue.Expanded)
                                }
                            })
                    }
                    1 -> {
                        MediaScanner()
                    }
                }
            }

            HorizontalPagerIndicator(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(16.dp),
                pagerState = pagerState,
                activeColor = MaterialColors.onSurface
            )

            Button(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(bottom = 24.dp, end = 24.dp),
                enabled = selectedMediaProviders.isNotEmpty(),
                onClick = {
                    scope.launch {
                        pagerState.scrollToPage(pagerState.currentPage + 1)
                    }
                }) {
                Text(stringResource(id = R.string.onboarding_button_next))
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Preview(showBackground = true)
@Composable
fun OnboardingPreview(@PreviewParameter(ThemedPreviewProvider::class) darkTheme: Boolean) {
    Theme(isDark = darkTheme) {
        Onboarding(listOf(MediaProviderType.MediaStore, MediaProviderType.Shuttle, MediaProviderType.Emby))
    }
}