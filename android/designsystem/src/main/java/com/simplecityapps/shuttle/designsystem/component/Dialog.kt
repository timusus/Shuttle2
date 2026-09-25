package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.preview.S2Preview

/**
 * A dialog: [title], an optional hero [icon], the [content] (a message, a [S2ChoiceList], a text
 * field), then the buttons. [confirmLabel] null leaves only the dismiss button, for a choice that
 * applies on tap. [destructive] draws the confirm button in `error`; [confirmEnabled] false
 * disables it until a form is valid. Built on `BasicAlertDialog`, so every kind of dialog shares
 * one layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun S2Dialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String? = null,
    onConfirm: () -> Unit = {},
    dismissLabel: String? = null,
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    icon: ImageVector? = null,
    content: @Composable () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest, modifier = modifier) {
        S2DialogContent(
            title = title,
            onDismiss = onDismissRequest,
            confirmLabel = confirmLabel,
            onConfirm = onConfirm,
            dismissLabel = dismissLabel,
            confirmEnabled = confirmEnabled,
            destructive = destructive,
            icon = icon,
            content = content,
        )
    }
}

/** The surface of an [S2Dialog] without its window, for laying a dialog out in place. */
@Composable
fun S2DialogContent(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String? = null,
    onConfirm: () -> Unit = {},
    dismissLabel: String? = null,
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    icon: ImageVector? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.sizeIn(minWidth = 280.dp, maxWidth = 560.dp),
        shape = AlertDialogDefaults.shape,
        color = AlertDialogDefaults.containerColor,
        tonalElevation = AlertDialogDefaults.TonalElevation,
    ) {
        Column(Modifier.padding(24.dp)) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (destructive) MaterialTheme.colorScheme.error else AlertDialogDefaults.iconContentColor,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .align(Alignment.CenterHorizontally),
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = AlertDialogDefaults.titleContentColor,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .align(if (icon != null) Alignment.CenterHorizontally else Alignment.Start),
            )
            CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.textContentColor) {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) { content() }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                if (dismissLabel != null) S2Button(dismissLabel, onDismiss, style = S2ButtonStyle.Text)
                if (confirmLabel != null) {
                    if (destructive) {
                        TextButton(
                            onClick = onConfirm,
                            enabled = confirmEnabled,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text(confirmLabel) }
                    } else {
                        S2Button(confirmLabel, onConfirm, style = S2ButtonStyle.Text, enabled = confirmEnabled)
                    }
                }
            }
        }
    }
}

/** A single-choice list for a dialog body: radio rows, [selectedIndex] checked; tapping a row selects it. */
@Composable
fun S2ChoiceList(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.selectableGroup()) {
        options.forEachIndexed { index, option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(selected = index == selectedIndex, onClick = { onSelect(index) }, role = Role.RadioButton),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                RadioButton(selected = index == selectedIndex, onClick = null)
                Text(option, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Preview
@Composable
private fun S2DialogContentPreview() {
    S2Preview {
        S2DialogContent(
            title = "Delete 3 songs?",
            onDismiss = {},
            confirmLabel = "Delete",
            dismissLabel = "Cancel",
            destructive = true,
            icon = Icons.Rounded.Delete,
        ) {
            Text("The files are removed from this device. This can't be undone.")
        }
    }
}
