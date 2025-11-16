package com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider.plex

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplecityapps.provider.plex.http.LoginCredentials
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.snapshot.Snapshot
import com.simplecityapps.shuttle.ui.theme.ColorSchemePreviewParameterProvider

@Composable
fun PlexConfigurationDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlexConfigurationViewModel = hiltViewModel()
) {
    val address by viewModel.address.collectAsStateWithLifecycle()
    val canAuthenticate by viewModel.canAuthenticate.collectAsStateWithLifecycle()
    val loginCredentials by viewModel.loginCredentials.collectAsStateWithLifecycle()
    val rememberPassword by viewModel.rememberPassword.collectAsStateWithLifecycle()
    val authenticationSuccess by viewModel.authenticationSuccess.collectAsStateWithLifecycle()

    if (authenticationSuccess == true) {
        LaunchedEffect(Unit) {
            onDismissRequest()
        }
    }

    PlexConfigurationDialog(
        address = address,
        modifier = modifier,
        canAuthenticate = canAuthenticate,
        onDismissRequest = onDismissRequest,
        rememberPassword = rememberPassword,
        loginCredentials = loginCredentials,
        onAddressChange = viewModel::onAddressChange,
        onUsernameChange = viewModel::onUsernameChange,
        onPasswordChange = viewModel::onPasswordChange,
        onAuthCodeChange = viewModel::onAuthCodeChange,
        onAuthenticateClick = viewModel::onAuthenticateClick,
        onRememberPasswordChange = viewModel::onRememberPasswordChange
    )
}

@Composable
private fun PlexConfigurationDialog(
    address: String,
    canAuthenticate: Boolean,
    rememberPassword: Boolean,
    loginCredentials: LoginCredentials?,
    onDismissRequest: () -> Unit,
    onAddressChange: (String) -> Unit,
    onAuthCodeChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRememberPasswordChange: (Boolean) -> Unit,
    onAuthenticateClick: (String, LoginCredentials) -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(com.simplecityapps.mediaprovider.R.string.media_provider_title_long_plex))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = address,
                        singleLine = true,
                        onValueChange = onAddressChange,
                        placeholder = { Text(text = "http://") },
                        label = { Text(stringResource(com.simplecityapps.mediaprovider.R.string.media_provider_config_hint_address)) }
                    )
                    Text(
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.labelSmall,
                        text = "e.g. https://my.plex.server.com/32400"
                    )
                }

                OutlinedTextField(
                    singleLine = true,
                    onValueChange = onUsernameChange,
                    value = loginCredentials?.username ?: "",
                    label = { Text(stringResource(com.simplecityapps.mediaprovider.R.string.media_provider_config_hint_username)) }
                )

                var showPassword by remember { mutableStateOf(false) }
                OutlinedTextField(
                    singleLine = true,
                    onValueChange = onPasswordChange,
                    value = loginCredentials?.password ?: "",
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    label = { Text(stringResource(com.simplecityapps.mediaprovider.R.string.media_provider_config_hint_password)) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                contentDescription = null,
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility
                            )
                        }
                    }
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        singleLine = true,
                        onValueChange = onAuthCodeChange,
                        value = loginCredentials?.authCode ?: "",
                        keyboardOptions = KeyboardOptions.Default.copy(
                            keyboardType = KeyboardType.Number
                        ),
                        label = { Text(stringResource(R.string.media_provider_config_hint_code)) }
                    )
                    Text(
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodySmall,
                        text = stringResource(R.string.media_provider_config_helper_code)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodySmall,
                        text = stringResource(com.simplecityapps.mediaprovider.R.string.media_provider_config_switch_remember_password)
                    )
                    Switch(
                        checked = rememberPassword,
                        onCheckedChange = onRememberPasswordChange
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.dialog_button_close))
            }
        },
        confirmButton = {
            TextButton(
                enabled = canAuthenticate,
                onClick = { onAuthenticateClick(address, loginCredentials!!) }
            ) {
                Text(text = stringResource(R.string.media_provider_button_authenticate))
            }
        }
    )
}

@Snapshot
@Preview
@Composable
private fun Preview(@PreviewParameter(ColorSchemePreviewParameterProvider::class) colorScheme: ColorScheme) {
    MaterialTheme(colorScheme = colorScheme) {
        PlexConfigurationDialog(
            onDismissRequest = {},
            address = "http://",
            loginCredentials = LoginCredentials(
                username = "shuttle",
                password = "musicplayer",
                authCode = null
            ),
            canAuthenticate = true,
            rememberPassword = false,
            onAddressChange = {},
            onUsernameChange = {},
            onPasswordChange = {},
            onAuthCodeChange = {},
            onRememberPasswordChange = {},
            onAuthenticateClick = { _, _ -> }
        )
    }
}
