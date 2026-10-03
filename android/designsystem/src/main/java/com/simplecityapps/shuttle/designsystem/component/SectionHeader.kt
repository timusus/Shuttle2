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
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.designsystem.theme.S2TouchTarget
import com.simplecityapps.shuttle.designsystem.theme.groupHeader
import com.simplecityapps.shuttle.designsystem.theme.leadSectionTitle
import com.simplecityapps.shuttle.designsystem.theme.rowSubtitle
import com.simplecityapps.shuttle.designsystem.theme.sectionTitle

/** How prominent a [SectionHeader] is. */
enum class SectionHeaderStyle {
    /** `groupHeader` on `primary`: a list's subheading, or a sticky letter. */
    Label,

    /** `sectionTitle` on `onSurface`: a screen's own sections, such as Home's shelves. */
    Title,

    /** `leadSectionTitle` on `onSurface`: the lead section of a screen, over its [Title] sections, such as Home's first. */
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
            .heightIn(min = S2TouchTarget.minimum)
            .padding(start = S2Spacing.medium, end = S2Spacing.xsmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = when (style) {
                    SectionHeaderStyle.Label -> MaterialTheme.typography.groupHeader
                    SectionHeaderStyle.Title -> MaterialTheme.typography.sectionTitle
                    SectionHeaderStyle.Headline -> MaterialTheme.typography.leadSectionTitle
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
                    style = MaterialTheme.typography.rowSubtitle,
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
