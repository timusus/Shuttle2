package com.simplecityapps.shuttle.ui.screens.library

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.EmptyState
import com.simplecityapps.shuttle.designsystem.component.S2Button
import com.simplecityapps.shuttle.designsystem.component.S2ButtonStyle
import com.simplecityapps.shuttle.designsystem.component.S2ProgressBar
import com.simplecityapps.shuttle.designsystem.component.S2Text
import com.simplecityapps.shuttle.designsystem.component.StateAction
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.ui.screens.sources.MusicAccess
import com.simplecityapps.shuttle.ui.screens.sources.MusicPermission
import dev.zacsweers.metrox.viewmodel.metroViewModel

/** Library state with no songs: offers device music access, shows scan progress, or reports that nothing was found. */
@Composable
fun LibraryEmptyScreen(
    state: LibraryAvailability.Empty,
    onAllowAccess: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onScan: () -> Unit,
    onConnectServer: () -> Unit,
    modifier: Modifier = Modifier,
    trialOffer: TrialOfferActions? = null,
    /** Opens Sources > This device to add a music folder; without it "No music found" offers Scan again instead. */
    onAddFolder: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LibraryEmptyContent(state, onAllowAccess, onOpenAppSettings, onScan, onConnectServer, onAddFolder, Modifier)
        if (trialOffer != null) TrialOffer(trialOffer, Modifier.align(Alignment.BottomCenter))
    }
}

/** What the trial offer's two buttons do. */
class TrialOfferActions(
    val onStart: () -> Unit,
    val onDismiss: () -> Unit,
    val startFailed: Boolean = false
)

/** What the trial offers and what it leaves alone, in the terms the Play listing and the paywall use. */
@Composable
private fun TrialOffer(
    actions: TrialOfferActions,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = S2Spacing.large, vertical = S2Spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(S2Spacing.small)
    ) {
        S2Text(
            text = stringResource(R.string.onboarding_trial_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actions.startFailed) {
            S2Text(
                text = stringResource(R.string.paywall_trial_start_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
        S2Button(
            text = stringResource(R.string.paywall_start_trial),
            onClick = actions.onStart,
            style = S2ButtonStyle.Tonal
        )
        S2Button(
            text = stringResource(R.string.onboarding_trial_not_now),
            onClick = actions.onDismiss,
            style = S2ButtonStyle.Text
        )
    }
}

@Composable
private fun LibraryEmptyContent(
    state: LibraryAvailability.Empty,
    onAllowAccess: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onScan: () -> Unit,
    onConnectServer: () -> Unit,
    onAddFolder: (() -> Unit)?,
    modifier: Modifier,
) {
    val connectServer = StateAction(stringResource(R.string.sources_connect_server), onConnectServer)
    val scan = state.scan
    when {
        scan != null -> Column(
            modifier = modifier.testTag("library-scanning"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(S2Spacing.small),
        ) {
            EmptyState(title = stringResource(if (state.hasServer) R.string.sources_syncing_title else R.string.sources_scanning_title), icon = Icons.Rounded.Search)
            val progressModifier = Modifier.widthIn(max = 320.dp).fillMaxWidth().padding(horizontal = S2Spacing.large)
            val fraction = scan.fraction
            S2ProgressBar(progress = fraction?.let { { it } }, modifier = progressModifier)
            scan.message?.let { message ->
                S2Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = S2Spacing.large),
                )
            }
        }

        // With a server connected, the device's permission isn't what's missing.
        state.access == MusicAccess.Granted || state.hasServer -> EmptyState(
            title = stringResource(R.string.sources_no_music_title),
            message = stringResource(if (onAddFolder != null) R.string.sources_no_music_message else R.string.sources_no_music_scan_message),
            action = if (onAddFolder != null) StateAction(stringResource(R.string.sources_add_folder), onAddFolder) else StateAction(stringResource(R.string.sources_scan_again), onScan),
            secondaryAction = connectServer,
            modifier = modifier.testTag("library-no-music"),
        )

        state.access == MusicAccess.PermanentlyDenied -> EmptyState(
            title = stringResource(R.string.sources_access_title),
            message = stringResource(R.string.sources_access_permanently_denied_message),
            icon = Icons.Rounded.Lock,
            action = StateAction(stringResource(R.string.sources_open_app_settings), onOpenAppSettings),
            secondaryAction = connectServer,
            modifier = modifier.testTag("library-access-permanently-denied"),
        )

        else -> EmptyState(
            title = stringResource(R.string.sources_access_title),
            message = stringResource(if (state.access == MusicAccess.Denied) R.string.sources_access_denied_message else R.string.sources_access_message),
            icon = Icons.Rounded.LibraryMusic,
            action = StateAction(stringResource(R.string.sources_allow_access), onAllowAccess),
            secondaryAction = connectServer,
            modifier = modifier.testTag("library-access"),
        )
    }
}

/** The trial offer for [LibraryEmptyScreen]'s `trialOffer`: null once the user has Pro, has used the trial or said "Not now". */
@Composable
fun rememberTrialOffer(): TrialOfferActions? {
    val viewModel: TrialOfferViewModel = metroViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    return if (state.offered) TrialOfferActions(viewModel::onStartTrial, viewModel::onDismiss, state.startFailed) else null
}

/** Asks for the music permission and opens the app's system settings page, on behalf of [LibraryEmptyScreen]. */
class MusicAccessRequests(val request: () -> Unit, val openAppSettings: () -> Unit)

/**
 * Reports the music permission to [viewModel] on every resume, so one granted from the settings page starts the scan,
 * and returns the requests [LibraryEmptyScreen] makes. The Library calls this whether or not it is empty: the
 * ViewModel can't tell the empty state apart from loading until the first report.
 */
@Composable
fun rememberMusicAccessRequests(viewModel: LibraryEmptyViewModel): MusicAccessRequests {
    val activity = LocalActivity.current
    val showRationale = { activity?.shouldShowRequestPermissionRationale(MusicPermission.name) == true }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        viewModel.onAccessResult(results[MusicPermission.name] == true, showRationale())
    }
    LifecycleResumeEffect(viewModel, activity) {
        activity?.let { viewModel.onAccessChecked(MusicPermission.isGranted(it), showRationale()) }
        onPauseOrDispose {}
    }
    return remember(launcher, activity) {
        MusicAccessRequests(request = { launcher.launch(MusicPermission.names) }, openAppSettings = { activity?.openAppSettings() })
    }
}

private fun Activity.openAppSettings() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
}
