package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.preview.S2Preview

/** How prominent a [SectionHeader] is. */
enum class SectionHeaderStyle {
    /** `titleSmall` on `primary`: a list's subheading, or a sticky letter. */
    Label,

    /** `titleLarge` on `onSurface`: a screen's own sections, such as Home's shelves. */
    Title,

    /** `headlineSmall` on `onSurface`: the lead section of a screen, over its [Title] sections, such as Home's first. */
    Headline,
}

/**
 * A section heading, a [SectionHeaderStyle.Label] unless [style] says otherwise, with an optional trailing [action]
 * ("See all") and an optional one-line [subtitle] under the title saying what the section is. It sits on an opaque [containerColor] (`surface`), so it also works as a sticky letter header;
 * pass the container's colour when it heads a list on another surface, such as the search view.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: String? = null,
    onAction: () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.surface,
    style: SectionHeaderStyle = SectionHeaderStyle.Label,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .heightIn(min = 48.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = when (style) {
                    SectionHeaderStyle.Label -> MaterialTheme.typography.titleSmall
                    SectionHeaderStyle.Title -> MaterialTheme.typography.titleLarge
                    SectionHeaderStyle.Headline -> MaterialTheme.typography.headlineSmall
                },
                color = when (style) {
                    SectionHeaderStyle.Label -> MaterialTheme.colorScheme.primary
                    SectionHeaderStyle.Title, SectionHeaderStyle.Headline -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (action != null) {
            S2Button(text = action, onClick = onAction, style = S2ButtonStyle.Text)
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
