package com.simplecityapps.shuttle.compose.ui.components.mediaimporter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.compose.R
import com.simplecityapps.shuttle.compose.ui.components.ThemedPreviewProvider
import com.simplecityapps.shuttle.compose.ui.theme.MaterialColors
import com.simplecityapps.shuttle.compose.ui.theme.Theme
import com.simplecityapps.shuttle.ui.mediaprovider.EmbyConfigurationViewModel
import kotlinx.coroutines.delay

@Composable
fun EmbyConfigurationView(
    viewModel: EmbyConfigurationViewModel,
    onDismiss: () -> Unit = {}
) {
    val viewState by viewModel.viewState.collectAsState()
    EmbyConfigurationView(
        viewState = viewState,
        onAuthenticate = { authenticationData -> viewModel.authenticate(authenticationData) },
        onRememberPasswordToggled = { remember -> viewModel.setRememberPassword(remember = remember) },
        onRetry = { viewModel.retry() },
        onDismiss = onDismiss
    )
}

@Composable
fun EmbyConfigurationView(
    viewState: EmbyConfigurationViewModel.ViewState,
    onAuthenticate: (data: EmbyConfigurationViewModel.AuthenticationData) -> Unit = {},
    onRememberPasswordToggled: (remember: Boolean) -> Unit = {},
    onRetry: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    var contentSize by remember { mutableStateOf(IntSize.Zero) }

    Column(Modifier.padding(24.dp)) {
        Text(
            text = stringResource(id = R.string.media_provider_title_emby),
            style = MaterialTheme.typography.h6
        )
        Spacer(modifier = Modifier.size(16.dp))
        when (viewState) {
            is EmbyConfigurationViewModel.ViewState.Configuring -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned {
                            contentSize = it.size
                        },
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    var addressText by remember { mutableStateOf(viewState.address ?: "") }
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = addressText,
                        onValueChange = { addressText = it },
                        label = { Text(text = stringResource(id = R.string.media_provider_config_hint_address)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        placeholder = { Text("e.g. http://my.server.com:8080") },
                        singleLine = true
                    )

                    var userNameText by remember { mutableStateOf(viewState.userName ?: "") }
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = userNameText,
                        onValueChange = { userNameText = it },
                        label = { Text(text = stringResource(id = R.string.media_provider_config_hint_username)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        singleLine = true
                    )

                    var passwordText by remember { mutableStateOf(viewState.password ?: "") }
                    var passwordVisible by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = passwordText,
                        onValueChange = { passwordText = it },
                        label = { Text(text = stringResource(id = R.string.media_provider_config_hint_password)) },
                        visualTransformation = if (passwordVisible && viewState.password.isNullOrEmpty()) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                passwordVisible = !passwordVisible
                            }) {
                                Icon(imageVector = if (passwordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, "Password Visibility")
                            }
                        }
                    )

                    var rememberPassword by remember { mutableStateOf(viewState.rememberPassword) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = stringResource(id = R.string.media_provider_config_switch_remember_password)
                        )
                        Switch(
                            checked = rememberPassword,
                            onCheckedChange = { checked ->
                                rememberPassword = checked
                                onRememberPasswordToggled(checked)
                            }
                        )
                    }

                    Button(
                        modifier = Modifier.align(Alignment.End),
                        onClick = {
                            onAuthenticate(
                                EmbyConfigurationViewModel.AuthenticationData(
                                    address = addressText,
                                    userName = userNameText,
                                    password = passwordText,
                                    rememberPassword = rememberPassword
                                )
                            )
                        }) {
                        Text(stringResource(id = R.string.media_provider_button_authenticate))
                    }
                }
            }
            EmbyConfigurationViewModel.ViewState.Authenticating -> {
                Box(
                    modifier = Modifier
                        .size(contentSize.width.toDp(), contentSize.height.toDp()),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(text = stringResource(id = R.string.media_provider_authenticating))
                    }
                }
            }
            EmbyConfigurationViewModel.ViewState.Authenticated -> {
                Box(
                    modifier = Modifier
                        .size(contentSize.width.toDp(), contentSize.height.toDp())
                        .wrapContentHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Outlined.CheckCircle, "Success Icon", tint = MaterialColors.primary)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(id = R.string.media_provider_authentication_success), style = MaterialTheme.typography.body1)
                    }
                    LaunchedEffect(viewState) {
                        delay(2000)
                        onDismiss()
                    }
                }
            }
            EmbyConfigurationViewModel.ViewState.AuthenticationFailed -> {
                Box(
                    modifier = Modifier
                        .size(contentSize.width.toDp(), contentSize.height.toDp())
                        .wrapContentHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stringResource(id = R.string.media_provider_authentication_error), style = MaterialTheme.typography.body1)
                        Spacer(modifier = Modifier.size(16.dp))
                        Button(onClick = {
                            onRetry()
                        }) {
                            Text(stringResource(id = R.string.dialog_button_retry))
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun EmbyConfigurationPreview(@PreviewParameter(ThemedPreviewProvider::class) darkTheme: Boolean) {
    Theme(isDark = darkTheme) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialColors.background)
        ) {
            EmbyConfigurationView(EmbyConfigurationViewModel.ViewState.Configuring("http://server.emby.com", "my_user", "pathword", true))
        }
    }
}

@Preview
@Composable
fun EmbyConfigurationPreview2(@PreviewParameter(ThemedPreviewProvider::class) darkTheme: Boolean) {
    Theme(isDark = darkTheme) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialColors.background)
        ) {
            EmbyConfigurationView(EmbyConfigurationViewModel.ViewState.Authenticating)
        }
    }
}

@Preview
@Composable
fun EmbyConfigurationPreview3(@PreviewParameter(ThemedPreviewProvider::class) darkTheme: Boolean) {
    Theme(isDark = darkTheme) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialColors.background)
        ) {
            EmbyConfigurationView(EmbyConfigurationViewModel.ViewState.Authenticated)
        }
    }
}
