package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.fakes.FakeServerAuthentication
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerSignInViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val server = FakeServerAuthentication()
    private val connected = mutableListOf<MediaProviderType>()

    private fun TestScope.viewModel(type: MediaProviderType = MediaProviderType.Jellyfin): ServerSignInViewModel {
        val servers = mapOf(type to server)
        return ServerSignInViewModel(
            type,
            ReadServerLogin(servers),
            SignInToServer(servers, ServerTrial { connected += it }),
            ForgetServerLogin(servers),
        ).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        }
    }

    private val ServerSignInViewModel.form get() = uiState.value.form

    private val ServerSignInViewModel.events get() = uiState.value.events.map { it.value }

    @Test
    fun `the form starts from the saved login, and a saved password can't be revealed`() = runTest {
        server.saved = SavedServerLogin("http://server:8096", "sam", "secret")

        val form = viewModel().form

        form shouldBe ServerSignInForm(address = "http://server:8096", username = "sam", password = "secret", passwordRevealable = false)
    }

    @Test
    fun `with nothing saved, the address starts as http and the password can be revealed`() = runTest {
        viewModel().form shouldBe ServerSignInForm(address = "http://")
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
    fun `Jellyfin and Emby need an address and a username, not a password`() = runTest {
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
    fun `a sign-in shows its progress, reports the connection, then finishes a second later`() = runTest {
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
        connected shouldBe listOf(MediaProviderType.Plex)
        viewModel.events shouldBe listOf(ServerSignInEvent.Connected)

        advanceTimeBy(1_001)
        viewModel.events shouldBe listOf(ServerSignInEvent.Connected, ServerSignInEvent.Finished)

        viewModel.onEventHandled(viewModel.uiState.value.events.first().id)
        viewModel.events shouldBe listOf(ServerSignInEvent.Finished)
    }

    @Test
    fun `Jellyfin and Emby send no two-factor code`() = runTest {
        val viewModel = viewModel()
        viewModel.onUsernameChange("sam")

        viewModel.onAuthenticate()
        runCurrent()

        server.authenticated shouldBe listOf(ServerLogin("http://", "sam", "", null))
    }

    @Test
    fun `a failed sign-in shows why, and Retry returns to the form as it was`() = runTest {
        server.failure = IllegalStateException("boom")
        val viewModel = viewModel()
        viewModel.onUsernameChange("sam")

        viewModel.onAuthenticate()
        runCurrent()
        viewModel.uiState.value.step shouldBe ServerSignInStep.Failed("An unknown error occurred.")
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
}
