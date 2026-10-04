package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.fakes.FakeQuickConnectAuthentication
import com.simplecityapps.fakes.FakeServerAuthentication
import com.simplecityapps.fakes.FakeSongDownloader
import com.simplecityapps.fakes.RecordingAnalytics
import com.simplecityapps.mediaprovider.server.QuickConnectPollState
import com.simplecityapps.mediaprovider.server.SavedServerLogin
import com.simplecityapps.mediaprovider.server.ServerLogin
import com.simplecityapps.shuttle.analytics.MonetisationAnalytics
import com.simplecityapps.shuttle.analytics.SignInFailureClassifier
import com.simplecityapps.shuttle.analytics.SignInFailureReason
import com.simplecityapps.shuttle.entitlement.ObserveServerStreamingNeedsPro
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ServerSignInViewModelTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val server = FakeServerAuthentication()
    private val quickConnect = FakeQuickConnectAuthentication()
    private val analytics = RecordingAnalytics()
    private val songDownloader = FakeSongDownloader()
    private val needsPro = MutableStateFlow(false)

    /** [address] is typed over the saved or default one, unless it's null. */
    private fun TestScope.viewModel(
        type: MediaProviderType = MediaProviderType.Jellyfin,
        address: String? = "http://server:8096",
    ): ServerSignInViewModel {
        val servers = mapOf(type to server)
        val quickConnects = mapOf(MediaProviderType.Jellyfin to quickConnect)
        val monetisation = MonetisationAnalytics(analytics)
        val classifyFailure = SignInFailureClassifier { SignInFailureReason.Other }
        return ServerSignInViewModel(
            type,
            ReadServerLogin(servers),
            SignInToServer(servers, monetisation, classifyFailure),
            ForgetServerLogin(servers),
            ObserveServerStreamingNeedsPro { needsPro },
            CheckQuickConnectAvailable(quickConnects),
            SignInWithQuickConnect(quickConnects, monetisation, classifyFailure),
            songDownloader,
        ).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            address?.let(viewModel::onAddressChange)
        }
    }

    private val ServerSignInViewModel.form get() = uiState.value.form

    private val ServerSignInViewModel.events get() = uiState.value.events.map { it.value }

    @Test
    fun `the form starts from the saved login and a saved password can't be revealed`() = runTest {
        server.saved = SavedServerLogin("http://server:8096", "sam", "secret")

        val form = viewModel().form

        form shouldBe ServerSignInForm(address = "http://server:8096", username = "sam", password = "secret", passwordRevealable = false)
    }

    @Test
    fun `with nothing saved - the address starts as http and the password can be revealed`() = runTest {
        viewModel(address = null).form shouldBe ServerSignInForm(address = "http://")
    }

    @Test
    fun `clearing a saved password lets it be revealed again`() = runTest {
        server.saved = SavedServerLogin(password = "secret")
        val viewModel = viewModel()

        viewModel.onPasswordChange("secre")
        viewModel.form.passwordRevealable shouldBe false

        viewModel.onPasswordChange("")
        viewModel.form.passwordRevealable shouldBe true
    }

    @Test
    fun `Jellyfin and Emby need an address and a username - not a password`() = runTest {
        val viewModel = viewModel(MediaProviderType.Emby)
        viewModel.onAddressChange("")

        viewModel.onAuthenticate()

        viewModel.form.missing shouldBe setOf(ServerSignInField.Address, ServerSignInField.Username)
        viewModel.uiState.value.step shouldBe ServerSignInStep.Form
        server.authenticated shouldBe emptyList()
    }

    @Test
    fun `Plex needs the password too`() = runTest {
        val viewModel = viewModel(MediaProviderType.Plex)
        viewModel.onUsernameChange("sam")

        viewModel.onAuthenticate()

        viewModel.form.missing shouldBe setOf(ServerSignInField.Password)
    }

    @Test
    fun `typing into a missing field clears its error`() = runTest {
        val viewModel = viewModel()
        viewModel.onAddressChange("")
        viewModel.onAuthenticate()

        viewModel.onAddressChange("h")

        viewModel.form.missing shouldBe setOf(ServerSignInField.Username)
    }

    @Test
    fun `turning off remember password forgets the saved login`() = runTest {
        val viewModel = viewModel()

        viewModel.onRememberPasswordChange(false)

        viewModel.form.rememberPassword shouldBe false
        server.forgotten shouldBe 1
    }

    @Test
    fun `a sign-in shows its progress - reports the connection - then finishes a second later`() = runTest {
        server.pending = CompletableDeferred()
        val viewModel = viewModel(MediaProviderType.Plex)
        viewModel.onAddressChange("http://plex:32400")
        viewModel.onUsernameChange("sam")
        viewModel.onPasswordChange("secret")
        viewModel.onAuthCodeChange("123456")

        viewModel.onAuthenticate()
        viewModel.uiState.value.step shouldBe ServerSignInStep.Authenticating

        server.pending?.complete(Unit)
        runCurrent()
        viewModel.uiState.value.step shouldBe ServerSignInStep.Connected
        server.authenticated shouldBe listOf(ServerLogin("http://plex:32400", "sam", "secret", "123456"))
        server.remembered shouldBe ServerLogin("http://plex:32400", "sam", "secret", "123456")
        analytics.names shouldBe listOf("server_connected")
        viewModel.events shouldBe listOf(ServerSignInEvent.Connected)

        advanceTimeBy(1_001)
        viewModel.events shouldBe listOf(ServerSignInEvent.Connected, ServerSignInEvent.Finished)

        viewModel.onEventHandled(viewModel.uiState.value.events.first().id)
        viewModel.events shouldBe listOf(ServerSignInEvent.Finished)
    }

    @Test
    fun `signing in to the saved server again keeps its downloads`() = runTest {
        server.saved = SavedServerLogin("http://server:8096", "Sam", "secret")
        val viewModel = viewModel()
        viewModel.onUsernameChange("sam")

        viewModel.onAuthenticate()

        viewModel.uiState.value.step shouldBe ServerSignInStep.Connected
        songDownloader.removedAll shouldBe emptyList()
    }

    @Test
    fun `signing in to a different address or as a different user removes the old server's downloads`() = runTest {
        server.saved = SavedServerLogin("http://server:8096", "sam", "secret")
        viewModel(address = "http://other:8096").onAuthenticate()
        songDownloader.removedAll shouldBe listOf(MediaProviderType.Jellyfin)

        server.saved = SavedServerLogin("http://server:8096", "sam", "secret")
        val asAnotherUser = viewModel()
        asAnotherUser.onUsernameChange("alex")
        asAnotherUser.onAuthenticate()
        songDownloader.removedAll shouldBe listOf(MediaProviderType.Jellyfin, MediaProviderType.Jellyfin)
    }

    @Test
    fun `with no saved username - signing in to the saved address keeps its downloads`() = runTest {
        server.saved = SavedServerLogin("http://server:8096")
        val viewModel = viewModel()
        viewModel.onUsernameChange("sam")

        viewModel.onAuthenticate()

        viewModel.uiState.value.step shouldBe ServerSignInStep.Connected
        songDownloader.removedAll shouldBe emptyList()
    }

    @Test
    fun `turning off remember password then signing in to the same server keeps its downloads`() = runTest {
        server.saved = SavedServerLogin("http://server:8096", "sam", "secret")
        val viewModel = viewModel()
        viewModel.onRememberPasswordChange(false)

        viewModel.onAuthenticate()

        viewModel.uiState.value.step shouldBe ServerSignInStep.Connected
        songDownloader.removedAll shouldBe emptyList()
    }

    @Test
    fun `a failed sign-in to a mistyped address - then the saved server - keeps its downloads`() = runTest {
        server.saved = SavedServerLogin("http://server:8096", "sam", "secret")
        val viewModel = viewModel(address = "http://sever:8096")
        server.failure = IllegalStateException("no")
        viewModel.onAuthenticate()
        viewModel.onRetry()

        server.failure = null
        viewModel.onAddressChange("http://server:8096")
        viewModel.onAuthenticate()

        viewModel.uiState.value.step shouldBe ServerSignInStep.Connected
        songDownloader.removedAll shouldBe emptyList()
    }

    @Test
    fun `a failed sign-in to another server - then a successful one to it - removes the old server's downloads`() = runTest {
        server.saved = SavedServerLogin("http://server:8096", "sam", "secret")
        val viewModel = viewModel(address = "http://other:8096")
        server.failure = IllegalStateException("no")
        viewModel.onAuthenticate()
        viewModel.onRetry()

        server.failure = null
        viewModel.onAuthenticate()

        viewModel.uiState.value.step shouldBe ServerSignInStep.Connected
        songDownloader.removedAll shouldBe listOf(MediaProviderType.Jellyfin)
    }

    @Test
    fun `the saved server typed with another case - scheme or trailing slash keeps its downloads`() = runTest {
        server.saved = SavedServerLogin("http://Server.local:8096/jellyfin/", "sam", "secret")

        viewModel(address = "https://server.LOCAL:8096/jellyfin").onAuthenticate()

        songDownloader.removedAll shouldBe emptyList()
    }

    @Test
    fun `addresses name the same server whatever the host's case - the scheme - a default port or trailing slashes`() {
        isSameServerAddress("http://Server:8096", "https://server:8096/") shouldBe true
        isSameServerAddress("server:8096", "http://server:8096") shouldBe true
        isSameServerAddress("http://server", "http://server:80") shouldBe true
        isSameServerAddress("https://server:443/", "https://SERVER") shouldBe true
        isSameServerAddress("http://[FE80::1]:8096", "http://[fe80::1]:8096") shouldBe true

        isSameServerAddress("http://server:8096", "http://server:8920") shouldBe false
        isSameServerAddress("http://server:8096", "http://other:8096") shouldBe false
        isSameServerAddress("http://server/jellyfin", "http://server/emby") shouldBe false
        isSameServerAddress("http://", "http://") shouldBe false
    }

    @Test
    fun `a failed sign-in to a different server keeps the downloads`() = runTest {
        server.saved = SavedServerLogin("http://server:8096", "sam", "secret")
        server.failure = IllegalStateException("no")

        viewModel(address = "http://other:8096").onAuthenticate()

        songDownloader.removedAll shouldBe emptyList()
    }

    @Test
    fun `Quick Connect to a different address removes the old server's downloads - to the same one keeps them`() = runTest {
        server.saved = SavedServerLogin("http://server:8096", "sam", "secret")
        quickConnect.pollState = QuickConnectPollState.Authenticated

        viewModel().onUseQuickConnect()
        advanceTimeBy(5_001)
        runCurrent()
        songDownloader.removedAll shouldBe emptyList()

        viewModel(address = "http://other:8096").onUseQuickConnect()
        advanceTimeBy(5_001)
        runCurrent()
        songDownloader.removedAll shouldBe listOf(MediaProviderType.Jellyfin)
    }

    @Test
    fun `Jellyfin and Emby send no two-factor code`() = runTest {
        val viewModel = viewModel()
        viewModel.onUsernameChange("sam")

        viewModel.onAuthenticate()
        runCurrent()

        server.authenticated shouldBe listOf(ServerLogin("http://server:8096", "sam", "", null))
    }

    @Test
    fun `a failed sign-in shows why - and Retry returns to the form as it was`() = runTest {
        server.failure = Exception("The server could not be reached.")
        val viewModel = viewModel()
        viewModel.onUsernameChange("sam")

        viewModel.onAuthenticate()
        runCurrent()
        viewModel.uiState.value.step shouldBe ServerSignInStep.Failed("The server could not be reached.")
        viewModel.events shouldBe emptyList()

        viewModel.onRetry()
        viewModel.uiState.value.step shouldBe ServerSignInStep.Form
        viewModel.form.username shouldBe "sam"
    }

    @Test
    fun `Authenticate does nothing while a sign-in is under way`() = runTest {
        server.pending = CompletableDeferred()
        val viewModel = viewModel()
        viewModel.onUsernameChange("sam")

        viewModel.onAuthenticate()
        viewModel.onAuthenticate()

        server.authenticated.size shouldBe 1
    }

    @Test
    fun `a Free user is shown the streaming disclosure`() = runTest {
        needsPro.value = true
        viewModel().uiState.value.showProDisclosure shouldBe true
    }

    @Test
    fun `a Trial or Pro user isn't shown the disclosure`() = runTest {
        needsPro.value = false
        viewModel().uiState.value.showProDisclosure shouldBe false
    }

    @Test
    fun `Quick Connect is unavailable for a type with no binding`() = runTest {
        val viewModel = viewModel(MediaProviderType.Plex)
        viewModel.uiState.value.quickConnectEnabled shouldBe false
    }

    @Test
    fun `the server's Quick Connect availability appears after a short debounce`() = runTest {
        quickConnect.enabled = true
        val viewModel = viewModel()

        viewModel.uiState.value.quickConnectEnabled shouldBe false

        advanceTimeBy(501)
        runCurrent()
        viewModel.uiState.value.quickConnectEnabled shouldBe true
    }

    @Test
    fun `Quick Connect shows the code - then connects and finishes as a sign-in does`() = runTest {
        quickConnect.pollState = QuickConnectPollState.Authenticated
        val viewModel = viewModel()

        viewModel.onUseQuickConnect()
        viewModel.uiState.value.step shouldBe ServerSignInStep.AwaitingCode("123456")

        advanceTimeBy(5_001)
        runCurrent()
        viewModel.uiState.value.step shouldBe ServerSignInStep.Connected
        analytics.names shouldBe listOf("server_connected")
        viewModel.events shouldBe listOf(ServerSignInEvent.Connected)

        advanceTimeBy(1_001)
        viewModel.events shouldBe listOf(ServerSignInEvent.Connected, ServerSignInEvent.Finished)
    }

    @Test
    fun `Quick Connect does nothing while the form isn't showing`() = runTest {
        val viewModel = viewModel()
        viewModel.onAuthenticate()

        viewModel.onUseQuickConnect()

        quickConnect.authenticated shouldBe emptyList()
    }

    @Test
    fun `a second tap while Quick Connect is initiating doesn't start a second attempt`() = runTest {
        quickConnect.initiatePending = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.onUseQuickConnect()
        viewModel.onUseQuickConnect()
        quickConnect.initiatePending?.complete(Unit)
        runCurrent()

        quickConnect.initiateCallCount shouldBe 1
        viewModel.uiState.value.step shouldBe ServerSignInStep.AwaitingCode("123456")
    }

    @Test
    fun `denial fails - and cancelling mid-poll returns to the form without authenticating`() = runTest {
        quickConnect.pending = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.onUseQuickConnect()
        viewModel.uiState.value.step shouldBe ServerSignInStep.AwaitingCode("123456")

        viewModel.onCancelQuickConnect()

        viewModel.uiState.value.step shouldBe ServerSignInStep.Form
        quickConnect.authenticated shouldBe emptyList()
    }

    @Test
    fun `cancelling Quick Connect stops the polling`() = runTest {
        val viewModel = viewModel()
        viewModel.onUseQuickConnect()
        advanceTimeBy(10_001)
        val polls = quickConnect.pollCount

        viewModel.onCancelQuickConnect()
        advanceTimeBy(60_000)

        quickConnect.pollCount shouldBe polls
    }

    @Test
    fun `leaving the sign-in while its code shows stops the polling and returns to the form`() = runTest {
        val viewModel = viewModel()
        viewModel.onUseQuickConnect()
        advanceTimeBy(10_001)
        val polls = quickConnect.pollCount

        viewModel.onLeave()
        advanceTimeBy(60_000)

        quickConnect.pollCount shouldBe polls
        viewModel.uiState.value.step shouldBe ServerSignInStep.Form
    }

    @Test
    fun `leaving a connected sign-in lets it finish`() = runTest {
        quickConnect.pollState = QuickConnectPollState.Authenticated
        val viewModel = viewModel()
        viewModel.onUseQuickConnect()
        advanceTimeBy(5_001)
        runCurrent()

        viewModel.onLeave()
        advanceTimeBy(1_001)

        viewModel.uiState.value.step shouldBe ServerSignInStep.Connected
        viewModel.events shouldBe listOf(ServerSignInEvent.Connected, ServerSignInEvent.Finished)
    }
}
