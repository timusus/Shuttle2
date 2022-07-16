package com.simplecityapps.shuttle.compose.ui.components.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.*
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
import com.simplecityapps.shuttle.compose.ui.components.mediaimporter.EmbyConfigurationView
import com.simplecityapps.shuttle.compose.ui.components.mediaimporter.JellyfinConfigurationView
import com.simplecityapps.shuttle.compose.ui.components.mediaimporter.MediaImporter
import com.simplecityapps.shuttle.compose.ui.components.mediaprovider.MediaProviderBottomSheet
import com.simplecityapps.shuttle.compose.ui.components.mediaprovider.MediaProviderSelection
import com.simplecityapps.shuttle.compose.ui.theme.MaterialColors
import com.simplecityapps.shuttle.compose.ui.theme.Theme
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.requiresConfiguration
import com.simplecityapps.shuttle.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.launch

sealed class BottomSheetContent {
    object MediaProviderList : BottomSheetContent()
    data class ConfigureMediaProvider(val mediaProviderType: MediaProviderType) : BottomSheetContent()
}

enum class OnboardingScreen {
    MediaProviderSelection, MediaPermissions, MediaImporter
}

@ExperimentalMaterialApi
@Composable
fun Onboarding(viewModel: OnboardingViewModel, onboardingComplete: () -> Unit = {}) {
    val selectedMediaProviders by viewModel.selectedMediaProviders.collectAsState()

    Onboarding(
        selectedMediaProviders = selectedMediaProviders,
        onAddMediaProvider = { mediaProviderType ->
            viewModel.addMediaProvider(mediaProviderType)
        },
        onboardingComplete = {
            viewModel.setOnboardingComplete()
            onboardingComplete()
        }
    )
}

@ExperimentalMaterialApi
@OptIn(ExperimentalPagerApi::class, androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun Onboarding(
    selectedMediaProviders: List<MediaProviderType>,
    onAddMediaProvider: (MediaProviderType) -> Unit = {},
    onboardingComplete: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
    val scope = rememberCoroutineScope()

    var bottomSheetContent by remember { mutableStateOf<BottomSheetContent>(BottomSheetContent.MediaProviderList) }

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetContent = {
            when (bottomSheetContent) {
                is BottomSheetContent.MediaProviderList -> {
                    MediaProviderBottomSheet(
                        mediaProviders = MediaProviderType.values().toMutableList() - selectedMediaProviders.toSet(),
                        onMediaProviderTypeSelected = { mediaProviderType ->
                            onAddMediaProvider(mediaProviderType)
                            scope.launch {
                                if (mediaProviderType.requiresConfiguration()) {
                                    bottomSheetContent = BottomSheetContent.ConfigureMediaProvider(mediaProviderType)
                                    sheetState.animateTo(ModalBottomSheetValue.Expanded)
                                } else {
                                    sheetState.hide()
                                }
                            }
                        }
                    )
                }
                is BottomSheetContent.ConfigureMediaProvider -> {
                    when ((bottomSheetContent as BottomSheetContent.ConfigureMediaProvider).mediaProviderType) {
                        MediaProviderType.Emby -> {
                            EmbyConfigurationView(viewModel = hiltViewModel(), onDismiss = {
                                scope.launch {
                                    sheetState.hide()
                                }
                            })
                        }
                        MediaProviderType.Jellyfin -> {
                            JellyfinConfigurationView(viewModel = hiltViewModel(), onDismiss = {
                                scope.launch {
                                    sheetState.hide()
                                }
                            })
                        }
                    }
                }
            }
        }) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialColors.background)
        ) {
            val onboardingScreens = remember { mutableStateListOf(OnboardingScreen.MediaProviderSelection, OnboardingScreen.MediaImporter) }
            val pagerState = rememberPagerState(pageCount = onboardingScreens.size)
            HorizontalPager(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = pagerState,
                dragEnabled = false
            ) { page ->
                when (onboardingScreens[page]) {
                    OnboardingScreen.MediaProviderSelection -> {
                        MediaProviderSelection(
                            viewModel = hiltViewModel(),
                            onAddMediaProvider = {
                                bottomSheetContent = BottomSheetContent.MediaProviderList
                                scope.launch {
                                    sheetState.animateTo(ModalBottomSheetValue.Expanded)
                                }
                            },
                            onConfigureMediaProvider = { mediaProviderType ->
                                bottomSheetContent = BottomSheetContent.ConfigureMediaProvider(mediaProviderType)
                                scope.launch {
                                    sheetState.animateTo(ModalBottomSheetValue.Expanded)
                                }
                            }
                        )
                    }
                    OnboardingScreen.MediaPermissions -> {

                    }
                    OnboardingScreen.MediaImporter -> {
                        MediaImporter(viewModel = hiltViewModel(), isVisible = pagerState.currentPage == page)
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
                    if (pagerState.currentPage == pagerState.pageCount - 1) {
                        onboardingComplete()
                    } else {
                        scope.launch {
                            pagerState.scrollToPage(pagerState.currentPage + 1)
                        }
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