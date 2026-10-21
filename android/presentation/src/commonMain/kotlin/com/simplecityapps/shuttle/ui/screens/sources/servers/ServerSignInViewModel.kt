package com.simplecityapps.shuttle.ui.screens.sources.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.server.AccountServer
import com.simplecityapps.mediaprovider.server.DiscoveredServer
import com.simplecityapps.mediaprovider.server.SavedServerLogin
import com.simplecityapps.mediaprovider.server.ServerAuthentication
import com.simplecityapps.mediaprovider.server.ServerDiscovery
import com.simplecityapps.mediaprovider.server.ServerLogin
import com.simplecityapps.shuttle.entitlement.ObserveServerStreamingNeedsPro
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.server.CustomHeader
import com.simplecityapps.shuttle.server.ServerConnectionStore
import com.simplecityapps.shuttle.server.ServerOrigin
import com.simplecityapps.shuttle.ui.actions.SongDownloader
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
    private val authentications: Map<MediaProviderType, ServerAuthentication>,
    observeServerStreamingNeedsPro: ObserveServerStreamingNeedsPro,
    checkQuickConnectAvailable: CheckQuickConnectAvailable,
    private val signInWithQuickConnect: SignInWithQuickConnect,
    private val signInWithPin: SignInWithPin,
    private val connectToAccountServer: ConnectToAccountServer,
    private val songDownloader: SongDownloader,
    private val serverDiscovery: ServerDiscovery,
    private val localNetworkAccess: LocalNetworkAccess,
    prepareServerConnection: PrepareServerConnection,
    private val serverConnections: ServerConnectionStore,
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
                headers = saved.address?.let { serverConnections.connection(it).headers }.orEmpty(),
            ).let { it.copy(showAdvanced = it.headers.isNotEmpty()) }
        },
    )
    private val step = MutableStateFlow<ServerSignInStep>(ServerSignInStep.Form)
    private val events = PendingEvents<ServerSignInEvent>()
    private val needsPro = observeServerStreamingNeedsPro()
    private val gate = LocalNetworkGate(localNetworkAccess, ::discoverOnce)

    /** What the failed sign-in does when the user taps Retry after refusing the local-network permission. */
    private var retryAfterLocalNetworkDenied: (() -> Unit)? = null
    private val searchesNetwork = type == MediaProviderType.Jellyfin || type == MediaProviderType.Emby
    private var discoveryStarted = false
    private val quickConnectEnabled = form
        .map { it.address }
        .distinctUntilChanged()
        .debounce(QUICK_CONNECT_CHECK_DEBOUNCE_MILLIS)
        .mapLatest { address ->
            serverAddress(address)?.let { url ->
                // Only Jellyfin's probe reaches the server. A refusal leaves Quick Connect off, as an unreachable server does
                val reachable = type != MediaProviderType.Jellyfin || gate.await(localNetworkAccess.needsRequest(url))
                reachable && checkQuickConnectAvailable(type, url)
            } ?: false
        }
        .onStart { emit(false) }
    private val discoveredServers = MutableStateFlow(emptyList<DiscoveredServer>())
    private var quickConnectJob: Job? = null

    /** The sign-in to try again once the user trusts the certificate that failed it. */
    private var retryAfterTrust: (() -> Unit)? = null

    private val connections = SignInConnections(
        type,
        readServerLogin,
        prepareServerConnection,
        serverConnections,
        initial = signedInServer.address?.let(ServerOrigin::parse),
    )
    private var pinJob: Job? = null

    /** The account's servers, once its PIN has been approved: what [ServerSignInStep.ChoosingServer] lists. */
    private var accountServers: List<AccountServer> = emptyList()

    val uiState: StateFlow<ServerSignInUiState> =
        combine(
            form,
            step,
            events.flow,
            needsPro,
            combine(quickConnectEnabled, discoveredServers, gate.requested, ::Triple),
        ) { form, step, events, needsPro, (quickConnectEnabled, discoveredServers, localNetworkRequested) ->
            ServerSignInUiState(
                type,
                form,
                step,
                events,
                showProDisclosure = needsPro,
                quickConnectEnabled = quickConnectEnabled,
                discoveredServers = discoveredServers,
                localNetworkRequested = localNetworkRequested,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ServerSignInUiState(type, form.value, step.value, showProDisclosure = needsPro.value),
        )

    init {
        // Looked for once, as the sign-in opens (Jellyfin and Emby only): a server that answers later can still be typed in
        viewModelScope.launch {
            if (searchesNetwork && gate.await(localNetworkAccess.needsRequestToSearch())) discoverOnce()
        }
    }

    private fun discoverOnce() {
        if (!searchesNetwork || discoveryStarted) return
        discoveryStarted = true
        viewModelScope.launch { discoveredServers.value = serverDiscovery.discover(type) }
    }

    /** The screen's answer to [ServerSignInUiState.localNetworkRequested]: whether the user allowed local-network access. */
    fun onLocalNetworkResult(granted: Boolean) = gate.onResult(granted)

    private fun localNetworkDenied(retry: () -> Unit) {
        retryAfterLocalNetworkDenied = retry
        step.value = ServerSignInStep.Failed(LOCAL_NETWORK_DENIED_MESSAGE)
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
        connections.trust(untrusted)
        step.value = ServerSignInStep.Form
        retryAfterTrust?.invoke()
    }

    /** Turning it off forgets the saved login straight away. */
    fun onRememberPasswordChange(remember: Boolean) {
        form.update { it.copy(rememberPassword = remember) }
        if (!remember) authentications.getValue(type).forgetLogin()
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
            if (!gate.await(localNetworkAccess.needsRequest(login.address))) return@launch localNetworkDenied(::onAuthenticate)
            when (val result = signInToServer(type, login, form.rememberPassword)) {
                SignInToServer.Result.Success -> {
                    onSignedIn(login.address, login.username)
                    connections.forgetUnused(inUse = origin)
                    finishSignIn()
                }

                is SignInToServer.Result.Failure -> step.value = connections.failedStep(origin, result.message)
            }
        }
    }

    /** Back to the form after a failed sign-in, which asks for the local-network permission again if that's what failed. */
    fun onRetry() {
        step.value = ServerSignInStep.Form
        gate.reset()
        val retry = retryAfterLocalNetworkDenied ?: return
        retryAfterLocalNetworkDenied = null
        retry()
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
            if (!gate.await(localNetworkAccess.needsRequest(address))) return@launch localNetworkDenied(::onUseQuickConnect)
            signInWithQuickConnect(type, address).collect { state ->
                when (state) {
                    is SignInWithQuickConnect.State.AwaitingApproval -> step.value = ServerSignInStep.AwaitingCode(state.code)

                    SignInWithQuickConnect.State.Success -> {
                        // Quick Connect doesn't take a username, so only a different address tells it's another server
                        onSignedIn(address, username = null)
                        connections.forgetUnused(inUse = origin)
                        finishSignIn()
                    }

                    SignInWithQuickConnect.State.Expired -> step.value = ServerSignInStep.Failed(QUICK_CONNECT_EXPIRED_MESSAGE)

                    is SignInWithQuickConnect.State.Failed -> step.value = connections.failedStep(origin, state.message)
                }
            }
        }
    }

    /** Cancels an in-flight Quick Connect poll, whether the user backed out or the dialog is closing. */
    fun onCancelQuickConnect() {
        quickConnectJob?.cancel()
        quickConnectJob = null
        step.value = ServerSignInStep.Form
        connections.forgetUnused()
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
            ServerSignInStep.Form, is ServerSignInStep.Failed, is ServerSignInStep.UntrustedCertificate -> connections.forgetUnused()
            else -> Unit
        }
    }

    override fun onCleared() {
        connections.forgetUnused()
    }

    fun onEventHandled(id: Long) = events.consume(id)

    /** Signs in to the account's [server]; switching from another server removes the downloads of the old one's songs. */
    private suspend fun connectTo(server: AccountServer) {
        step.value = ServerSignInStep.Authenticating
        // Connecting probes each of the server's addresses, the local ones among them. Plex's LAN URIs are plex.direct
        // hostnames, so the connection's own local flag and plain address count as well as the URI
        val needsRequest = server.connections.any { connection ->
            (connection.local && localNetworkAccess.needsRequestToSearch()) ||
                localNetworkAccess.needsRequest(connection.uri) ||
                connection.address?.let(localNetworkAccess::needsRequest) == true
        }
        if (!gate.await(needsRequest)) return localNetworkDenied { pinJob = viewModelScope.launch { connectTo(server) } }
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

    /** Saves the typed headers for the server at [address] before it's contacted; [retry] repeats the attempt once its certificate is trusted. */
    private fun prepareConnection(address: String, retry: () -> Unit): ServerOrigin? {
        val origin = connections.prepare(address, form.value.headers) ?: return null
        retryAfterTrust = retry
        return origin
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
        const val LOCAL_NETWORK_DENIED_MESSAGE =
            "Shuttle needs permission to reach devices on your local network to connect to a server at this address. " +
                "Tap Retry and allow it, or turn on Nearby devices for Shuttle in system settings."
        const val PIN_EXPIRED_MESSAGE = "The sign-in code expired before it was approved."
    }
}
