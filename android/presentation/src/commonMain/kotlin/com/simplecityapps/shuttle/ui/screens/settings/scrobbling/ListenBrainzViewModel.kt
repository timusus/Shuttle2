package com.simplecityapps.shuttle.ui.screens.settings.scrobbling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.scrobbling.ListenBrainzAccountState
import com.simplecityapps.shuttle.scrobbling.ListenBrainzSignInResult
import com.simplecityapps.shuttle.scrobbling.ObserveListenBrainzAccount
import com.simplecityapps.shuttle.scrobbling.SignInToListenBrainz
import com.simplecityapps.shuttle.scrobbling.SignOutOfListenBrainz
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Something to tell the user once, after a ListenBrainz sign-in. */
enum class ListenBrainzMessage { InvalidToken, Failed }

data class ListenBrainzUiState(
    val account: ListenBrainzAccountState = ListenBrainzAccountState.SignedOut,
    val message: ListenBrainzMessage? = null,
    val busy: Boolean = false
)

/** The ListenBrainz section of Settings > Playback & sound > Scrobbling (#503): paste a token, see who's signed in, sign out. */
@ViewModelKey(ListenBrainzViewModel::class)
@ContributesIntoMap(AppScope::class)
class ListenBrainzViewModel @Inject constructor(
    observeAccount: ObserveListenBrainzAccount,
    private val signIn: SignInToListenBrainz,
    private val signOut: SignOutOfListenBrainz
) : ViewModel() {
    private val transient = MutableStateFlow(ListenBrainzUiState())

    val uiState: StateFlow<ListenBrainzUiState> = combine(observeAccount(), transient) { account, transient ->
        transient.copy(account = account)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListenBrainzUiState())

    fun onSignIn(token: String) {
        launchBusy {
            val message = when (signIn(token)) {
                ListenBrainzSignInResult.InvalidToken -> ListenBrainzMessage.InvalidToken
                ListenBrainzSignInResult.Failed -> ListenBrainzMessage.Failed
                ListenBrainzSignInResult.SignedIn -> null
            }
            transient.update { it.copy(message = message) }
        }
    }

    fun onSignOut() {
        launchBusy { signOut() }
    }

    fun onMessageShown() {
        transient.update { it.copy(message = null) }
    }

    private fun launchBusy(block: suspend () -> Unit) {
        if (transient.value.busy) return
        transient.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                transient.update { it.copy(busy = false) }
            }
        }
    }
}
