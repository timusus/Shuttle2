package com.simplecityapps.shuttle.ui.screens.sources

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.component.S2ActionsSheet
import com.simplecityapps.shuttle.model.MediaProviderType

/** One row per provider type. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerTypePickerSheet(
    onTypeSelected: (MediaProviderType) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    S2ActionsSheet(
        title = stringResource(R.string.sources_connect_server),
        actions = ServerTypes.map { type ->
            S2Action(
                label = stringResource(type.titleRes),
                onClick = { onTypeSelected(type) },
                supporting = stringResource(type.descriptionRes),
                leading = { ProviderMark(type) },
            )
        },
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
    )
}
