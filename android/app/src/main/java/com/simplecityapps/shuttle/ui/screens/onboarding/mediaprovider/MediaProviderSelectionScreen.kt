package com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.core.view.postDelayed
import androidx.fragment.app.Fragment
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplecityapps.mediaprovider.iconResId
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingChild
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingPage
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingParent
import com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider.emby.EmbyConfigurationDialog
import com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider.plex.PlexConfigurationDialog
import com.simplecityapps.shuttle.ui.snapshot.Snapshot
import com.simplecityapps.shuttle.ui.theme.AppTheme
import com.simplecityapps.shuttle.ui.theme.ColorSchemePreviewParameterProvider

class MediaProviderSelectionScreenFragment :
    Fragment(),
    OnboardingChild {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val onboardingParent = parentFragment as OnboardingParent
        return ComposeView(requireContext()).apply {
            postDelayed(50) {
                onboardingParent.hideBackButton()
                onboardingParent.toggleNextButton(true)
                onboardingParent.showNextButton(getString(R.string.onboarding_button_next))
            }
            setContent {
                AppTheme {
                    MediaProviderSelectionScreen()
                }
            }
        }
    }

    override val page = OnboardingPage.MediaProviderSelector

    override fun getParent() = parentFragment as? OnboardingParent

    override fun handleNextButtonClick() {
        getParent()?.goToNext()
    }
}

@Composable
private fun MediaProviderSelectionScreen(
    modifier: Modifier = Modifier,
    viewModel: MediaProviderSelectionViewModel = hiltViewModel()
) {
    val mediaProviders by viewModel.mediaProviders.collectAsStateWithLifecycle()
    val unAddedMediaProviders by viewModel.unAddedMediaProviders.collectAsStateWithLifecycle()
    val configureMediaProvider by viewModel.configureMediaProvider.collectAsStateWithLifecycle()
    val showAddProviderDialog by viewModel.showAddMediaProviderDialog.collectAsStateWithLifecycle()
    val showProviderOverflowMenu by viewModel.showProviderOverflowMenu.collectAsStateWithLifecycle()

    if (configureMediaProvider != null) {
        when (configureMediaProvider!!) {
            MediaProviderType.Shuttle -> TODO()
            MediaProviderType.MediaStore -> TODO()
            MediaProviderType.Emby -> EmbyConfigurationDialog(onDismissRequest = viewModel::onConsumeConfigureMediaProvider)
            MediaProviderType.Jellyfin -> TODO()
            MediaProviderType.Plex -> PlexConfigurationDialog(onDismissRequest = viewModel::onConsumeConfigureMediaProvider)
        }
    }

    MediaProviderSelectionScreen(
        modifier = modifier,
        mediaProviders = mediaProviders,
        showAddProviderDialog = showAddProviderDialog,
        unAddedMediaProviders = unAddedMediaProviders,
        showProviderOverflowMenu = showProviderOverflowMenu,
        onMediaProviderTypeClick = viewModel::onAddMediaProvider,
        onRemoveProviderClick = viewModel::onRemoveMediaProvider,
        onConfigureProviderClick = viewModel::onConfigureProviderClick,
        onAddMediaProviderClick = viewModel::onAddMediaProviderClicked,
        onProviderOverflowMenuClick = viewModel::onMediaProviderOverflowMenuClicked,
        onDismissOverflowMenuRequest = viewModel::onDismissMediaProviderOverflowMenu,
        onDismissAddMediaProviderRequest = viewModel::onDismissAddMediaProviderRequest
    )
}

