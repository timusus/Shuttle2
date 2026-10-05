package com.simplecityapps.shuttle.ui.screens.sources.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.server.AccountServer
import com.simplecityapps.mediaprovider.server.DiscoveredServer
import com.simplecityapps.mediaprovider.server.SavedServerLogin
import com.simplecityapps.mediaprovider.server.ServerDiscovery
import com.simplecityapps.mediaprovider.server.ServerLogin
import com.simplecityapps.shuttle.entitlement.ObserveServerStreamingNeedsPro
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.server.CustomHeader
import com.simplecityapps.shuttle.server.ServerOrigin
import com.simplecityapps.shuttle.server.displayFingerprint
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
    val rememberPassword: Boolean = true,
    /** False while the saved password fills its field: it can't be revealed until it's cleared. */
    val passwordRevealable: Boolean = true,
    /** Required fields the user left empty when they last submitted, until they type into them. */
    val missing: Set<ServerSignInField> = emptySet(),
    /** Headers sent with every request to the server (a reverse proxy's access token, say), as typed. */
    val headers: List<CustomHeader> = emptyList(),
    /** Whether the Advanced section, with [headers], is open. */
    val showAdvanced: Boolean = false,
)

sealed interface ServerSignInStep {
    data object Form : ServerSignInStep

    data object Authenticating : ServerSignInStep

    /** Quick Connect's code is up, waiting for the user to approve it in another Jellyfin client. */
    data class AwaitingCode(val code: String) : ServerSignInStep

    /**
     * A sign-in PIN is up, waiting for the user to approve it on the web: at [authUrl] on this device, or by entering
     * [code] at [linkUrl] on another. Plex only.
     */
    data class AwaitingPin(val code: String, val authUrl: String, val linkUrl: String) : ServerSignInStep

    /** The PIN was approved, and the account has more than one server to choose from. */
    data class ChoosingServer(val servers: List<ServerChoice>) : ServerSignInStep

    data object Connected : ServerSignInStep

    data class Failed(val message: String) : ServerSignInStep

    /**
     * The server's certificate isn't one the device trusts (self-signed, say): the user can trust this exact one, by its
     * SHA-256 [fingerprint], for [origin] alone, or go back with [message].
     */
    data class UntrustedCertificate(val origin: ServerOrigin, val fingerprint: String, val message: String) : ServerSignInStep {
        val host: String get() = origin.toString()

        /** [fingerprint] as colon-separated pairs, for the user to compare with the server's. */
        val displayedFingerprint: String get() = displayFingerprint(fingerprint)
    }
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
    /** Servers of [type] that answered on the local network. */
    val discoveredServers: List<DiscoveredServer> = emptyList(),
) {
    /** The discovered servers to offer as addresses: all but the one already typed. */
    val addressSuggestions: List<DiscoveredServer>
        get() = discoveredServers.filterNot { it.address.equals(form.address.trim().trimEnd('/'), ignoreCase = true) }

    /** Plex signs in with a plex.tv PIN and a choice of the account's servers, rather than an address and password. */
    val signsInWithPin: Boolean get() = type == MediaProviderType.Plex

    /** A Subsonic server takes an OpenSubsonic API key in the password field in place of a username and password. */
    val acceptsApiKey: Boolean get() = type == MediaProviderType.Subsonic
}

/** One of the account's servers, as [ServerSignInStep.ChoosingServer] lists it: the user's own, or shared with them. */
data class ServerChoice(val id: String, val name: String, val owned: Boolean)

sealed interface ServerSignInEvent {
    /** The server is signed in: the library can import from it. */
    data object Connected : ServerSignInEvent

    /** The success message has shown for long enough: the dialog can close. */
    data object Finished : ServerSignInEvent

    /** The sign-in PIN's web page, for the user to approve it on: open it in the browser. */
    data class OpenUrl(val url: String) : ServerSignInEvent
}

