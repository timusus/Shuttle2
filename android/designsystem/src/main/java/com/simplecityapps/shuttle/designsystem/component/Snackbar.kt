package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.simplecityapps.shuttle.designsystem.R
import com.simplecityapps.shuttle.designsystem.preview.S2Preview

/**
 * A snackbar: the [message], an optional action ("Undo") and, with [onDismiss], a close button.
 * Screens don't call this directly; they show messages through an [S2SnackbarHost]'s state.
 */
@Composable
fun S2Snackbar(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    onDismiss: (() -> Unit)? = null,
) {
    Snackbar(
        modifier = modifier,
        action = actionLabel?.let {
            { TextButton(onClick = onAction, colors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionColor)) { Text(it) } }
        },
        dismissAction = onDismiss?.let {
            { IconButton(onClick = it) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ds_dismiss)) } }
        },
    ) {
        Text(message)
    }
}

/**
 * Where a screen's snackbars appear. Place it above the mini player and nav bar; the shell owns
 * that position. A snackbar with an action stays until dismissed only if the caller asks for
 * [SnackbarDuration.Indefinite].
 */
@Composable
fun S2SnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier) { data ->
        S2Snackbar(
            message = data.visuals.message,
            actionLabel = data.visuals.actionLabel,
            onAction = data::performAction,
            onDismiss = if (data.visuals.withDismissAction) data::dismiss else null,
        )
    }
}

@Preview
@Composable
private fun S2SnackbarPreview() {
    S2Preview {
        S2Snackbar("Removed from queue", actionLabel = "Undo")
    }
}
