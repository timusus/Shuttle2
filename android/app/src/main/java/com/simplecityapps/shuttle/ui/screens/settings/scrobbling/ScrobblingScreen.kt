package com.simplecityapps.shuttle.ui.screens.settings.scrobbling

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ActionsSetting
import com.simplecityapps.shuttle.designsystem.component.LinkSetting
import com.simplecityapps.shuttle.designsystem.component.S2Button
import com.simplecityapps.shuttle.designsystem.component.S2ButtonStyle
import com.simplecityapps.shuttle.designsystem.component.S2Text
import com.simplecityapps.shuttle.designsystem.component.SettingsGroup
import com.simplecityapps.shuttle.designsystem.component.SwitchSetting
import com.simplecityapps.shuttle.scrobbling.LastFmAccountState
import com.simplecityapps.shuttle.ui.screens.settings.SettingsScaffold

/**
 * Last.fm sign-in and the server-streams switch. Sign-in opens last.fm in the browser; coming back to this screen
 * finishes it, and the "I've approved it" button is there for when that didn't happen on its own.
 */
@Composable
fun ScrobblingScreen(
    uiState: ScrobblingUiState,
    onNavigateUp: () -> Unit,
    onSignIn: () -> Unit,
    onFinishSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onServerStreamsChange: (Boolean) -> Unit,
    onApprovalUrlOpened: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }
    val message = uiState.message?.let {
        stringResource(
            when (it) {
                ScrobblingMessage.NotApproved -> R.string.scrobbling_not_approved
                ScrobblingMessage.Expired -> R.string.scrobbling_expired
                ScrobblingMessage.Failed -> R.string.scrobbling_failed
            }
        )
    }

    LaunchedEffect(uiState.approvalUrl) {
        uiState.approvalUrl?.let {
            uriHandler.openUri(it)
            onApprovalUrlOpened()
        }
    }
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onMessageShown()
        }
    }
    // Back from the browser: finish the sign-in if one is waiting, so the user needn't press anything. Only a resume
    // that follows the app being stopped counts (the browser covers the whole activity; a notification shade or dialog
    // only pauses it), and the flag is saved so it survives process death while the user is away. A stop caused by a
    // configuration change (rotation, dark mode, locale) is the activity restarting, not the user leaving.
    val activity = LocalContext.current.findActivity()
    val awaiting = uiState.account == LastFmAccountState.AwaitingApproval
    var leftWhileAwaiting by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (awaiting && activity?.isChangingConfigurations != true) leftWhileAwaiting = true
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val returned = awaiting && leftWhileAwaiting
        leftWhileAwaiting = false
        if (returned) onFinishSignIn()
    }

    SettingsScaffold(
        title = stringResource(R.string.settings_scrobbling_title),
        onNavigateUp = onNavigateUp,
        modifier = modifier,
        snackbarHostState = snackbarHostState
    ) {
        if (uiState.account != LastFmAccountState.Unavailable) {
            item(key = "lastfm") {
                SettingsGroup(
                    rows = listOf(
                        { shapes: ListItemShapes ->
                            LinkSetting(
                                title = stringResource(R.string.scrobbling_lastfm),
                                onClick = {},
                                summary = when (val account = uiState.account) {
                                    is LastFmAccountState.SignedIn -> stringResource(R.string.scrobbling_lastfm_signed_in, account.username)
                                    LastFmAccountState.AwaitingApproval -> stringResource(R.string.scrobbling_lastfm_awaiting)
                                    else -> stringResource(R.string.scrobbling_lastfm_signed_out)
                                },
                                enabled = false,
                                shapes = shapes
                            )
                        },
                        { shapes: ListItemShapes ->
                            ActionsSetting(shapes = shapes) {
                                when (uiState.account) {
                                    is LastFmAccountState.SignedIn -> S2Button(
                                        text = stringResource(R.string.scrobbling_sign_out),
                                        onClick = onSignOut,
                                        style = S2ButtonStyle.Outlined,
                                        enabled = !uiState.busy
                                    )

                                    LastFmAccountState.AwaitingApproval -> {
                                        S2Button(
                                            text = stringResource(R.string.scrobbling_finish_sign_in),
                                            onClick = onFinishSignIn,
                                            enabled = !uiState.busy
                                        )
                                        S2Button(
                                            text = stringResource(R.string.scrobbling_sign_in),
                                            onClick = onSignIn,
                                            style = S2ButtonStyle.Outlined,
                                            enabled = !uiState.busy
                                        )
                                    }

                                    else -> S2Button(
                                        text = stringResource(R.string.scrobbling_sign_in),
                                        onClick = onSignIn,
                                        enabled = !uiState.busy
                                    )
                                }
                            }
                        }
                    )
                )
            }
        }
        item(key = "server_streams") {
            SettingsGroup(
                rows = listOf { shapes: ListItemShapes ->
                    SwitchSetting(
                        title = stringResource(R.string.scrobbling_server_streams_title),
                        checked = uiState.scrobbleServerStreams,
                        onCheckedChange = onServerStreamsChange,
                        summary = stringResource(R.string.scrobbling_server_streams_summary),
                        shapes = shapes
                    )
                }
            )
        }
        item(key = "attribution") {
            S2Text(stringResource(R.string.scrobbling_attribution))
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
