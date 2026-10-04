package com.simplecityapps.shuttle.ui.screens.settings.scrobbling

import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.scrobbling.FinishLastFmSignIn
import com.simplecityapps.shuttle.scrobbling.LastFmAccountState
import com.simplecityapps.shuttle.scrobbling.LastFmSignInResult
import com.simplecityapps.shuttle.scrobbling.ObserveLastFmAccount
import com.simplecityapps.shuttle.scrobbling.SignOutOfLastFm
import com.simplecityapps.shuttle.scrobbling.StartLastFmSignIn
import com.simplecityapps.shuttle.settings.ScrobblingSettings
import com.simplecityapps.shuttle.settings.SettingsStore
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

class ScrobblingViewModelTest {
    private val mainDispatcher = UnconfinedTestDispatcher()
    private val account = MutableStateFlow<LastFmAccountState>(LastFmAccountState.SignedOut)
    private var approvalUrl: String? = "https://last.fm/approve"
    private var finishResult = LastFmSignInResult.SignedIn
    private var signedOut = false
    private val settings = ScrobblingSettings(SettingsStore(InMemoryKeyValueStore()))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ScrobblingViewModel(
        observeAccount = ObserveLastFmAccount { account },
        startSignIn = StartLastFmSignIn { approvalUrl },
        finishSignIn = FinishLastFmSignIn { finishResult },
        signOut = SignOutOfLastFm { signedOut = true },
        settings = settings
    )

    @Test
    fun `signing in hands the approval page to the screen once`() = runTest(mainDispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onSignIn()
        viewModel.uiState.value.approvalUrl shouldBe "https://last.fm/approve"

        viewModel.onApprovalUrlOpened()
        viewModel.uiState.value.approvalUrl shouldBe null
    }

    @Test
    fun `a sign-in Last-fm couldn't start says so`() = runTest(mainDispatcher) {
        approvalUrl = null
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onSignIn()

        viewModel.uiState.value.message shouldBe ScrobblingMessage.Failed
        viewModel.onMessageShown()
        viewModel.uiState.value.message shouldBe null
    }

    @Test
    fun `finishing a sign-in that isn't approved yet says so`() = runTest(mainDispatcher) {
        finishResult = LastFmSignInResult.NotApproved
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onFinishSignIn()

        viewModel.uiState.value.message shouldBe ScrobblingMessage.NotApproved
    }

    @Test
    fun `signing out and the server-streams switch reach their owners`() = runTest(mainDispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onSignOut()
        viewModel.onServerStreamsChange(true)

        signedOut shouldBe true
        settings.scrobbleServerStreams.value shouldBe true
        viewModel.uiState.value.scrobbleServerStreams shouldBe true
    }

    @Test
    fun `the account state comes through`() = runTest(mainDispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        account.value = LastFmAccountState.SignedIn("tim")

        viewModel.uiState.value.account shouldBe LastFmAccountState.SignedIn("tim")
    }
}
