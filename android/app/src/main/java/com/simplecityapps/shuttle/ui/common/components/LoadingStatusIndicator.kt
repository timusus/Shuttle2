package com.simplecityapps.shuttle.ui.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.R

@Composable
fun LoadingStatusIndicator(
    state: CircularLoadingState,
    modifier: Modifier = Modifier,
    onRetryClicked: () -> Unit = {},
    progressColor: Color = MaterialTheme.colorScheme.primary,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .wrapContentHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (state) {
            is CircularLoadingState.Loading -> {
                CircularProgressIndicator(
                    color = progressColor,
                    modifier = Modifier
                )
            }

            is CircularLoadingState.Error,
            is CircularLoadingState.Empty,
            is CircularLoadingState.Retry,
                -> {
                Icon(
                    painter = painterResource(R.drawable.ic_error_outline_black_24dp),
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        if (state is CircularLoadingState.Retry) {
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onRetryClicked,
            ) {
                Text("Retry")
            }
        }
    }
}

sealed class CircularLoadingState(open val message: String) {
    data class Loading(override val message: String) : CircularLoadingState(message)
    data class Empty(override val message: String) : CircularLoadingState(message)
    data class Error(override val message: String) : CircularLoadingState(message)
    data class Retry(override val message: String) : CircularLoadingState(message)
}

@Preview(showBackground = true)
@Composable
private fun CircularLoadingViewPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Loading state
            LoadingStatusIndicator(
                state = CircularLoadingState.Loading("Loading data...")
            )

            HorizontalDivider()

            // Error state
            LoadingStatusIndicator(
                state = CircularLoadingState.Error("Something went wrong")
            )

            HorizontalDivider()

            // Empty state
            LoadingStatusIndicator(
                state = CircularLoadingState.Empty("No items found")
            )

            HorizontalDivider()

            // Retry state
            LoadingStatusIndicator(
                state = CircularLoadingState.Retry("Failed to load data"),
                onRetryClicked = { /* Handle retry */ }
            )
        }
    }
}
