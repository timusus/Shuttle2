package com.simplecityapps.shuttle.shared.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelClearingTest {
    private class ScreenViewModel : ViewModel() {
        var cleared = false

        var closed = false

        init {
            addCloseable(AutoCloseable { closed = true })
        }

        override fun onCleared() {
            cleared = true
        }
    }

    @Test
    fun `clearing runs onCleared - closes its closeables and cancels its scope`() {
        Dispatchers.setMain(StandardTestDispatcher())
        try {
            val viewModel = ScreenViewModel()
            val job = viewModel.viewModelScope.coroutineContext[Job]!!

            viewModel.clearFromSwift()

            viewModel.cleared shouldBe true
            viewModel.closed shouldBe true
            job.isCancelled shouldBe true
        } finally {
            Dispatchers.resetMain()
        }
    }
}
