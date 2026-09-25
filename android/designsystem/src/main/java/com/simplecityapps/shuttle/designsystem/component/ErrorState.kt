package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.simplecityapps.shuttle.designsystem.theme.S2Theme

/** Something failed: what happened, and the [action] that recovers (Retry, Sign in). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ErrorState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector = Icons.Rounded.ErrorOutline,
    action: StateAction? = null,
) {
    StateMessage(
        title = title,
        message = message,
        icon = icon,
        iconShape = MaterialShapes.Burst,
        iconContainerColor = MaterialTheme.colorScheme.errorContainer,
        iconColor = MaterialTheme.colorScheme.onErrorContainer,
        action = action,
        modifier = modifier,
    )
}

@Preview
@Composable
private fun ErrorStatePreview() {
    S2Theme {
        ErrorState(
            title = "Couldn't reach Jellyfin",
            message = "Check the server address and your connection.",
            action = StateAction("Retry") {},
        )
    }
}