@Composable
private fun MediaProviderSelectionScreen(
    showAddProviderDialog: Boolean,
    mediaProviders: List<MediaProviderType>,
    unAddedMediaProviders: List<MediaProviderType>,
    showProviderOverflowMenu: MediaProviderType?,
    onRemoveProviderClick: () -> Unit,
    onAddMediaProviderClick: () -> Unit,
    onDismissOverflowMenuRequest: () -> Unit,
    onDismissAddMediaProviderRequest: () -> Unit,
    onConfigureProviderClick: (MediaProviderType) -> Unit,
    onMediaProviderTypeClick: (MediaProviderType) -> Unit,
    onProviderOverflowMenuClick: (MediaProviderType) -> Unit,
    modifier: Modifier = Modifier
) {
    if (showAddProviderDialog) {
        AddMediaProviderDialog(
            providers = unAddedMediaProviders,
            onDismissRequest = onDismissAddMediaProviderRequest,
            onMediaProviderTypeClick = onMediaProviderTypeClick
        )
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(24.dp)
    ) {
        Text(
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineSmall,
            text = stringResource(R.string.media_provider_toolbar_title_onboarding)
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            text = stringResource(R.string.onboarding_media_selection_subtitle)
        )
        Spacer(modifier = Modifier.height(24.dp))

        LazyMediaProviderTypeColumn(
            providers = mediaProviders,
            modifier = Modifier.weight(1f),
            onRemoveProviderClick = onRemoveProviderClick,
            onOverflowMenuClick = onProviderOverflowMenuClick,
            showProviderOverflowMenu = showProviderOverflowMenu,
            onConfigureProviderClick = onConfigureProviderClick,
            onDismissOverflowMenuRequest = onDismissOverflowMenuRequest
        )

        AnimatedVisibility(visible = mediaProviders.size < MediaProviderType.entries.size) {
            OutlinedButton(
                onClick = onAddMediaProviderClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally)
            ) {
                Text(text = stringResource(R.string.media_provider_add))
            }
        }
    }
}

@Composable
private fun AddMediaProviderDialog(
    onDismissRequest: () -> Unit,
    providers: List<MediaProviderType>,
    onMediaProviderTypeClick: (MediaProviderType) -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(R.string.media_provider_add)) },
        text = {
            LazyMediaProviderTypeColumn(
                providers = providers,
                onMediaProviderTypeClick = onMediaProviderTypeClick
            )
        },
        confirmButton = {
            TextButton(onDismissRequest) {
                Text(text = stringResource(R.string.dialog_button_close))
            }
        }
    )
}

@Composable
private fun LazyMediaProviderTypeColumn(
    providers: List<MediaProviderType>,
    onMediaProviderTypeClick: (MediaProviderType) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        providers
            .groupBy(MediaProviderType::remote)
            .forEach { (remote, providers) ->
                item {
                    MediaProviderItemHeader(
                        remote = remote,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
                items(providers) { provider ->
                    MediaProviderTypeItem(
                        provider = provider,
                        modifier = Modifier.clickable(onClick = { onMediaProviderTypeClick(provider) })
                    )
                }
            }
    }
}

@Composable
private fun LazyMediaProviderTypeColumn(
    providers: List<MediaProviderType>,
    showProviderOverflowMenu: MediaProviderType?,
    onRemoveProviderClick: () -> Unit,
    onDismissOverflowMenuRequest: () -> Unit,
    onOverflowMenuClick: (MediaProviderType) -> Unit,
    onConfigureProviderClick: (MediaProviderType) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        providers
            .groupBy(MediaProviderType::remote)
            .forEach { (remote, providers) ->
                item { MediaProviderItemHeader(remote = remote) }
                items(providers) { provider ->
                    MediaProviderTypeItem(
                        provider = provider,
                        onRemoveProviderClick = onRemoveProviderClick,
                        onOverflowMenuClick = { onOverflowMenuClick(provider) },
                        onDismissOverflowMenuRequest = onDismissOverflowMenuRequest,
                        showProviderOverflowMenu = showProviderOverflowMenu == provider,
                        onConfigureProviderClick = { onConfigureProviderClick(provider) }
                    )
                }
            }
    }
}

