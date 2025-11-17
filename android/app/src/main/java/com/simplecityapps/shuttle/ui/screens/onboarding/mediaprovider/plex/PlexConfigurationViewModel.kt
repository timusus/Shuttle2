package com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider.plex

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.provider.plex.PlexAuthenticationManager
import com.simplecityapps.provider.plex.http.LoginCredentials
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PlexConfigurationViewModel @Inject constructor(
    private val plexAuthenticationManager: PlexAuthenticationManager
) : ViewModel() {

    private val _viewState = MutableStateFlow(
        PlexConfigurationViewState.Empty.copy(
            address = plexAuthenticationManager.getAddress() ?: "",
            rememberPassword = plexAuthenticationManager.getLoginCredentials() != null,
            loginCredentials = plexAuthenticationManager.getLoginCredentials() ?: LoginCredentials.Empty
        )
    )
    val viewState = _viewState.map { state ->
        PlexConfigurationViewState(
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
        started = SharingStarted.WhileSubscribed(),
        initialValue = PlexConfigurationViewState.Empty.copy(
            address = plexAuthenticationManager.getAddress() ?: "",
            rememberPassword = plexAuthenticationManager.getLoginCredentials() != null,
            loginCredentials = plexAuthenticationManager.getLoginCredentials() ?: LoginCredentials.Empty
        )
    )

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

    fun onAuthCodeChange(authCode: String) {
        _viewState.update { state ->
            state.copy(loginCredentials = state.loginCredentials.copy(authCode = authCode))
        }
    }

    fun onRememberPasswordChange(remember: Boolean) {
        if (!remember) {
            plexAuthenticationManager.setLoginCredentials(null)
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
                state.copy(authenticationState = PlexAuthenticationState.Loading)
            }
            plexAuthenticationManager.authenticate(
                address = address,
                loginCredentials = loginCredentials
            ).onSuccess {
                plexAuthenticationManager.setAddress(address)
                if (rememberPassword) {
                    plexAuthenticationManager.setLoginCredentials(loginCredentials)
                }
                _viewState.update { state ->
                    state.copy(authenticationState = PlexAuthenticationState.Success)
                }
            }.onFailure { error ->
                _viewState.update { state ->
                    state.copy(authenticationState = PlexAuthenticationState.Error(error))
                }
            }
        }
    }

    fun onConsumeAuthenticationSuccess() {
        _viewState.update { state ->
            state.copy(authenticationState = null)
        }
    }
}
