package com.simplecityapps.shuttle.ui.screens.sources.servers

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simplecityapps.mediaprovider.R as MediaProviderR
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.S2Button
import com.simplecityapps.shuttle.designsystem.component.S2ButtonStyle
import com.simplecityapps.shuttle.designsystem.component.S2DialogContent
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import com.simplecityapps.shuttle.model.MediaProviderType

/** What the sign-in form asks its host to do. */
class ServerSignInActions(
    val onAddressChange: (String) -> Unit,
    val onUsernameChange: (String) -> Unit,
    val onPasswordChange: (String) -> Unit,
    val onAuthCodeChange: (String) -> Unit,
    val onRememberPasswordChange: (Boolean) -> Unit,
    val onAuthenticate: () -> Unit,
    val onRetry: () -> Unit,
    val onDismiss: () -> Unit,
)

/** A Jellyfin, Emby or Plex server's sign-in dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSignInDialog(
    uiState: ServerSignInUiState,
    actions: ServerSignInActions,
) {
    BasicAlertDialog(onDismissRequest = actions.onDismiss) {
        ServerSignInForm(uiState, actions)
    }
}

/**
 * The [ServerSignInDialog]'s surface, without its window. Under Robolectric a text field in a dialog window never
 * lets Compose idle, so tests render this.
 */
@Composable
internal fun ServerSignInForm(
    uiState: ServerSignInUiState,
    actions: ServerSignInActions,
) {
    S2DialogContent(
        title = stringResource(uiState.type.longTitleRes),
        onDismiss = actions.onDismiss,
        confirmLabel = stringResource(R.string.media_provider_button_authenticate),
        onConfirm = actions.onAuthenticate,
        dismissLabel = stringResource(R.string.dialog_button_close),
        confirmEnabled = uiState.step == ServerSignInStep.Form,
    ) {
        when (val step = uiState.step) {
            ServerSignInStep.Form -> SignInFields(uiState, actions)

            ServerSignInStep.Authenticating -> Progress(stringResource(R.string.media_provider_authenticating), showSpinner = true)

            ServerSignInStep.Connected -> Progress(stringResource(R.string.media_provider_authentication_success), showSpinner = false)

            is ServerSignInStep.Failed -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(step.message, textAlign = TextAlign.Center)
                S2Button(
                    text = stringResource(R.string.dialog_button_retry),
                    onClick = actions.onRetry,
                    style = S2ButtonStyle.Outlined,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SignInFields(
    uiState: ServerSignInUiState,
    actions: ServerSignInActions,
) {
    val form = uiState.form
    val required = stringResource(R.string.validation_field_required)
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val addressMissing = ServerSignInField.Address in form.missing
        OutlinedTextField(
            value = form.address,
            onValueChange = actions.onAddressChange,
            label = { Text(stringResource(R.string.media_provider_config_hint_address)) },
            supportingText = {
                Text(if (addressMissing) required else stringResource(if (uiState.asksForAuthCode) R.string.media_provider_config_helper_address_plex else R.string.media_provider_config_helper_address))
            },
            isError = addressMissing,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        val usernameMissing = ServerSignInField.Username in form.missing
        OutlinedTextField(
            value = form.username,
            onValueChange = actions.onUsernameChange,
            label = { Text(stringResource(R.string.media_provider_config_hint_username)) },
            supportingText = if (usernameMissing) ({ Text(required) }) else null,
            isError = usernameMissing,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        PasswordField(
            password = form.password,
            revealable = form.passwordRevealable,
            missing = ServerSignInField.Password in form.missing,
            onPasswordChange = actions.onPasswordChange,
        )
        if (uiState.asksForAuthCode) {
            OutlinedTextField(
                value = form.authCode,
                onValueChange = actions.onAuthCodeChange,
                label = { Text(stringResource(R.string.media_provider_config_hint_code)) },
                supportingText = { Text(stringResource(R.string.media_provider_config_helper_code)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.media_provider_config_switch_remember_password))
            Spacer(Modifier.width(16.dp))
            Switch(checked = form.rememberPassword, onCheckedChange = actions.onRememberPasswordChange)
        }
    }
}

/** A saved password can't be revealed: the toggle only shows once the field has been cleared. */
@Composable
private fun PasswordField(
    password: String,
    revealable: Boolean,
    missing: Boolean,
    onPasswordChange: (String) -> Unit,
) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text(stringResource(R.string.media_provider_config_hint_password)) },
        supportingText = if (missing) ({ Text(stringResource(R.string.validation_field_required)) }) else null,
        isError = missing,
        singleLine = true,
        visualTransformation = if (revealed && revealable) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = if (revealable) {
            {
                IconButton(onClick = { revealed = !revealed }) {
                    Icon(
                        if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = stringResource(if (revealed) R.string.media_provider_config_hide_password else R.string.media_provider_config_show_password),
                    )
                }
            }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Progress(
    message: String,
    showSpinner: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showSpinner) CircularProgressIndicator()
        Text(message, textAlign = TextAlign.Center)
    }
}

@get:StringRes
private val MediaProviderType.longTitleRes: Int
    get() = when (this) {
        MediaProviderType.Jellyfin -> MediaProviderR.string.media_provider_title_long_jellyfin
        MediaProviderType.Emby -> MediaProviderR.string.media_provider_title_long_emby
        MediaProviderType.Plex -> MediaProviderR.string.media_provider_title_long_plex
        MediaProviderType.Shuttle, MediaProviderType.MediaStore -> error("$this has no sign-in")
    }

private val previewActions = ServerSignInActions({}, {}, {}, {}, {}, {}, {}, {})

@Preview
@Composable
private fun PlexSignIn() {
    S2Preview(darkTheme = false) {
        ServerSignInForm(
            ServerSignInUiState(MediaProviderType.Plex, ServerSignInForm(address = "http://192.168.1.20:32400", username = "sam")),
            previewActions,
        )
    }
}

@Preview
@Composable
private fun JellyfinSignInMissingFields() {
    S2Preview(darkTheme = false) {
        ServerSignInForm(
            ServerSignInUiState(
                MediaProviderType.Jellyfin,
                ServerSignInForm(address = "", missing = setOf(ServerSignInField.Address, ServerSignInField.Username)),
            ),
            previewActions,
        )
    }
}

@Preview
@Composable
private fun EmbySignInConnected() {
    S2Preview(darkTheme = true) {
        ServerSignInForm(ServerSignInUiState(MediaProviderType.Emby, step = ServerSignInStep.Connected), previewActions)
    }
}

@Preview
@Composable
private fun JellyfinSignInFailed() {
    S2Preview(darkTheme = false) {
        ServerSignInForm(
            ServerSignInUiState(MediaProviderType.Jellyfin, step = ServerSignInStep.Failed("An error occurred. (401)")),
            previewActions,
        )
    }
}
