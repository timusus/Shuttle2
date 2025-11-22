package com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider.emby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.provider.emby.EmbyAuthenticationManager
import com.simplecityapps.provider.emby.http.LoginCredentials
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class EmbyConfigurationViewModel @Inject constructor(
    private val embyAuthenticationManager: EmbyAuthenticationManager
) : ViewModel() {

    private val _viewState = MutableStateFlow(EmbyConfigurationViewState.Empty)
    val viewState = _viewState.map { state ->
        EmbyConfigurationViewState(
            address = state.address,
            loginCredentials = state.loginCredentials,
            rememberPassword = state.rememberPassword,
            authenticationState = state.authenticationState,
            canAuthenticate = state.address.isNotBlank() &&
                state.loginCredentials.username.isNotBlank() &&
                state.loginCredentials.password.isNotBlank()
        )
    }.stateIn(
        scope = viewModelScope,
        initialValue = _viewState.value,
        started = SharingStarted.WhileSubscribed()
    )

    fun onInitializeConfiguration() {
        _viewState.update {
            EmbyConfigurationViewState.Empty.copy(
                address = embyAuthenticationManager.getAddress() ?: "",
                rememberPassword = embyAuthenticationManager.getLoginCredentials() != null,
                loginCredentials = embyAuthenticationManager.getLoginCredentials() ?: LoginCredentials.Empty
            )
        }
    }

    fun onAddressChange(address: String) {
        _viewState.update { state ->
            state.copy(address = address)
        }
    }

    fun onUsernameChange(username: String) {
        _viewState.update { state ->
            state.copy(loginCredentials = state.loginCredentials.copy(username = username))
        }
    }

    fun onPasswordChange(password: String) {
        _viewState.update { state ->
            state.copy(loginCredentials = state.loginCredentials.copy(password = password))
        }
    }

    fun onRememberPasswordChange(remember: Boolean) {
        if (!remember) {
            embyAuthenticationManager.setLoginCredentials(null)
        }
        _viewState.update { state ->
            state.copy(rememberPassword = remember)
        }
    }

    fun onAuthenticateClick(
        address: String,
        rememberPassword: Boolean,
        loginCredentials: LoginCredentials
    ) {
        viewModelScope.launch {
            _viewState.update { state ->
                state.copy(authenticationState = EmbyAuthenticationState.Loading)
            }
            embyAuthenticationManager.authenticate(
                address = address,
                loginCredentials = loginCredentials
            ).onSuccess {
                embyAuthenticationManager.setAddress(address)
                if (rememberPassword) {
                    embyAuthenticationManager.setLoginCredentials(loginCredentials)
                }
                _viewState.update { state ->
                    state.copy(authenticationState = EmbyAuthenticationState.Success)
                }
            }.onFailure { error ->
                _viewState.update { state ->
                    state.copy(authenticationState = EmbyAuthenticationState.Error(error))
                }
            }
        }
    }
}
