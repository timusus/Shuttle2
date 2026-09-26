package com.simplecityapps.shuttle.ui.screens.tageditor

import android.app.Activity
import android.content.res.Resources
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ErrorState
import com.simplecityapps.shuttle.designsystem.component.LoadingState
import com.simplecityapps.shuttle.designsystem.component.S2Button
import com.simplecityapps.shuttle.designsystem.component.S2Dialog
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2TopBar
import com.simplecityapps.shuttle.designsystem.component.SettingsHeader
import com.simplecityapps.shuttle.designsystem.component.StateAction
import com.simplecityapps.shuttle.ui.common.ConsumeEvents
import com.simplecityapps.shuttle.ui.shell.LocalShellSnackbarHostState
import kotlinx.coroutines.launch

/** The tag editor route: loads the songs' tags, saves the changes, reports how the save went and closes. */
@Composable
fun TagEditorDestination(
    route: TagEditorRoute,
    onNavigateUp: () -> Unit,
) {
    val viewModel = hiltViewModel<TagEditorViewModel, TagEditorViewModel.Factory> { it.create(route.songIds) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbarHostState = LocalShellSnackbarHostState.current
    // The snackbar outlives this screen, so it runs in the activity's scope rather than the entry's.
    val activityScope = (LocalActivity.current as? ComponentActivity)?.lifecycleScope
    val writeConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onWriteConsent(granted = result.resultCode == Activity.RESULT_OK)
    }

    ConsumeEvents((uiState as? TagEditorUiState.Editing)?.events.orEmpty(), viewModel::onEventHandled) { event ->
        when (event) {
            is TagEditorEvent.RequestWriteConsent -> writeConsent.launch(IntentSenderRequest.Builder(event.intentSender).build())

            is TagEditorEvent.Saved -> {
                val message = event.result.message(resources)
                activityScope?.launch { snackbarHostState.showSnackbar(message) }
                onNavigateUp()
            }
        }
    }

    TagEditorScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onFieldChange = viewModel::onFieldChange,
        onFieldReset = viewModel::onFieldReset,
        onSave = viewModel::onSave,
    )
}

private fun TagWriteResult.message(resources: Resources): String = if (failed.isEmpty()) {
    resources.getQuantityString(R.plurals.edit_tags_success, updated.size, updated.size)
} else {
    val total = updated.size + failed.size
    resources.getQuantityString(R.plurals.edit_tags_failure, total, failed.size, total)
}

/**
 * Tag editor (inventory §5): the tags of one song, or the tags several songs share. A field the songs disagree on
 * shows "Multiple values" and is only written if the user types into it. Songs that can't be edited are listed
 * before saving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagEditorScreen(
    uiState: TagEditorUiState,
    onNavigateUp: () -> Unit,
    onFieldChange: (TagField, String) -> Unit,
    onFieldReset: (TagField) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editing = uiState as? TagEditorUiState.Editing
    val writing = editing?.writing != null
    val hasChanges = editing?.hasChanges == true
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val onBack = {
        when {
            writing -> Unit
            hasChanges -> confirmDiscard = true
            else -> onNavigateUp()
        }
    }
    BackHandler(enabled = writing || hasChanges, onBack = onBack)

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection).testTag("tag-editor"),
        topBar = {
            S2TopBar(
                title = stringResource(R.string.edit_tags_action),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
                actions = {
                    if (editing != null) {
                        S2Button(
                            text = stringResource(R.string.dialog_button_save),
                            onClick = onSave,
                            enabled = hasChanges && !writing,
                            icon = Icons.Rounded.Check,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (uiState) {
                is TagEditorUiState.Reading -> LoadingState(
                    modifier = Modifier.align(Alignment.Center),
                    message = progressText(R.string.edit_tags_reading_tags, uiState.progress),
                    progress = { uiState.progress.fraction },
                )

                TagEditorUiState.Unreadable -> ErrorState(
                    title = stringResource(R.string.edit_tags_read_failed),
                    modifier = Modifier.align(Alignment.Center),
                    action = StateAction(stringResource(R.string.dialog_button_close), onNavigateUp),
                )

                is TagEditorUiState.Editing -> {
                    TagEditorForm(uiState, onFieldChange = onFieldChange, onFieldReset = onFieldReset, enabled = !writing)
                    uiState.writing?.let { progress ->
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)) {
                            Box(contentAlignment = Alignment.Center) {
                                LoadingState(message = progressText(R.string.edit_tags_writing_tags, progress), progress = { progress.fraction })
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        S2Dialog(
            title = stringResource(R.string.edit_tags_discard_title),
            onDismissRequest = { confirmDiscard = false },
            confirmLabel = stringResource(R.string.edit_tags_discard),
            onConfirm = {
                confirmDiscard = false
                onNavigateUp()
            },
            dismissLabel = stringResource(R.string.edit_tags_keep_editing),
            destructive = true,
        ) {}
    }
}

@Composable
private fun progressText(
    resId: Int,
    progress: TagProgress,
): String {
    val resources = LocalResources.current
    return resources.getString(resId, progress.done, progress.total)
}

@Composable
private fun TagEditorForm(
    state: TagEditorUiState.Editing,
    onFieldChange: (TagField, String) -> Unit,
    onFieldReset: (TagField) -> Unit,
    enabled: Boolean,
) {
    val fields = remember(state.fields) { state.fields.associateBy { it.field } }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.songCount > 1) {
            Text(
                text = pluralStringResource(R.plurals.edit_tags_editing_count_songs, state.songCount, state.songCount),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (state.skipped.isNotEmpty()) SkippedSongs(state)
        TagSection.entries.forEach { section ->
            val rows = section.rows.map { row -> row.mapNotNull(fields::get) }.filter { it.isNotEmpty() }
            if (rows.isNotEmpty()) {
                TagSectionCard(stringResource(section.title)) {
                    rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { field ->
                                TagTextField(field, onFieldChange, onFieldReset, enabled, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One section of fields: its heading over a tonal container, the way settings group their rows. */
@Composable
private fun TagSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        SettingsHeader(title)
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.largeIncreased, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

@Composable
private fun SkippedSongs(state: TagEditorUiState.Editing) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.largeIncreased, modifier = Modifier.fillMaxWidth().testTag("tag-editor-skipped")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = pluralStringResource(R.plurals.edit_tags_skipped, state.skipped.size, state.skipped.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            state.skipped.forEach { song ->
                Text(
                    text = listOfNotNull(song.name, song.friendlyArtistName).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun TagTextField(
    state: TagFieldState,
    onFieldChange: (TagField, String) -> Unit,
    onFieldReset: (TagField) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(state.field.hint)
    val multiple = stringResource(R.string.edit_tags_multiple_values)
    OutlinedTextField(
        value = state.text,
        onValueChange = { onFieldChange(state.field, it) },
        modifier = modifier.fillMaxWidth().testTag("tag-field-${state.field.name}"),
        enabled = enabled,
        label = { Text(label) },
        placeholder = if (state.mixed) ({ Text(multiple) }) else null,
        supportingText = if (state.mixed && !state.changed) ({ Text(multiple) }) else null,
        trailingIcon = if (state.changed) {
            {
                S2IconButton(
                    icon = Icons.AutoMirrored.Rounded.Undo,
                    contentDescription = stringResource(R.string.edit_tags_reset_field, label),
                    onClick = { onFieldReset(state.field) },
                    enabled = enabled,
                )
            }
        } else {
            null
        },
        singleLine = state.field != TagField.Lyrics,
        minLines = if (state.field == TagField.Lyrics) 5 else 1,
        keyboardOptions = if (state.field.numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
    )
}
