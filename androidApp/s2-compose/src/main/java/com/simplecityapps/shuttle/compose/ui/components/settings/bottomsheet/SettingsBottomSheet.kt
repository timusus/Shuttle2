package com.simplecityapps.shuttle.compose.ui.components.settings.bottomsheet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.compose.ui.BottomSettings
import com.simplecityapps.shuttle.compose.ui.components.ThemedPreviewProvider
import com.simplecityapps.shuttle.compose.ui.icon
import com.simplecityapps.shuttle.compose.ui.nameResId
import com.simplecityapps.shuttle.compose.ui.theme.Theme

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SettingsBottomSheet(
    onItemSelected: (BottomSettings) -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.padding(top = 16.dp),
        content = {
            items(items = BottomSettings.values()) { item ->
                Surface(
                    onClick = {
                        onItemSelected(item)
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .height(56.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.size(24.dp))
                        Icon(imageVector = item.icon, contentDescription = item.name)
                        Spacer(modifier = Modifier.size(24.dp))
                        Text(stringResource(id = item.nameResId))
                    }
                }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview(@PreviewParameter(ThemedPreviewProvider::class) darkTheme: Boolean) {
    Theme(isDark = darkTheme) {
        SettingsBottomSheet()
    }
}