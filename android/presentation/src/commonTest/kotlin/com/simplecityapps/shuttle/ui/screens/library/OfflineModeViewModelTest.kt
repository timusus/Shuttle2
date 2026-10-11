package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.fakes.FakeOfflineSongs
import com.simplecityapps.shuttle.ui.actions.ObserveOfflineMode
import com.simplecityapps.shuttle.ui.actions.SetOfflineMode
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineModeViewModelTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val offline = FakeOfflineSongs()

    @Test
    fun `toggling flips Offline mode on and back off`() = runTest {
        val viewModel = OfflineModeViewModel(ObserveOfflineMode(offline.modeSource), SetOfflineMode(offline.modeSource))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        viewModel.uiState.value shouldBe false

        viewModel.toggle()
        advanceUntilIdle()
        viewModel.uiState.value shouldBe true
        offline.offlineMode.value shouldBe true

        viewModel.toggle()
        advanceUntilIdle()
        viewModel.uiState.value shouldBe false
    }

    @Test
    fun `reflects the mode being changed elsewhere`() = runTest {
        val viewModel = OfflineModeViewModel(ObserveOfflineMode(offline.modeSource), SetOfflineMode(offline.modeSource))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        offline.offlineMode.value = true
        advanceUntilIdle()

        viewModel.uiState.value shouldBe true
    }
}
