package com.simplecityapps.shuttle.ui.screens.settings.scrobbling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.scrobbling.FinishLastFmSignIn
import com.simplecityapps.shuttle.scrobbling.LastFmAccountState
import com.simplecityapps.shuttle.scrobbling.LastFmSignInResult
import com.simplecityapps.shuttle.scrobbling.ObserveLastFmAccount
import com.simplecityapps.shuttle.scrobbling.SignOutOfLastFm
import com.simplecityapps.shuttle.scrobbling.StartLastFmSignIn
import com.simplecityapps.shuttle.settings.ScrobblingSettings
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

/** Something to tell the user once, after a sign-in step. */
enum class ScrobblingMessage { NotApproved, Expired, Failed }

data class ScrobblingUiState(
    val account: LastFmAccountState = LastFmAccountState.SignedOut,
    val scrobbleServerStreams: Boolean = false,
    /** The last.fm page to open in the browser, once; consumed by [ScrobblingViewModel.onApprovalUrlOpened]. */
    val approvalUrl: String? = null,
    val message: ScrobblingMessage? = null,
    val busy: Boolean = false
)

/** Settings > Playback & sound > Scrobbling (#503): the Last.fm account and the server-streams switch. */
@ViewModelKey(ScrobblingViewModel::class)
@ContributesIntoMap(AppScope::class)
class ScrobblingViewModel @Inject constructor(
    observeAccount: ObserveLastFmAccount,
    private val startSignIn: StartLastFmSignIn,
    private val finishSignIn: FinishLastFmSignIn,
    private val signOut: SignOutOfLastFm,
    private val settings: ScrobblingSettings
) : ViewModel() {
    private val transient = MutableStateFlow(ScrobblingUiState())

    val uiState: StateFlow<ScrobblingUiState> = combine(
        observeAccount(),
        settings.scrobbleServerStreams.flow,
        transient
    ) { account, serverStreams, transient ->
        transient.copy(account = account, scrobbleServerStreams = serverStreams)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScrobblingUiState(scrobbleServerStreams = settings.scrobbleServerStreams.value))

    fun onSignIn() {
        launchBusy {
            val url = startSignIn()
            transient.update { it.copy(approvalUrl = url, message = if (url == null) ScrobblingMessage.Failed else null) }
        }
    }

    /** Trades an approved token for a session; also run when the user comes back from the browser. */
    fun onFinishSignIn() {
        launchBusy {
            val message = when (finishSignIn()) {
                LastFmSignInResult.NotApproved -> ScrobblingMessage.NotApproved
                LastFmSignInResult.Expired -> ScrobblingMessage.Expired
                LastFmSignInResult.Failed -> ScrobblingMessage.Failed
                LastFmSignInResult.SignedIn, LastFmSignInResult.NotStarted -> null
            }
            transient.update { it.copy(message = message) }
        }
    }

    fun onSignOut() {
        launchBusy { signOut() }
    }

    fun onServerStreamsChange(enabled: Boolean) {
        settings.scrobbleServerStreams.value = enabled
    }

    fun onApprovalUrlOpened() {
        transient.update { it.copy(approvalUrl = null) }
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
