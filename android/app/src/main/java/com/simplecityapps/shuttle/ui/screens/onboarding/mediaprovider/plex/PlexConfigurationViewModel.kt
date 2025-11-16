package com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider.plex

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.provider.plex.PlexAuthenticationManager
import com.simplecityapps.provider.plex.http.LoginCredentials
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlexConfigurationViewModel @Inject constructor(
    private val plexAuthenticationManager: PlexAuthenticationManager
) : ViewModel() {

    private val _address = MutableStateFlow(
        plexAuthenticationManager.getAddress() ?: ""
    )
    val address = _address.asStateFlow()

    private val _loginCredentials = MutableStateFlow(
        plexAuthenticationManager.getLoginCredentials() ?: LoginCredentials.Empty
    )
    val loginCredentials = _loginCredentials.asStateFlow()

    val canAuthenticate = combine(
        _address,
        _loginCredentials
    ) { address, (username, password, _) ->
        address.isNotBlank() && username.isNotBlank() && password.isNotBlank()
    }.stateIn(
        viewModelScope,
        initialValue = false,
        started = SharingStarted.WhileSubscribed()
    )

    private val _rememberPassword = MutableStateFlow(
        plexAuthenticationManager.getLoginCredentials() != null
    )
    val rememberPassword = _rememberPassword.asStateFlow()

    private val _authenticationSuccess = MutableStateFlow<Boolean?>(null)
    val authenticationSuccess = _authenticationSuccess.asStateFlow()

    fun onAddressChange(address: String) {
        _address.update { address }
    }

    fun onUsernameChange(username: String) {
        _loginCredentials.update { credentials -> credentials.copy(username = username) }
    }

    fun onPasswordChange(password: String) {
        _loginCredentials.update { credentials -> credentials.copy(password = password) }
    }

    fun onAuthCodeChange(authCode: String) {
        _loginCredentials.update { credentials -> credentials.copy(authCode = authCode) }
    }

    fun onRememberPasswordChange(remember: Boolean) {
        if (!remember) {
            plexAuthenticationManager.setLoginCredentials(null)
        }
        _rememberPassword.update { remember }
    }

    fun onAuthenticateClick(address: String, loginCredentials: LoginCredentials) {
        viewModelScope.launch {
            plexAuthenticationManager.authenticate(
                address = address,
                loginCredentials = loginCredentials
            ).onSuccess {
                plexAuthenticationManager.setLoginCredentials(loginCredentials)
                _authenticationSuccess.update { true }
            }.onFailure {
                _authenticationSuccess.update { false }
            }
        }
    }
}
