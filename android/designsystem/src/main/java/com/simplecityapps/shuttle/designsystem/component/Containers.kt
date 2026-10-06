package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.VisualTransformation

/** The screen frame: a [topBar] over [content], which receives the padding the bars take. */
@Composable
fun S2Scaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(modifier = modifier, topBar = topBar, snackbarHost = snackbarHost, contentWindowInsets = contentWindowInsets, content = content)
}

/** A flat container in [color] (a tonal surface by default), clipped to [shape]. */
@Composable
fun S2Surface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.largeIncreased,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit,
) {
    Surface(modifier = modifier, shape = shape, color = color, content = content)
}

/** A raised card for a standalone message on a list screen. */
@Composable
fun S2Card(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(modifier = modifier, colors = CardDefaults.elevatedCardColors(), content = content)
}

/** A decorative or labelled glyph, in the surrounding content colour unless [tint] is given. */
@Composable
fun S2Icon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    Icon(icon, contentDescription = contentDescription, modifier = modifier, tint = if (tint == Color.Unspecified) LocalContentColor.current else tint)
}

@Composable
fun S2Divider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier)
}

@Composable
fun S2VerticalDivider(modifier: Modifier = Modifier) {
    VerticalDivider(modifier = modifier)
}

/** Pull-to-refresh over [content]; [isRefreshing] shows the indicator until the owner clears it. */
@Composable
fun S2PullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = modifier) { content() }
}

/** An outlined single-line text field with a [label] and optional [placeholder] and [supportingText]. */
@Composable
fun S2TextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        label = { S2Text(label) },
        placeholder = placeholder?.let { { S2Text(it) } },
        supportingText = supportingText?.let { { S2Text(it) } },
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
    )
}
