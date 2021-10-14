package com.simplecityapps.shuttle.compose.ui.components.mediaimporter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.compose.R
import com.simplecityapps.shuttle.compose.ui.components.ThemedPreviewProvider
import com.simplecityapps.shuttle.compose.ui.theme.MaterialColors
import com.simplecityapps.shuttle.compose.ui.theme.Theme
import com.simplecityapps.shuttle.mediaprovider.jellyfin.AuthenticationManager
import com.simplecityapps.shuttle.mediaprovider.jellyfin.CredentialStore
import com.simplecityapps.shuttle.ui.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class JellyfinConfigurationViewModel(
    private val authenticationManager: AuthenticationManager,
    private val credentialStore: CredentialStore
) : ViewModel() {

    private val _viewState = MutableStateFlow<ViewState>(ViewState.Configuring(address = null, userName = null, password = null))
    val viewState = _viewState.asStateFlow()

    init {
        coroutineScope.launch {
            _viewState.emit(
                ViewState.Configuring(
                    address = credentialStore.getAddress().firstOrNull(),
                    userName = credentialStore.getUserName().firstOrNull(),
                    password = credentialStore.getPassword().firstOrNull()
                )
            )
        }
    }

    fun authenticate(authenticationData: AuthenticationData) {
        coroutineScope.launch {
            credentialStore.setAddress(authenticationData.address)
            credentialStore.setUserName(authenticationData.userName)
            credentialStore.setPassword(authenticationData.password)
            authenticationManager.authenticate().fold(
                onSuccess = {
                    _viewState.emit(ViewState.Authenticated)
                },
                onFailure = {
                    _viewState.emit(ViewState.AuthenticationFailed)
                }
            )
        }
    }
}

sealed class ViewState {
    data class Configuring(val address: String?, val userName: String?, val password: String?) : ViewState()
    object Authenticating : ViewState()
    object Authenticated : ViewState()
    object AuthenticationFailed : ViewState()
}

data class AuthenticationData(val address: String, val userName: String, val password: String, val rememberPassword: Boolean)

@Composable
fun JellyfinConfigurationView(viewModel: JellyfinConfigurationViewModel) {
    val viewState by viewModel.viewState.collectAsState()
    JellyfinConfigurationView(viewState = viewState)
}

@Composable
fun JellyfinConfigurationView(viewState: ViewState, onAuthenticate: (data: AuthenticationData) -> Unit = {}) {
    when (viewState) {
        is ViewState.Configuring -> {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                val addressText by remember { mutableStateOf(viewState.address ?: "") }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = addressText,
                    onValueChange = {},
                    label = { Text(text = stringResource(id = R.string.media_provider_config_hint_address)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    placeholder = { Text("e.g. http://my.server.com:8080") }
                )

                val userNameText by remember { mutableStateOf(viewState.userName ?: "") }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = userNameText,
                    onValueChange = {},
                    label = { Text(text = stringResource(id = R.string.media_provider_config_hint_username)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )

                val passwordText by remember { mutableStateOf(viewState.password ?: "") }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = passwordText,
                    onValueChange = {},
                    label = { Text(text = stringResource(id = R.string.media_provider_config_hint_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

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
                        checked = true,
                        onCheckedChange = {}
                    )
                }

                Button(
                    onClick = {

                    }) {
                    Text(stringResource(id = R.string.media_provider_button_authenticate))
                }
            }
        }
        ViewState.Authenticating -> {
            Column {
                CircularProgressIndicator()
                Text(text = stringResource(id = R.string.media_provider_authenticating))
            }
        }
        ViewState.Authenticated -> TODO()
        ViewState.AuthenticationFailed -> TODO()
    }

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
            JellyfinConfigurationView(ViewState.Configuring("http://server.jellyfin.com", "my_user", "pathword"))
        }
    }
}