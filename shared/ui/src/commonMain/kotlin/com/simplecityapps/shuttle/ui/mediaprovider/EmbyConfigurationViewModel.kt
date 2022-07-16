package com.simplecityapps.shuttle.ui.mediaprovider

import com.simplecityapps.shuttle.inject.Inject
import com.simplecityapps.shuttle.inject.hilt.HiltViewModel
import com.simplecityapps.shuttle.logging.logcat
import com.simplecityapps.shuttle.mediaprovider.emby.EmbyAuthenticationManager
import com.simplecityapps.shuttle.mediaprovider.emby.EmbyPreferenceManager
import com.simplecityapps.shuttle.ui.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@HiltViewModel
class EmbyConfigurationViewModel @Inject constructor(
    private val authenticationManager: EmbyAuthenticationManager,
    private val preferenceManager: EmbyPreferenceManager
) : ViewModel() {

    sealed class ViewState {
        data class Configuring(
            val address: String?,
            val userName: String?,
            val password: String?,
            val rememberPassword: Boolean
        ) : ViewState()

        object Authenticating : ViewState()
        object Authenticated : ViewState()
        object AuthenticationFailed : ViewState()
    }

    data class AuthenticationData(
        val address: String,
        val userName: String,
        val password: String,
        val rememberPassword: Boolean
    )

    private val _viewState = MutableStateFlow<ViewState>(ViewState.Configuring(address = null, userName = null, password = null, rememberPassword = false))
    val viewState = _viewState.asStateFlow()

    init {
        coroutineScope.launch {
            _viewState.emit(
                ViewState.Configuring(
                    address = preferenceManager.getAddress().firstOrNull(),
                    userName = preferenceManager.getUserName().firstOrNull(),
                    password = preferenceManager.getPassword().firstOrNull(),
                    rememberPassword = preferenceManager.getRememberPassword().firstOrNull() ?: true
                )
            )
        }
    }

    fun authenticate(authenticationData: AuthenticationData) {
        coroutineScope.launch {
            _viewState.emit(ViewState.Authenticating)
            preferenceManager.setAddress(authenticationData.address)
            preferenceManager.setUserName(authenticationData.userName)
            preferenceManager.setPassword(authenticationData.password)
            authenticationManager.authenticate().fold(
                onSuccess = {
                    _viewState.emit(ViewState.Authenticated)
                },
                onFailure = {
                    logcat { "Authentication failed: ${it.message}" }
                    _viewState.emit(ViewState.AuthenticationFailed)
                }
            )
        }
    }

    fun retry() {
        coroutineScope.launch {
            _viewState.emit(
                ViewState.Configuring(
                    address = preferenceManager.getAddress().firstOrNull(),
                    userName = preferenceManager.getUserName().firstOrNull(),
                    password = preferenceManager.getPassword().firstOrNull(),
                    rememberPassword = preferenceManager.getRememberPassword().firstOrNull() ?: true
                )
            )
        }
    }

    fun setRememberPassword(remember: Boolean) {
        coroutineScope.launch {
            preferenceManager.setRememberPassword(remember)
            if (!remember) {
                preferenceManager.setPassword(null)
            }
        }
    }
}