package com.simplecityapps.shuttle.ui.screens.settings.scrobbling

import com.simplecityapps.shuttle.scrobbling.ListenBrainzAccountState
import com.simplecityapps.shuttle.scrobbling.ListenBrainzSignInResult
import com.simplecityapps.shuttle.scrobbling.ObserveListenBrainzAccount
import com.simplecityapps.shuttle.scrobbling.SignInToListenBrainz
import com.simplecityapps.shuttle.scrobbling.SignOutOfListenBrainz
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

class ListenBrainzViewModelTest {
    private val mainDispatcher = UnconfinedTestDispatcher()
    private val account = MutableStateFlow<ListenBrainzAccountState>(ListenBrainzAccountState.SignedOut)
    private var signInResult = ListenBrainzSignInResult.SignedIn
    private var signedInWith: String? = null
    private var signedOut = false

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ListenBrainzViewModel(
        observeAccount = ObserveListenBrainzAccount { account },
        signIn = SignInToListenBrainz { token ->
            signedInWith = token
            if (signInResult == ListenBrainzSignInResult.SignedIn) account.value = ListenBrainzAccountState.SignedIn("tim")
            signInResult
        },
        signOut = SignOutOfListenBrainz {
            signedOut = true
            account.value = ListenBrainzAccountState.SignedOut
        }
    )

    @Test
    fun `starts signed out with nothing to say`() = runTest(mainDispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.uiState.value shouldBe ListenBrainzUiState()
    }

    @Test
    fun `a good token shows the signed-in username`() = runTest(mainDispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onSignIn("abc")

        signedInWith shouldBe "abc"
        viewModel.uiState.value.account shouldBe ListenBrainzAccountState.SignedIn("tim")
        viewModel.uiState.value.message shouldBe null
        viewModel.uiState.value.busy shouldBe false
    }

    @Test
    fun `a token ListenBrainz refuses says so once`() = runTest(mainDispatcher) {
        signInResult = ListenBrainzSignInResult.InvalidToken
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onSignIn("abc")
        viewModel.uiState.value.message shouldBe ListenBrainzMessage.InvalidToken
        viewModel.uiState.value.account shouldBe ListenBrainzAccountState.SignedOut

        viewModel.onMessageShown()
        viewModel.uiState.value.message shouldBe null
    }

    @Test
    fun `not reaching ListenBrainz says so`() = runTest(mainDispatcher) {
        signInResult = ListenBrainzSignInResult.Failed
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onSignIn("abc")

        viewModel.uiState.value.message shouldBe ListenBrainzMessage.Failed
    }

    @Test
    fun `signing out returns to signed out`() = runTest(mainDispatcher) {
        account.value = ListenBrainzAccountState.SignedIn("tim")
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onSignOut()

        signedOut shouldBe true
        viewModel.uiState.value.account shouldBe ListenBrainzAccountState.SignedOut
        viewModel.uiState.value.busy shouldBe false
    }
}
