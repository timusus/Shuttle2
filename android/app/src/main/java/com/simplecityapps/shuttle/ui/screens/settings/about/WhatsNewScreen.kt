package com.simplecityapps.shuttle.ui.screens.settings.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.LoadingState
import com.simplecityapps.shuttle.ui.screens.changelog.Changeset
import com.simplecityapps.shuttle.ui.screens.settings.SettingsScaffold
import java.text.DateFormat

@Composable
fun WhatsNewScreen(
    uiState: WhatsNewUiState,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsScaffold(title = stringResource(R.string.pref_view_changelog_title), onNavigateUp = onNavigateUp, modifier = modifier) {
        if (uiState.loading) {
            item(key = "loading") { LoadingState() }
        } else {
            items(uiState.changesets, key = { it.versionName }) { changeset -> ChangesetCard(changeset) }
        }
    }
}

@Composable
private fun ChangesetCard(changeset: Changeset) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(changeset.versionName, style = MaterialTheme.typography.titleLarge)
            Text(
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(changeset.date),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            changeset.notes.forEach { note -> Text(note, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium) }
            ChangeList(stringResource(R.string.changelog_features), changeset.features)
            ChangeList(stringResource(R.string.changelog_improvements), changeset.improvements)
            ChangeList(stringResource(R.string.changelog_bug_fixes), changeset.fixes)
        }
    }
}

@Composable
private fun ChangeList(
    title: String,
    changes: List<String>
) {
    if (changes.isEmpty()) return
    Text(title, Modifier.padding(top = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    changes.forEach { change -> Text("• $change", Modifier.padding(vertical = 2.dp), style = MaterialTheme.typography.bodyMedium) }
}
