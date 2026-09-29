package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.preview.S2Preview

import com.simplecityapps.shuttle.designsystem.theme.LocalCompactMode

/**
 * A section heading in `titleSmall` on `primary`, with an optional trailing [action] ("See all")
 * and/or a trailing icon button (a shelf's play button). It sits on an opaque [containerColor]
 * (`surface`), so it also works as a sticky letter header; pass the container's colour when it
 * heads a list on another surface, such as the search view.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.surface,
    iconAction: ImageVector? = null,
    iconActionContentDescription: String? = null,
    onIconAction: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .heightIn(min = if (LocalCompactMode.current) 36.dp else 48.dp)
            .padding(start = if (LocalCompactMode.current) 12.dp else 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (action != null) {
            S2Button(text = action, onClick = onAction, style = S2ButtonStyle.Text)
        }
        if (iconAction != null) {
            S2IconButton(icon = iconAction, contentDescription = iconActionContentDescription, onClick = onIconAction)
        }
    }
}

@Preview
@Composable
private fun SectionHeaderPreview() {
    S2Preview {
        SectionHeader(title = "Recently added", action = "See all")
    }
}
