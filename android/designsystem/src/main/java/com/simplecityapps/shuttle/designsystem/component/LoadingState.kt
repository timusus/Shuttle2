package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.R
import com.simplecityapps.shuttle.designsystem.preview.S2Preview

/**
 * A screen or section waiting on content: the M3 loading indicator with an optional [message].
 * With [progress] it's determinate (the import), otherwise it morphs indefinitely.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    message: String? = null,
    progress: (() -> Float)? = null,
) {
    val description = message ?: stringResource(R.string.ds_loading)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (progress != null) LoadingIndicator(progress = progress) else LoadingIndicator()
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The small contained indicator for inline loading (the end of a paged list) and pull to refresh,
 * where it sits on content and needs its own container.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InlineLoadingIndicator(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
) {
    val description = stringResource(R.string.ds_loading)
    val semanticsModifier = modifier.semantics { contentDescription = description }
    if (progress != null) {
        ContainedLoadingIndicator(progress = progress, modifier = semanticsModifier)
    } else {
        ContainedLoadingIndicator(modifier = semanticsModifier)
    }
}

@Preview
@Composable
private fun LoadingStatePreview() {
    S2Preview {
        LoadingState(message = "Importing 1,204 of 3,310 songs", progress = { 0.36f })
    }
}
