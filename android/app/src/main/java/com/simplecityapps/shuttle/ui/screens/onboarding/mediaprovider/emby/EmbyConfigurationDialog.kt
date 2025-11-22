package com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider.emby

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplecityapps.provider.emby.http.LoginCredentials
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.snapshot.Snapshot
import com.simplecityapps.shuttle.ui.theme.ColorSchemePreviewParameterProvider

@Composable
fun EmbyConfigurationDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EmbyConfigurationViewModel = hiltViewModel()
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.onInitializeConfiguration()
    }
    EmbyConfigurationDialog(
        modifier = modifier,
        viewState = viewState,
        onDismissRequest = onDismissRequest,
        onAddressChange = viewModel::onAddressChange,
        onUsernameChange = viewModel::onUsernameChange,
        onPasswordChange = viewModel::onPasswordChange,
        onRememberPasswordChange = viewModel::onRememberPasswordChange,
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
private fun EmbyConfigurationDialog(
    viewState: EmbyConfigurationViewState,
    onDismissRequest: () -> Unit,
    onAuthenticateClick: () -> Unit,
    onAddressChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRememberPasswordChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(com.simplecityapps.mediaprovider.R.string.media_provider_title_long_emby))
        },
        text = {
            AnimatedContent(
                targetState = viewState.authenticationState,
                transitionSpec = { EnterTransition.None togetherWith ExitTransition.None }
            ) { state ->
                when (state) {
                    EmbyAuthenticationState.Success -> {
                        LaunchedEffect(Unit) {
                            onDismissRequest()
                        }
                    }

                    EmbyAuthenticationState.Loading -> EmbyAuthenticationLoading()
                    is EmbyAuthenticationState.Error -> EmbyAuthenticationError(state = state)
                    null -> EmbyAuthenticationInputForm(
                        address = viewState.address,
                        rememberPassword = viewState.rememberPassword,
                        loginCredentials = viewState.loginCredentials,
                        onAddressChange = onAddressChange,
                        onUsernameChange = onUsernameChange,
                        onPasswordChange = onPasswordChange,
                        onRememberPasswordChange = onRememberPasswordChange
                    )
                }
            }
        },
        dismissButton = {
            AnimatedVisibility(visible = viewState.authenticationState != EmbyAuthenticationState.Loading) {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(R.string.dialog_button_close))
                }
            }
        },
        confirmButton = {
            AnimatedVisibility(visible = viewState.authenticationState != EmbyAuthenticationState.Loading) {
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
private fun EmbyAuthenticationInputForm(
    address: String,
    rememberPassword: Boolean,
    loginCredentials: LoginCredentials,
    onAddressChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
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
        RememberPasswordSwitch(
            rememberPassword = rememberPassword,
            onRememberPasswordChange = onRememberPasswordChange
        )
    }
}

@Composable
private fun EmbyAuthenticationLoading() {
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
private fun EmbyAuthenticationError(
    state: EmbyAuthenticationState.Error,
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
                text = "Unable to authenticate with your Emby server."
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
            text = "e.g. http://my.server.com:8080"
        )
    }
}

@Snapshot
@Preview
@Composable
private fun Loading(@PreviewParameter(ColorSchemePreviewParameterProvider::class) colorScheme: ColorScheme) {
    MaterialTheme(colorScheme = colorScheme) {
        EmbyConfigurationDialog(
            onDismissRequest = {},
            viewState = EmbyConfigurationViewState.Empty.copy(
                authenticationState = EmbyAuthenticationState.Loading
            ),
            onAddressChange = {},
            onUsernameChange = {},
            onPasswordChange = {},
            onAuthenticateClick = {},
            onRememberPasswordChange = {}
        )
    }
}

@Snapshot
@Preview
@Composable
private fun Success(@PreviewParameter(ColorSchemePreviewParameterProvider::class) colorScheme: ColorScheme) {
    MaterialTheme(colorScheme = colorScheme) {
        EmbyConfigurationDialog(
            onDismissRequest = {},
            viewState = EmbyConfigurationViewState(
                address = "http://",
                loginCredentials = LoginCredentials(
                    username = "shuttle",
                    password = "musicplayer"
                ),
                canAuthenticate = true,
                rememberPassword = false,
                authenticationState = null
            ),
            onAddressChange = {},
            onUsernameChange = {},
            onPasswordChange = {},
            onAuthenticateClick = {},
            onRememberPasswordChange = {}
        )
    }
}

@Snapshot
@Preview
@Composable
private fun Error(@PreviewParameter(ColorSchemePreviewParameterProvider::class) colorScheme: ColorScheme) {
    MaterialTheme(colorScheme = colorScheme) {
        EmbyConfigurationDialog(
            onDismissRequest = {},
            viewState = EmbyConfigurationViewState.Empty.copy(
                authenticationState = EmbyAuthenticationState.Error(
                    error = RuntimeException("Unable to connect to server")
                )
            ),
            onAddressChange = {},
            onUsernameChange = {},
            onPasswordChange = {},
            onAuthenticateClick = {},
            onRememberPasswordChange = {}
        )
    }
}
