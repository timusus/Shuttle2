package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.simplecityapps.shuttle.designsystem.preview.S2Preview

/** A button a state message offers, such as "Add a music folder" or "Retry". */
class StateAction(val label: String, val onClick: () -> Unit)

/** Why a screen has nothing to show, with an optional [action] that fixes it and a [secondaryAction] beside it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector = Icons.Rounded.LibraryMusic,
    action: StateAction? = null,
    secondaryAction: StateAction? = null,
) {
    StateMessage(
        title = title,
        message = message,
        icon = icon,
        iconShape = MaterialShapes.Cookie9Sided,
        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        action = action,
        secondaryAction = secondaryAction,
        modifier = modifier,
    )
}

/** The layout empty and error states share: a `MaterialShapes` icon container, text, up to two actions. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StateMessage(
    title: String,
    message: String?,
    icon: ImageVector,
    iconShape: RoundedPolygon,
    iconContainerColor: Color,
    iconColor: Color,
    action: StateAction?,
    modifier: Modifier = Modifier,
    secondaryAction: StateAction? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(iconShape.toShape())
                .background(iconContainerColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(40.dp))
        }
        Column(
            modifier = Modifier.widthIn(max = 360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (action != null) {
            S2Button(text = action.label, onClick = action.onClick)
        }
        if (secondaryAction != null) {
            S2Button(text = secondaryAction.label, onClick = secondaryAction.onClick, style = S2ButtonStyle.Text)
        }
    }
}

@Preview
@Composable
private fun EmptyStatePreview() {
    S2Preview {
        EmptyState(
            title = "No songs yet",
            message = "Add a music folder to start building your library.",
            action = StateAction("Add a folder") {},
        )
    }
}
