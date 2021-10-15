package com.simplecityapps.shuttle.compose.ui.components.mediaimporter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
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
import com.simplecityapps.shuttle.ui.mediaprovider.AuthenticationData
import com.simplecityapps.shuttle.ui.mediaprovider.JellyfinConfigurationViewModel
import com.simplecityapps.shuttle.ui.mediaprovider.ViewState
import kotlinx.coroutines.delay

@Composable
fun JellyfinConfigurationView(
    viewModel: JellyfinConfigurationViewModel,
    onDismiss: () -> Unit = {}
) {
    val viewState by viewModel.viewState.collectAsState()
    JellyfinConfigurationView(
        viewState = viewState,
        onAuthenticate = { authenticationData -> viewModel.authenticate(authenticationData) },
        onRememberPasswordToggled = { remember -> viewModel.setRememberPassword(remember = remember) },
        onRetry = { viewModel.retry() },
        onDismiss = onDismiss
    )
}

@Composable
fun JellyfinConfigurationView(
    viewState: ViewState,
    onAuthenticate: (data: AuthenticationData) -> Unit = {},
    onRememberPasswordToggled: (remember: Boolean) -> Unit = {},
    onRetry: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    var contentSize by remember { mutableStateOf(IntSize.Zero) }

    Column(Modifier.padding(24.dp)) {
        Text(
            text = stringResource(id = R.string.media_provider_title_jellyfin),
            style = MaterialTheme.typography.h6
        )
        Spacer(modifier = Modifier.size(16.dp))
        when (viewState) {
            is ViewState.Configuring -> {
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
                                AuthenticationData(
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
            ViewState.Authenticating -> {
                Box(
                    modifier = Modifier
                        .size(contentSize.width.toDp(), contentSize.height.toDp())
                        .background(MaterialColors.background),
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
            ViewState.Authenticated -> {
                Box(
                    modifier = Modifier
                        .size(contentSize.width.toDp(), contentSize.height.toDp())
                        .background(MaterialColors.background)
                        .wrapContentHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(id = R.string.media_provider_authentication_success))
                    LaunchedEffect(viewState) {
                        delay(2000)
                        onDismiss()
                    }
                }
            }
            ViewState.AuthenticationFailed -> {
                Box(
                    modifier = Modifier
                        .size(contentSize.width.toDp(), contentSize.height.toDp())
                        .background(MaterialColors.background)
                        .wrapContentHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stringResource(id = R.string.media_provider_authentication_error))
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

@Composable
fun Int.toDp(): Dp {
    return with(LocalDensity.current) { toDp() }
}

@Preview
@Composable
fun JellyfinConfigurationPreview(@PreviewParameter(ThemedPreviewProvider::class) darkTheme: Boolean) {
    Theme(isDark = darkTheme) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialColors.background)
        ) {
            JellyfinConfigurationView(ViewState.Configuring("http://server.jellyfin.com", "my_user", "pathword", true))
        }
    }
}

@Preview
@Composable
fun JellyfinConfigurationPreview2(@PreviewParameter(ThemedPreviewProvider::class) darkTheme: Boolean) {
    Theme(isDark = darkTheme) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialColors.background)
        ) {
            JellyfinConfigurationView(ViewState.Authenticating)
        }
    }
}

@Preview
@Composable
fun JellyfinConfigurationPreview3(@PreviewParameter(ThemedPreviewProvider::class) darkTheme: Boolean) {
    Theme(isDark = darkTheme) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialColors.background)
        ) {
            JellyfinConfigurationView(ViewState.Authenticated)
        }
    }
}
