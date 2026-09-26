package com.simplecityapps.shuttle.ui.screens.sources.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.common.PendingEvent
import com.simplecityapps.shuttle.ui.common.PendingEvents
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A sign-in field the form won't submit empty. */
enum class ServerSignInField { Address, Username, Password }

/** What the user has typed into a server's sign-in form. */
data class ServerSignInForm(
    val address: String = "",
    val username: String = "",
    val password: String = "",
    val authCode: String = "",
    val rememberPassword: Boolean = true,
    /** False while the saved password fills its field: it can't be revealed until it's cleared. */
    val passwordRevealable: Boolean = true,
    /** Required fields the user left empty when they last submitted, until they type into them. */
    val missing: Set<ServerSignInField> = emptySet(),
)

sealed interface ServerSignInStep {
    data object Form : ServerSignInStep

    data object Authenticating : ServerSignInStep

    data object Connected : ServerSignInStep

    data class Failed(val message: String) : ServerSignInStep
}

data class ServerSignInUiState(
    val type: MediaProviderType,
    val form: ServerSignInForm = ServerSignInForm(),
    val step: ServerSignInStep = ServerSignInStep.Form,
    val events: List<PendingEvent<ServerSignInEvent>> = emptyList(),
) {
    /** Plex takes a two-factor code, and needs the password. */
    val asksForAuthCode: Boolean get() = type == MediaProviderType.Plex
}

sealed interface ServerSignInEvent {
    /** The server is signed in: the library can import from it. */
    data object Connected : ServerSignInEvent

    /** The success message has shown for long enough: the dialog can close. */
    data object Finished : ServerSignInEvent
}

/**
 * A Jellyfin, Emby or Plex server's sign-in: the address and login, starting from the saved ones, then the
 * authentication's progress and outcome.
 */
@HiltViewModel(assistedFactory = ServerSignInViewModel.Factory::class)
class ServerSignInViewModel @AssistedInject constructor(
    @Assisted private val type: MediaProviderType,
    readServerLogin: ReadServerLogin,
    private val signInToServer: SignInToServer,
    private val forgetServerLogin: ForgetServerLogin,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(type: MediaProviderType): ServerSignInViewModel
    }

    private val form = MutableStateFlow(
        readServerLogin(type).let { saved ->
            ServerSignInForm(
                address = saved.address ?: DEFAULT_ADDRESS,
                username = saved.username.orEmpty(),
                password = saved.password.orEmpty(),
                passwordRevealable = saved.password == null,
            )
        },
    )
    private val step = MutableStateFlow<ServerSignInStep>(ServerSignInStep.Form)
    private val events = PendingEvents<ServerSignInEvent>()

    val uiState: StateFlow<ServerSignInUiState> =
        combine(form, step, events.flow) { form, step, events -> ServerSignInUiState(type, form, step, events) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServerSignInUiState(type, form.value, step.value))

    fun onAddressChange(address: String) = form.update { it.copy(address = address, missing = it.missing - ServerSignInField.Address) }

    fun onUsernameChange(username: String) = form.update { it.copy(username = username, missing = it.missing - ServerSignInField.Username) }

    fun onPasswordChange(password: String) = form.update {
        it.copy(password = password, passwordRevealable = it.passwordRevealable || password.isEmpty(), missing = it.missing - ServerSignInField.Password)
    }

    fun onAuthCodeChange(authCode: String) = form.update { it.copy(authCode = authCode) }

    /** Turning it off forgets the saved login straight away. */
    fun onRememberPasswordChange(remember: Boolean) {
        form.update { it.copy(rememberPassword = remember) }
        if (!remember) forgetServerLogin(type)
    }

    fun onAuthenticate() {
        if (step.value != ServerSignInStep.Form) return
        val form = form.value
        val missing = missingFields(form)
        if (missing.isNotEmpty()) {
            this.form.update { it.copy(missing = missing) }
            return
        }
        step.value = ServerSignInStep.Authenticating
        val login = ServerLogin(form.address, form.username, form.password, form.authCode.takeIf { uiState.value.asksForAuthCode })
        viewModelScope.launch {
            when (val result = signInToServer(type, login, form.rememberPassword)) {
                SignInToServer.Result.Success -> {
                    step.value = ServerSignInStep.Connected
                    events.post(ServerSignInEvent.Connected)
                    delay(SUCCESS_SHOWN_MILLIS)
                    events.post(ServerSignInEvent.Finished)
                }

                is SignInToServer.Result.Failure -> step.value = ServerSignInStep.Failed(result.message)
            }
        }
    }

    /** Back to the form after a failed sign-in. */
    fun onRetry() {
        step.value = ServerSignInStep.Form
    }

    fun onEventHandled(id: Long) = events.consume(id)

    private fun missingFields(form: ServerSignInForm): Set<ServerSignInField> = buildSet {
        if (form.address.isEmpty()) add(ServerSignInField.Address)
        if (form.username.isEmpty()) add(ServerSignInField.Username)
        if (type == MediaProviderType.Plex && form.password.isEmpty()) add(ServerSignInField.Password)
    }

    private companion object {
        const val DEFAULT_ADDRESS = "http://"
        const val SUCCESS_SHOWN_MILLIS = 1_000L
    }
}