@Composable
private fun MediaProviderItemHeader(
    remote: Boolean,
    modifier: Modifier = Modifier
) {
    val headerTitle = if (remote) {
        stringResource(R.string.media_provider_type_remote)
    } else {
        stringResource(R.string.media_provider_type_local)
    }
    Text(
        text = headerTitle,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun MediaProviderTypeItem(
    provider: MediaProviderType,
    showProviderOverflowMenu: Boolean,
    onOverflowMenuClick: () -> Unit,
    onRemoveProviderClick: () -> Unit,
    onConfigureProviderClick: () -> Unit,
    onDismissOverflowMenuRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    MediaProviderTypeItem(
        modifier = modifier,
        provider = provider,
        trailingContent = {
            IconButton(onClick = onOverflowMenuClick) {
                Icon(
                    contentDescription = null,
                    imageVector = Icons.Default.MoreVert,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            DropdownMenu(
                expanded = showProviderOverflowMenu,
                onDismissRequest = onDismissOverflowMenuRequest
            ) {
                if (provider != MediaProviderType.MediaStore) {
                    DropdownMenuItem(
                        onClick = onConfigureProviderClick,
                        text = { Text(stringResource(R.string.menu_title_media_provider_configure)) }
                    )
                }
                DropdownMenuItem(
                    onClick = onRemoveProviderClick,
                    text = { Text(stringResource(R.string.menu_title_remove)) }
                )
            }
        }
    )
}

@Composable
private fun MediaProviderTypeItem(
    provider: MediaProviderType,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val description: String = when (provider) {
        MediaProviderType.Shuttle -> stringResource(com.simplecityapps.mediaprovider.R.string.media_provider_description_s2)
        MediaProviderType.MediaStore -> stringResource(com.simplecityapps.mediaprovider.R.string.media_provider_description_media_store)
        MediaProviderType.Jellyfin -> stringResource(com.simplecityapps.mediaprovider.R.string.media_provider_description_jellyfin)
        MediaProviderType.Emby -> stringResource(com.simplecityapps.mediaprovider.R.string.media_provider_description_emby)
        MediaProviderType.Plex -> stringResource(com.simplecityapps.mediaprovider.R.string.media_provider_description_plex)
    }
    ListItem(
        trailingContent = trailingContent,
        headlineContent = { Text(text = provider.name) },
        supportingContent = { Text(text = description) },
        modifier = modifier.clip(RoundedCornerShape(8.dp)),
        leadingContent = {
            Icon(
                tint = Color.Unspecified,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                painter = painterResource(provider.iconResId())
            )
        }
    )
}

@Snapshot
@Preview
@Composable
private fun Preview(@PreviewParameter(ColorSchemePreviewParameterProvider::class) colorScheme: ColorScheme) {
    MaterialTheme(colorScheme = colorScheme) {
        MediaProviderSelectionScreen(
            onRemoveProviderClick = {},
            onAddMediaProviderClick = {},
            onMediaProviderTypeClick = {},
            onConfigureProviderClick = {},
            onProviderOverflowMenuClick = {},
            onDismissOverflowMenuRequest = {},
            onDismissAddMediaProviderRequest = {},
            showAddProviderDialog = false,
            showProviderOverflowMenu = null,
            unAddedMediaProviders = emptyList(),
            mediaProviders = MediaProviderType.entries
        )
    }
}

@Snapshot
@Preview
@Composable
private fun AddMediaProviderDialog(@PreviewParameter(ColorSchemePreviewParameterProvider::class) colorScheme: ColorScheme) {
    MaterialTheme(colorScheme = colorScheme) {
        MediaProviderSelectionScreen(
            onRemoveProviderClick = {},
            onAddMediaProviderClick = {},
            onMediaProviderTypeClick = {},
            onConfigureProviderClick = {},
            onProviderOverflowMenuClick = {},
            onDismissOverflowMenuRequest = {},
            onDismissAddMediaProviderRequest = {},
            showAddProviderDialog = true,
            showProviderOverflowMenu = null,
            mediaProviders = listOf(MediaProviderType.MediaStore),
            unAddedMediaProviders = MediaProviderType.entries - MediaProviderType.MediaStore
        )
    }
}
