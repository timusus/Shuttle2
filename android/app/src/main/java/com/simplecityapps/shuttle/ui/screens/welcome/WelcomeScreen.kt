package com.simplecityapps.shuttle.ui.screens.welcome

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.S2Button
import com.simplecityapps.shuttle.designsystem.component.S2ButtonSize
import com.simplecityapps.shuttle.designsystem.component.S2ButtonStyle
import com.simplecityapps.shuttle.designsystem.component.S2Surface
import com.simplecityapps.shuttle.designsystem.component.S2Text
import com.simplecityapps.shuttle.designsystem.theme.S2ContentWidth
import com.simplecityapps.shuttle.designsystem.theme.S2IconSize
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.ui.screens.sources.MusicPermission
import com.simplecityapps.shuttle.ui.screens.sources.ServerTypePickerRoute
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * Shows the first run's welcome in place of [content] until the user picks where their music comes from (#1040).
 * [onOpenPrivacy] opens Settings > Privacy in the shell that replaces it.
 */
@Composable
fun WelcomeGate(
    onOpenPrivacy: () -> Unit,
    content: @Composable () -> Unit,
) {
    val viewModel: WelcomeViewModel = metroViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    if (!uiState.showWelcome) {
        content()
        return
    }
    val activity = LocalActivity.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        viewModel.onMusicAccessResult(results[MusicPermission.name] == true, activity?.shouldShowRequestPermissionRationale(MusicPermission.name) == true)
    }
    WelcomeFlow(
        onRequestMusicAccess = { launcher.launch(MusicPermission.names) },
        onOpenPrivacy = {
            viewModel.onOpenPrivacy()
            onOpenPrivacy()
        },
        serverPicker = { onDismissRequest ->
            ServerTypePickerRoute(
                onDismissRequest = {
                    onDismissRequest()
                    viewModel.onServerFlowClosed()
                },
            )
        },
    )
}

/** The welcome, with [serverPicker] over it once "Connect a server" is tapped, until it calls its dismiss. */
@Composable
internal fun WelcomeFlow(
    onRequestMusicAccess: () -> Unit,
    onOpenPrivacy: () -> Unit,
    serverPicker: @Composable (onDismissRequest: () -> Unit) -> Unit,
) {
    var connectingServer by rememberSaveable { mutableStateOf(false) }
    WelcomeScreen(
        onUseThisDevice = onRequestMusicAccess,
        onConnectServer = { connectingServer = true },
        onOpenPrivacy = onOpenPrivacy,
    )
    if (connectingServer) {
        serverPicker { connectingServer = false }
    }
}

@Composable
fun WelcomeScreen(
    onUseThisDevice: () -> Unit,
    onConnectServer: () -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    S2Surface(modifier.fillMaxSize(), shape = RectangleShape, color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.safeDrawingPadding(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .widthIn(max = S2ContentWidth.readable)
                    .verticalScroll(rememberScrollState())
                    .padding(S2Spacing.large),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(S2Spacing.medium),
            ) {
                AppIcon()
                S2Text(
                    text = stringResource(R.string.welcome_title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                S2Text(
                    text = stringResource(R.string.welcome_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Column(
                    modifier = Modifier.padding(top = S2Spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(S2Spacing.small),
                ) {
                    S2Button(
                        text = stringResource(R.string.welcome_this_device),
                        onClick = onUseThisDevice,
                        size = S2ButtonSize.Medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    S2Button(
                        text = stringResource(R.string.sources_connect_server),
                        onClick = onConnectServer,
                        style = S2ButtonStyle.Outlined,
                        size = S2ButtonSize.Medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                S2Text(
                    text = stringResource(R.string.welcome_telemetry),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = S2Spacing.medium),
                )
                S2Button(
                    text = stringResource(R.string.welcome_privacy_settings),
                    onClick = onOpenPrivacy,
                    style = S2ButtonStyle.Text,
                )
            }
        }
    }
}

/** The launcher icon's layers, drawn as the launcher would. */
@Composable
private fun AppIcon() {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = Modifier
            .size(S2IconSize.brand)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(colorResource(R.color.ic_launcher_background)),
    )
}