/**
 * A Jellyfin, Emby or Subsonic server's sign-in: the address and login, starting from the saved ones, then the
 * authentication's progress and outcome. Plex's is a plex.tv PIN instead, then a choice of the account's servers.
 * Signing in to a different server (another address or user) than the one signed in when the sign-in opened removes the
 * downloads of the old server's songs: their paths don't say which server they came from.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ServerSignInViewModel @AssistedInject constructor(
    @Assisted private val type: MediaProviderType,
    private val readServerLogin: ReadServerLogin,
    private val signInToServer: SignInToServer,
    private val forgetServerLogin: ForgetServerLogin,
    observeServerStreamingNeedsPro: ObserveServerStreamingNeedsPro,
    checkQuickConnectAvailable: CheckQuickConnectAvailable,
    private val signInWithQuickConnect: SignInWithQuickConnect,
    private val signInWithPin: SignInWithPin,
    private val connectToAccountServer: ConnectToAccountServer,
    private val songDownloader: SongDownloader,
    serverDiscovery: ServerDiscovery,
    private val readServerHeaders: ReadServerHeaders,
    private val prepareServerConnection: PrepareServerConnection,
    private val trustServerCertificate: TrustServerCertificate,
    private val rejectedServerCertificate: RejectedServerCertificate,
    private val forgetServerConnection: ForgetServerConnection,
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
                headers = saved.address?.let(readServerHeaders::invoke).orEmpty(),
            ).let { it.copy(showAdvanced = it.headers.isNotEmpty()) }
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
    private val discoveredServers = MutableStateFlow(emptyList<DiscoveredServer>())
    private var quickConnectJob: Job? = null

    /** The sign-in to try again once the user trusts the certificate that failed it. */
    private var retryAfterTrust: (() -> Unit)? = null

    /**
     * The servers whose custom headers or trusted certificate this sign-in may have saved: the one it opened with and
     * each address it tried. Only the server's saved address keeps them (see [forgetUnusedConnections]).
     */
    private val touchedOrigins = listOfNotNull(signedInServer.address?.let(ServerOrigin::parse)).toMutableSet()
    private var pinJob: Job? = null

    /** The account's servers, once its PIN has been approved: what [ServerSignInStep.ChoosingServer] lists. */
    private var accountServers: List<AccountServer> = emptyList()

    val uiState: StateFlow<ServerSignInUiState> =
        combine(
            form,
            step,
            events.flow,
            needsPro,
            combine(quickConnectEnabled, discoveredServers, ::Pair),
        ) { form, step, events, needsPro, (quickConnectEnabled, discoveredServers) ->
            ServerSignInUiState(
                type,
                form,
                step,
                events,
                showProDisclosure = needsPro,
                quickConnectEnabled = quickConnectEnabled,
                discoveredServers = discoveredServers,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ServerSignInUiState(type, form.value, step.value, showProDisclosure = needsPro.value),
        )

    init {
        // Looked for once, as the sign-in opens (Jellyfin and Emby only): a server that answers later can still be typed in
        viewModelScope.launch { discoveredServers.value = serverDiscovery.discover(type) }
    }

    fun onAddressChange(address: String) = form.update { it.copy(address = address, missing = it.missing - ServerSignInField.Address) }

    fun onUsernameChange(username: String) = form.update { it.copy(username = username, missing = it.missing - ServerSignInField.Username) }

    fun onPasswordChange(password: String) = form.update {
        it.copy(password = password, passwordRevealable = it.passwordRevealable || password.isEmpty(), missing = it.missing - ServerSignInField.Password)
    }

    fun onShowAdvancedChange(show: Boolean) = form.update { it.copy(showAdvanced = show) }

    fun onAddHeader() = form.update { it.copy(headers = it.headers + CustomHeader("", "")) }

    fun onHeaderChange(index: Int, name: String, value: String) = form.update {
        it.copy(headers = it.headers.mapIndexed { i, header -> if (i == index) CustomHeader(name, value) else header })
    }

    fun onRemoveHeader(index: Int) = form.update { it.copy(headers = it.headers.filterIndexed { i, _ -> i != index }) }

    /** Trusts the certificate [ServerSignInStep.UntrustedCertificate] showed, for its server alone, and signs in again. */
    fun onTrustCertificate() {
        val untrusted = step.value as? ServerSignInStep.UntrustedCertificate ?: return
        trustServerCertificate(untrusted.origin, untrusted.fingerprint)
        step.value = ServerSignInStep.Form
        retryAfterTrust?.invoke()
    }

    /** Turning it off forgets the saved login straight away. */
    fun onRememberPasswordChange(remember: Boolean) {
        form.update { it.copy(rememberPassword = remember) }
        if (!remember) forgetServerLogin(type)
    }

    fun onAuthenticate() {
        if (step.value != ServerSignInStep.Form) return
        if (type == MediaProviderType.Plex) return onSignInWithPin()
        val form = form.value
        val missing = missingFields(form)
        if (missing.isNotEmpty()) {
            this.form.update { it.copy(missing = missing) }
            return
        }
        step.value = ServerSignInStep.Authenticating
        val login = ServerLogin(serverAddress(form.address)!!, form.username, form.password)
        val origin = prepareConnection(login.address, ::onAuthenticate)
        viewModelScope.launch {
            when (val result = signInToServer(type, login, form.rememberPassword)) {
                SignInToServer.Result.Success -> {
                    onSignedIn(login.address, login.username)
                    forgetUnusedConnections(inUse = origin)
                    finishSignIn()
                }

                is SignInToServer.Result.Failure -> step.value = failedStep(origin, result.message)
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
        val origin = prepareConnection(address, ::onUseQuickConnect)
        quickConnectJob = viewModelScope.launch {
            signInWithQuickConnect(type, address).collect { state ->
                when (state) {
                    is SignInWithQuickConnect.State.AwaitingApproval -> step.value = ServerSignInStep.AwaitingCode(state.code)

                    SignInWithQuickConnect.State.Success -> {
                        // Quick Connect doesn't take a username, so only a different address tells it's another server
                        onSignedIn(address, username = null)
                        forgetUnusedConnections(inUse = origin)
                        finishSignIn()
                    }

                    SignInWithQuickConnect.State.Expired -> step.value = ServerSignInStep.Failed(QUICK_CONNECT_EXPIRED_MESSAGE)

                    is SignInWithQuickConnect.State.Failed -> step.value = failedStep(origin, state.message)
                }
            }
        }
    }

    /** Cancels an in-flight Quick Connect poll, whether the user backed out or the dialog is closing. */
    fun onCancelQuickConnect() {
        quickConnectJob?.cancel()
        quickConnectJob = null
        step.value = ServerSignInStep.Form
        forgetUnusedConnections()
    }

    /**
     * Starts a plex.tv sign-in: shows the PIN and opens its web page, polls until the user approves it, then connects
     * to the account's server, or lists its servers to choose from when it has more than one.
     */
    fun onSignInWithPin() {
        if (step.value != ServerSignInStep.Form) return
        if (pinJob?.isActive == true) return
        pinJob = viewModelScope.launch {
            signInWithPin(type).collect { state ->
                when (state) {
                    is SignInWithPin.State.AwaitingApproval -> {
                        step.value = ServerSignInStep.AwaitingPin(state.pin.code, state.pin.authUrl, state.pin.linkUrl)
                        events.post(ServerSignInEvent.OpenUrl(state.pin.authUrl))
                    }

                    is SignInWithPin.State.Approved -> {
                        accountServers = state.servers
                        val only = state.servers.singleOrNull()
                        if (only != null) {
                            connectTo(only)
                        } else {
                            step.value = ServerSignInStep.ChoosingServer(state.servers.map { ServerChoice(it.id, it.name, it.owned) })
                        }
                    }

                    SignInWithPin.State.Expired -> step.value = ServerSignInStep.Failed(PIN_EXPIRED_MESSAGE)

                    is SignInWithPin.State.Failed -> step.value = ServerSignInStep.Failed(state.message)
                }
            }
        }
    }

    /** Signs in to the account's server [id], from [ServerSignInStep.ChoosingServer]. */
    fun onChooseServer(id: String) {
        if (step.value !is ServerSignInStep.ChoosingServer) return
        val server = accountServers.firstOrNull { it.id == id } ?: return
        pinJob = viewModelScope.launch { connectTo(server) }
    }

    /** Stops waiting on the PIN, or choosing a server, and goes back to the start. */
    fun onCancelPin() {
        pinJob?.cancel()
        pinJob = null
        accountServers = emptyList()
        step.value = ServerSignInStep.Form
    }

    /**
     * The sign-in has left the screen (iOS: its view disappeared, while the view model stays cached for the rest of the
     * setup): a Quick Connect code or PIN nobody can see any more stops polling. A sign-in that has already connected
     * is left to finish.
     */
    fun onLeave() {
        when (step.value) {
            is ServerSignInStep.AwaitingCode -> onCancelQuickConnect()
            is ServerSignInStep.AwaitingPin, is ServerSignInStep.ChoosingServer -> onCancelPin()
            ServerSignInStep.Form, is ServerSignInStep.Failed, is ServerSignInStep.UntrustedCertificate -> forgetUnusedConnections()
            else -> Unit
        }
    }

    override fun onCleared() {
        forgetUnusedConnections()
    }

    fun onEventHandled(id: Long) = events.consume(id)

    /** Signs in to the account's [server]; switching from another server removes the downloads of the old one's songs. */
    private suspend fun connectTo(server: AccountServer) {
        step.value = ServerSignInStep.Authenticating
        when (val result = connectToAccountServer(type, server)) {
            is ConnectToAccountServer.Result.Success -> {
                if (result.switchedServer) songDownloader.removeAll(type)
                finishSignIn()
            }

            is ConnectToAccountServer.Result.Failure -> step.value = ServerSignInStep.Failed(result.message)
        }
    }

    /** Shows the success message for a moment, then lets the dialog close. */
    private suspend fun finishSignIn() {
        step.value = ServerSignInStep.Connected
        events.post(ServerSignInEvent.Connected)
        delay(SUCCESS_SHOWN_MILLIS)
        events.post(ServerSignInEvent.Finished)
    }

    /** Removes the downloads when [address] and [username] (if known) name another server than [signedInServer]. */
    private suspend fun onSignedIn(address: String, username: String?) {
        val previous = signedInServer
        val otherAddress = previous.address?.let { !isSameServerAddress(it, address) } ?: true
        val otherUser = username != null && previous.username != null && !previous.username.equals(username, ignoreCase = true)
        if (otherAddress || otherUser) songDownloader.removeAll(type)
        signedInServer = SavedServerLogin(address, username ?: previous.username.takeUnless { otherAddress })
    }

    /**
     * Saves the typed headers for the server at [address] before it's contacted, so the sign-in sends them, and forgets
     * any certificate it refused before: a refusal recorded from here on is this attempt's, which [retry] repeats.
     */
    private fun prepareConnection(address: String, retry: () -> Unit): ServerOrigin? {
        val origin = ServerOrigin.parse(address) ?: return null
        forgetUnusedConnections(inUse = origin)
        touchedOrigins += origin
        prepareServerConnection(origin, form.value.headers)
        retryAfterTrust = retry
        return origin
    }

    /**
     * Forgets the headers and trusted certificate this sign-in saved for any server but the one whose address is saved
     * (which removing the server forgets, [ForgetServer]) and [inUse]: an address the user tried and moved on from, or
     * the server another one replaced, keeps no proxy token or trusted certificate behind.
     */
    private fun forgetUnusedConnections(inUse: ServerOrigin? = null) {
        val saved = readServerLogin(type).address?.let(ServerOrigin::parse)
        val unused = touchedOrigins.filter { it != saved && it != inUse }
        unused.forEach { forgetServerConnection(it) }
        touchedOrigins -= unused.toSet()
    }

    /** The step a failed sign-in to [origin] shows: the certificate to trust when it refused one, else [message]. */
    private fun failedStep(origin: ServerOrigin?, message: String): ServerSignInStep {
        val fingerprint = origin?.let { rejectedServerCertificate(it) }
        return if (fingerprint != null) ServerSignInStep.UntrustedCertificate(origin, fingerprint, message) else ServerSignInStep.Failed(message)
    }

    private fun missingFields(form: ServerSignInForm): Set<ServerSignInField> = buildSet {
        if (serverAddress(form.address) == null) add(ServerSignInField.Address)
        // A Subsonic sign-in with no username is an API key's, in the password field
        if (form.username.isEmpty() && type != MediaProviderType.Subsonic) add(ServerSignInField.Username)
        if (type == MediaProviderType.Subsonic && form.password.isEmpty()) add(ServerSignInField.Password)
    }

    private companion object {
        const val DEFAULT_ADDRESS = "http://"
        const val SUCCESS_SHOWN_MILLIS = 1_000L
        const val QUICK_CONNECT_CHECK_DEBOUNCE_MILLIS = 500L
        const val QUICK_CONNECT_EXPIRED_MESSAGE = "The code expired before it was approved."
        const val PIN_EXPIRED_MESSAGE = "The sign-in code expired before it was approved."
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
