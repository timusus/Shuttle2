package com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider.jellyfin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.provider.jellyfin.JellyfinAuthenticationManager
import com.simplecityapps.provider.jellyfin.http.LoginCredentials
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class JellyfinConfigurationViewModel @Inject constructor(
    private val jellyfinAuthenticationManager: JellyfinAuthenticationManager
) : ViewModel() {

    private val _viewState = MutableStateFlow(JellyfinConfigurationViewState.Empty)
    val viewState = _viewState.map { state ->
        JellyfinConfigurationViewState(
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
            JellyfinConfigurationViewState.Empty.copy(
                address = jellyfinAuthenticationManager.getAddress() ?: "",
                rememberPassword = jellyfinAuthenticationManager.getLoginCredentials() != null,
                loginCredentials = jellyfinAuthenticationManager.getLoginCredentials() ?: LoginCredentials.Empty
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
            jellyfinAuthenticationManager.setLoginCredentials(null)
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
                state.copy(authenticationState = JellyfinAuthenticationState.Loading)
            }
            jellyfinAuthenticationManager.authenticate(
                address = address,
                loginCredentials = loginCredentials
            ).onSuccess {
                jellyfinAuthenticationManager.setAddress(address)
                if (rememberPassword) {
                    jellyfinAuthenticationManager.setLoginCredentials(loginCredentials)
                }
                _viewState.update { state ->
                    state.copy(authenticationState = JellyfinAuthenticationState.Success)
                }
            }.onFailure { error ->
                _viewState.update { state ->
                    state.copy(authenticationState = JellyfinAuthenticationState.Error(error))
                }
            }
        }
    }
}
