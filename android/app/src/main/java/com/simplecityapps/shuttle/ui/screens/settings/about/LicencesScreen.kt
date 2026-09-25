package com.simplecityapps.shuttle.ui.screens.settings.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.InfoSetting
import com.simplecityapps.shuttle.designsystem.component.LinkSetting
import com.simplecityapps.shuttle.designsystem.component.LoadingState
import com.simplecityapps.shuttle.ui.screens.settings.SettingsScaffold

/** The bundled open source libraries; a row with a website opens it. */
@Composable
fun LicencesScreen(
    uiState: LicencesUiState,
    onNavigateUp: () -> Unit,
    onOpenWebsite: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsScaffold(
        title = stringResource(R.string.pref_view_licenses_title),
        onNavigateUp = onNavigateUp,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (uiState.loading) {
            item(key = "loading") { LoadingState() }
        } else {
            items(uiState.licences, key = { it.name + it.version }) { licence ->
                val summary = listOfNotNull(licence.version, licence.licence).joinToString(" · ").ifEmpty { stringResource(com.simplecityapps.core.R.string.unknown) }
                val website = licence.website
                if (website != null) {
                    LinkSetting(title = licence.name, summary = summary, onClick = { onOpenWebsite(website) })
                } else {
                    InfoSetting(title = licence.name, summary = summary)
                }
            }
        }
    }
}
