package com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider.plex

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
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
    val owner = LocalViewModelStoreOwner.current
    DisposableEffect(Unit) {
        onDispose {
            owner?.viewModelStore?.clear()
        }
    }
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    PlexConfigurationDialog(
        modifier = modifier,
        viewState = viewState,
        onDismissRequest = onDismissRequest,
        onAddressChange = viewModel::onAddressChange,
        onUsernameChange = viewModel::onUsernameChange,
        onPasswordChange = viewModel::onPasswordChange,
        onAuthCodeChange = viewModel::onAuthCodeChange,
        onRememberPasswordChange = viewModel::onRememberPasswordChange,
        onAuthenticationSuccess = viewModel::onConsumeAuthenticationSuccess,
        onAuthenticateClick = {
            viewModel.onAuthenticateClick(
                address = viewState.address,
                rememberPassword = viewState.rememberPassword,
                loginCredentials = viewState.loginCredentials
            )
        }
    )
}

@Composable
private fun PlexConfigurationDialog(
    viewState: PlexConfigurationViewState,
    onDismissRequest: () -> Unit,
    onAuthenticateClick: () -> Unit,
    onAddressChange: (String) -> Unit,
    onAuthCodeChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onAuthenticationSuccess: () -> Unit,
    onRememberPasswordChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(com.simplecityapps.mediaprovider.R.string.media_provider_title_long_plex))
        },
        text = {
            AnimatedContent(targetState = viewState.authenticationState) { state ->
                when (state) {
                    PlexAuthenticationState.Success -> {
                        LaunchedEffect(Unit) {
                            onDismissRequest()
                            onAuthenticationSuccess()
                        }
                    }

                    PlexAuthenticationState.Loading -> PlexAuthenticationLoading()
                    is PlexAuthenticationState.Error -> PlexAuthenticationError(state = state)
                    null -> PlexAuthenticationInputForm(
                        address = viewState.address,
                        rememberPassword = viewState.rememberPassword,
                        loginCredentials = viewState.loginCredentials,
                        onAddressChange = onAddressChange,
                        onUsernameChange = onUsernameChange,
                        onPasswordChange = onPasswordChange,
                        onAuthCodeChange = onAuthCodeChange,
                        onRememberPasswordChange = onRememberPasswordChange
                    )
                }
            }
        },
        dismissButton = {
            AnimatedVisibility(visible = viewState.authenticationState != PlexAuthenticationState.Loading) {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(R.string.dialog_button_close))
                }
            }
        },
        confirmButton = {
            AnimatedVisibility(visible = viewState.authenticationState != PlexAuthenticationState.Loading) {
                TextButton(
                    onClick = onAuthenticateClick,
                    enabled = viewState.canAuthenticate
                ) {
                    Text(text = stringResource(R.string.media_provider_button_authenticate))
                }
            }
        }
    )
}

@Composable
private fun PlexAuthenticationInputForm(
    address: String,
    rememberPassword: Boolean,
    loginCredentials: LoginCredentials,
    onAddressChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onAuthCodeChange: (String) -> Unit,
    onRememberPasswordChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        AddressInputField(
            address = address,
            onAddressChange = onAddressChange
        )
        UsernameInputField(
            username = loginCredentials.username,
            onUsernameChange = onUsernameChange
        )
        PasswordInputField(
            password = loginCredentials.password,
            onPasswordChange = onPasswordChange
        )
        AuthCodeInputField(
            authCode = loginCredentials.authCode,
            onAuthCodeChange = onAuthCodeChange
        )
        RememberPasswordSwitch(
            rememberPassword = rememberPassword,
            onRememberPasswordChange = onRememberPasswordChange
        )
    }
}

@Composable
private fun PlexAuthenticationLoading() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        CircularProgressIndicator()
        Text(text = stringResource(R.string.media_provider_authenticating))
    }
}

@Composable
private fun PlexAuthenticationError(
    state: PlexAuthenticationState.Error,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Connection failed",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                text = "Unable to authenticate with your Plex server."
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
                .background(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
                .border(
                    width = 1.dp,
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.error
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Error: ${state.error}",
                color = MaterialTheme.colorScheme.tertiaryFixedDim
            )
        }
    }
}

@Composable
private fun RememberPasswordSwitch(
    rememberPassword: Boolean,
    onRememberPasswordChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
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

@Composable
private fun AuthCodeInputField(
    authCode: String?,
    onAuthCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            singleLine = true,
            value = authCode ?: "",
            onValueChange = onAuthCodeChange,
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
}

@Composable
private fun PasswordInputField(
    password: String?,
    onPasswordChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPassword by remember { mutableStateOf(false) }
    OutlinedTextField(
        singleLine = true,
        modifier = modifier,
        value = password ?: "",
        onValueChange = onPasswordChange,
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
}

@Composable
private fun UsernameInputField(
    username: String?,
    onUsernameChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        singleLine = true,
        modifier = modifier,
        value = username ?: "",
        onValueChange = onUsernameChange,
        label = { Text(stringResource(com.simplecityapps.mediaprovider.R.string.media_provider_config_hint_username)) }
    )
}

@Composable
private fun AddressInputField(
    address: String,
    onAddressChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
}

@Snapshot
@Preview
@Composable
private fun Loading(@PreviewParameter(ColorSchemePreviewParameterProvider::class) colorScheme: ColorScheme) {
    MaterialTheme(colorScheme = colorScheme) {
        PlexConfigurationDialog(
            onDismissRequest = {},
            viewState = PlexConfigurationViewState.Empty.copy(
                authenticationState = PlexAuthenticationState.Loading
            ),
            onAddressChange = {},
            onUsernameChange = {},
            onPasswordChange = {},
            onAuthCodeChange = {},
            onAuthenticateClick = {},
            onAuthenticationSuccess = {},
            onRememberPasswordChange = {}
        )
    }
}

@Snapshot
@Preview
@Composable
private fun Success(@PreviewParameter(ColorSchemePreviewParameterProvider::class) colorScheme: ColorScheme) {
    MaterialTheme(colorScheme = colorScheme) {
        PlexConfigurationDialog(
            onDismissRequest = {},
            viewState = PlexConfigurationViewState(
                address = "http://",
                loginCredentials = LoginCredentials(
                    username = "shuttle",
                    password = "musicplayer",
                    authCode = null
                ),
                canAuthenticate = true,
                rememberPassword = false,
                authenticationState = null
            ),
            onAddressChange = {},
            onUsernameChange = {},
            onPasswordChange = {},
            onAuthCodeChange = {},
            onAuthenticateClick = {},
            onAuthenticationSuccess = {},
            onRememberPasswordChange = {}
        )
    }
}

@Snapshot
@Preview
@Composable
private fun Error(@PreviewParameter(ColorSchemePreviewParameterProvider::class) colorScheme: ColorScheme) {
    MaterialTheme(colorScheme = colorScheme) {
        PlexConfigurationDialog(
            onDismissRequest = {},
            viewState = PlexConfigurationViewState.Empty.copy(
                authenticationState = PlexAuthenticationState.Error(
                    error = RuntimeException("Unable to connect to server")
                )
            ),
            onAddressChange = {},
            onUsernameChange = {},
            onPasswordChange = {},
            onAuthCodeChange = {},
            onAuthenticateClick = {},
            onAuthenticationSuccess = {},
            onRememberPasswordChange = {}
        )
    }
}
