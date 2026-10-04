package com.simplecityapps.shuttle.ui.screens.sources.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.server.SavedServerLogin
import com.simplecityapps.mediaprovider.server.ServerLogin
import com.simplecityapps.shuttle.entitlement.ObserveServerStreamingNeedsPro
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.actions.SongDownloader
import com.simplecityapps.shuttle.ui.common.PendingEvent
import com.simplecityapps.shuttle.ui.common.PendingEvents
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
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

    /** Quick Connect's code is up, waiting for the user to approve it in another Jellyfin client. */
    data class AwaitingCode(val code: String) : ServerSignInStep

    data object Connected : ServerSignInStep

    data class Failed(val message: String) : ServerSignInStep
}

data class ServerSignInUiState(
    val type: MediaProviderType,
    val form: ServerSignInForm = ServerSignInForm(),
    val step: ServerSignInStep = ServerSignInStep.Form,
    val events: List<PendingEvent<ServerSignInEvent>> = emptyList(),
    /** True for a Free user, who hasn't had the server trial yet or has used it up: streaming is S2 Pro. */
    val showProDisclosure: Boolean = false,
    /** True when [type]'s server at the typed address reports Quick Connect support. Jellyfin only. */
    val quickConnectEnabled: Boolean = false,
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
 * authentication's progress and outcome. Signing in to a different server (another address or user) than the one signed
 * in when the sign-in opened removes the downloads of the old server's songs: their paths don't say which server they
 * came from.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ServerSignInViewModel @AssistedInject constructor(
    @Assisted private val type: MediaProviderType,
    readServerLogin: ReadServerLogin,
    private val signInToServer: SignInToServer,
    private val forgetServerLogin: ForgetServerLogin,
    observeServerStreamingNeedsPro: ObserveServerStreamingNeedsPro,
    checkQuickConnectAvailable: CheckQuickConnectAvailable,
    private val signInWithQuickConnect: SignInWithQuickConnect,
    private val songDownloader: SongDownloader,
) : ViewModel() {
    @AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(type: MediaProviderType): ServerSignInViewModel
    }

    /**
     * The server whose songs any downloads came from: the saved login as the sign-in opened, then each successful
     * sign-in. Read once up front, as every attempt saves its address before contacting the server, and turning off
     * remember password forgets the username.
     */
    private var signedInServer: SavedServerLogin = readServerLogin(type)

    private val form = MutableStateFlow(
        signedInServer.let { saved ->
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
    private val needsPro = observeServerStreamingNeedsPro()
    private val quickConnectEnabled = form
        .map { it.address }
        .distinctUntilChanged()
        .debounce(QUICK_CONNECT_CHECK_DEBOUNCE_MILLIS)
        .mapLatest { address -> serverAddress(address)?.let { checkQuickConnectAvailable(type, it) } ?: false }
        .onStart { emit(false) }
    private var quickConnectJob: Job? = null

    val uiState: StateFlow<ServerSignInUiState> =
        combine(form, step, events.flow, needsPro, quickConnectEnabled) { form, step, events, needsPro, quickConnectEnabled ->
            ServerSignInUiState(type, form, step, events, showProDisclosure = needsPro, quickConnectEnabled = quickConnectEnabled)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ServerSignInUiState(type, form.value, step.value, showProDisclosure = needsPro.value),
        )

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
        val login = ServerLogin(serverAddress(form.address)!!, form.username, form.password, form.authCode.takeIf { uiState.value.asksForAuthCode })
        viewModelScope.launch {
            when (val result = signInToServer(type, login, form.rememberPassword)) {
                SignInToServer.Result.Success -> {
                    onSignedIn(login.address, login.username)
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

    fun onUseQuickConnect() {
        if (step.value != ServerSignInStep.Form) return
        if (quickConnectJob?.isActive == true) return
        val address = serverAddress(form.value.address) ?: run {
            form.update { it.copy(missing = it.missing + ServerSignInField.Address) }
            return
        }
        quickConnectJob = viewModelScope.launch {
            signInWithQuickConnect(type, address).collect { state ->
                when (state) {
                    is SignInWithQuickConnect.State.AwaitingApproval -> step.value = ServerSignInStep.AwaitingCode(state.code)

                    SignInWithQuickConnect.State.Success -> {
                        // Quick Connect doesn't take a username, so only a different address tells it's another server
                        onSignedIn(address, username = null)
                        step.value = ServerSignInStep.Connected
                        events.post(ServerSignInEvent.Connected)
                        delay(SUCCESS_SHOWN_MILLIS)
                        events.post(ServerSignInEvent.Finished)
                    }

                    SignInWithQuickConnect.State.Expired -> step.value = ServerSignInStep.Failed(QUICK_CONNECT_EXPIRED_MESSAGE)

                    is SignInWithQuickConnect.State.Failed -> step.value = ServerSignInStep.Failed(state.message)
                }
            }
        }
    }

    /** Cancels an in-flight Quick Connect poll, whether the user backed out or the dialog is closing. */
    fun onCancelQuickConnect() {
        quickConnectJob?.cancel()
        quickConnectJob = null
        step.value = ServerSignInStep.Form
    }

    /**
     * The sign-in has left the screen (iOS: its view disappeared, while the view model stays cached for the rest of the
     * setup): a Quick Connect code nobody can see any more stops polling. A sign-in that has already connected is left
     * to finish.
     */
    fun onLeave() {
        if (step.value is ServerSignInStep.AwaitingCode) onCancelQuickConnect()
    }

    fun onEventHandled(id: Long) = events.consume(id)

    /** Removes the downloads when [address] and [username] (if known) name another server than [signedInServer]. */
    private suspend fun onSignedIn(address: String, username: String?) {
        val previous = signedInServer
        val otherAddress = previous.address?.let { !isSameServerAddress(it, address) } ?: true
        val otherUser = username != null && previous.username != null && !previous.username.equals(username, ignoreCase = true)
        if (otherAddress || otherUser) songDownloader.removeAll(type)
        signedInServer = SavedServerLogin(address, username ?: previous.username.takeUnless { otherAddress })
    }

    private fun missingFields(form: ServerSignInForm): Set<ServerSignInField> = buildSet {
        if (serverAddress(form.address) == null) add(ServerSignInField.Address)
        if (form.username.isEmpty()) add(ServerSignInField.Username)
        if (type == MediaProviderType.Plex && form.password.isEmpty()) add(ServerSignInField.Password)
    }

    private companion object {
        const val DEFAULT_ADDRESS = "http://"
        const val SUCCESS_SHOWN_MILLIS = 1_000L
        const val QUICK_CONNECT_CHECK_DEBOUNCE_MILLIS = 500L
        const val QUICK_CONNECT_EXPIRED_MESSAGE = "The code expired before it was approved."
    }
}

/**
 * The server address [typed] as the sign-in uses it: trimmed, `http://` added when it has no scheme, and without
 * trailing slashes. An IPv6 host may come bracketed (`[fe80::1]:8096`) or bare (`fe80::1`, wrapped in brackets here,
 * so it can't carry a port). Null when there's no host to connect to (empty, only a scheme, an unclosed bracket or
 * with a space in it).
 */
fun serverAddress(typed: String): String? {
    val trimmed = typed.trim()
    if (trimmed.any(Char::isWhitespace)) return null
    val scheme = if ("://" in trimmed) trimmed.substringBefore("://") else "http"
    val rest = trimmed.substringAfter("://").trimEnd('/')
    val authority = rest.substringBefore('/')
    val path = rest.removePrefix(authority)
    val hostAndPort =
        when {
            // Bracketed IPv6, then an optional port
            authority.startsWith('[') -> {
                val host = authority.substringBefore(']', missingDelimiterValue = "").removePrefix("[")
                val afterHost = authority.substringAfter(']', missingDelimiterValue = "")
                authority.takeIf { host.isNotEmpty() && (afterHost.isEmpty() || afterHost.startsWith(':')) }
            }

            // Bare IPv6: no room for a port, so all of it is the host
            authority.count { it == ':' } >= 2 -> "[$authority]"

            else -> authority.takeIf { authority.substringBefore(':').isNotEmpty() }
        }
    return hostAndPort?.let { "$scheme://$it$path" }
}

/**
 * Whether two server addresses name the same server: the host compared case-insensitively, then the port and path.
 * The scheme (`http` → `https`), a default port and trailing slashes don't count.
 */
fun isSameServerAddress(a: String, b: String): Boolean = serverIdentity(a)?.let { it == serverIdentity(b) } ?: false

private fun serverIdentity(address: String): String? {
    val normalised = serverAddress(address) ?: return null
    val scheme = normalised.substringBefore("://").lowercase()
    val rest = normalised.substringAfter("://")
    val authority = rest.substringBefore('/')
    val path = rest.removePrefix(authority)
    val host = if (authority.startsWith('[')) authority.substringBefore(']') + "]" else authority.substringBefore(':')
    val port = authority.removePrefix(host).removePrefix(":")
    val defaultPort = when (scheme) {
        "http" -> "80"
        "https" -> "443"
        else -> null
    }
    return "${host.lowercase()}:${port.takeUnless { it == defaultPort }.orEmpty()}$path"
}
