package com.simplecityapps.shuttle.compose.ui.components.mediaprovider

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplecityapps.shuttle.compose.R
import com.simplecityapps.shuttle.compose.ui.components.ThemedPreviewProvider
import com.simplecityapps.shuttle.compose.ui.theme.Theme
import com.simplecityapps.shuttle.model.MediaProviderType

@Composable
fun MediaProviderListItem(
    modifier: Modifier = Modifier,
    mediaProviderType: MediaProviderType,
    showOverflow: Boolean,
    onRemove: () -> Unit = {},
    onConfigure: () -> Unit = {}
) {
    var showDropdown by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier.size(28.dp),
            painter = painterResource(id = mediaProviderType.iconResId),
            contentDescription = stringResource(id = mediaProviderType.titleResId),
            tint = Color.Unspecified
        )
        Spacer(modifier = Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(id = mediaProviderType.titleResId),
                style = MaterialTheme.typography.body1
            )
            Text(
                text = stringResource(id = mediaProviderType.descriptionResId),
                style = MaterialTheme.typography.body2,
                fontSize = 12.sp
            )
        }
        if (showOverflow) {
            Box {
                IconButton(
                    onClick = { showDropdown = true }) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "Overflow"
                    )
                }
                if (showDropdown) {
                    DropDownMenu(
                        expanded = true,
                        mediaProviderType = mediaProviderType,
                        onDismiss = { showDropdown = false },
                        onConfigure = {
                            showDropdown = false
                            onConfigure()
                        },
                        onRemove = {
                            showDropdown = false
                            onRemove()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DropDownMenu(
    expanded: Boolean,
    mediaProviderType: MediaProviderType,
    onDismiss: () -> Unit = {},
    onConfigure: () -> Unit = {},
    onRemove: () -> Unit = {}
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { onDismiss() }) {
        if (mediaProviderType != MediaProviderType.MediaStore) {
            DropdownMenuItem(onClick = { onConfigure() }) {
                Text(stringResource(id = R.string.menu_title_media_provider_configure))
            }
        }
        DropdownMenuItem(onClick = { onRemove() }) {
            Text(stringResource(id = R.string.media_provider_dialog_button_remove))
        }
    }
}


@Preview(showBackground = true)
@Composable
fun MediaProviderListItemPreview(@PreviewParameter(ThemedPreviewProvider::class) darkTheme: Boolean) {
    Theme(isDark = darkTheme) {
        MediaProviderListItem(
            mediaProviderType = MediaProviderType.MediaStore,
            showOverflow = true
        )
    }
}