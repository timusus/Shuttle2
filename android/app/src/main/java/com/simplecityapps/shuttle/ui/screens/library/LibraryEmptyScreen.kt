package com.simplecityapps.shuttle.ui.screens.library

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.EmptyState
import com.simplecityapps.shuttle.designsystem.component.StateAction
import com.simplecityapps.shuttle.ui.screens.sources.MusicAccess
import com.simplecityapps.shuttle.ui.screens.sources.MusicPermission

/**
 * What the Library shows while it has no songs (#379): the way to let S2 read this device's music, the scan's
 * progress once it can, or "No music found" when the scan came back empty. "Connect a server" is on offer throughout.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryEmptyScreen(
    state: LibraryAvailability.Empty,
    onAllowAccess: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onScan: () -> Unit,
    onConnectServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connectServer = StateAction(stringResource(R.string.sources_connect_server), onConnectServer)
    val scan = state.scan
    when {
        scan != null -> Column(
            modifier = modifier.testTag("library-scanning"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EmptyState(title = stringResource(R.string.sources_scanning_title), icon = Icons.Rounded.Search)
            val progressModifier = Modifier.widthIn(max = 320.dp).fillMaxWidth().padding(horizontal = 24.dp)
            if (scan.fraction != null) {
                LinearWavyProgressIndicator(progress = { scan.fraction }, modifier = progressModifier)
            } else {
                LinearWavyProgressIndicator(modifier = progressModifier)
            }
            scan.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }

        state.access == MusicAccess.Granted -> EmptyState(
            title = stringResource(R.string.sources_no_music_title),
            message = stringResource(R.string.sources_no_music_message),
            action = StateAction(stringResource(R.string.sources_scan_again), onScan),
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
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onAccessResult(granted, showRationale())
    }
    LifecycleResumeEffect(viewModel, activity) {
        activity?.let { viewModel.onAccessChecked(MusicPermission.isGranted(it), showRationale()) }
        onPauseOrDispose {}
    }
    return remember(launcher, activity) {
        MusicAccessRequests(request = { launcher.launch(MusicPermission.name) }, openAppSettings = { activity?.openAppSettings() })
    }
}

private fun Activity.openAppSettings() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
}
