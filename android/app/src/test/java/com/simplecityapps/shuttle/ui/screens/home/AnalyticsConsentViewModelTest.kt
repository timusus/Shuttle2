package com.simplecityapps.shuttle.ui.screens.home

import android.content.Context
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.shuttle.settings.AnalyticsConsentSettings
import com.simplecityapps.shuttle.settings.PrivacySettings
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.settings.defaultSharedPreferences
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AnalyticsConsentViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var settings: AnalyticsConsentSettings
    private lateinit var privacySettings: PrivacySettings
    private val songs = FakeSongRepository()

    @Before
    fun setUp() {
        val store = SettingsStore(context.defaultSharedPreferences().apply { edit().clear().commit() })
        settings = AnalyticsConsentSettings(store)
        privacySettings = PrivacySettings(store)
        songs.setSongs(listOf(createSong(name = "Chlorophyll Loop")))
    }

    private fun TestScope.viewModel(day: Int): AnalyticsConsentViewModel {
        val clock = Clock.fixed(Instant.EPOCH.plusSeconds(day * SecondsPerDay), ZoneOffset.UTC)
        return AnalyticsConsentViewModel(ObserveSongs(songs), settings, privacySettings, clock).also { viewModel ->
            backgroundScope.launch { viewModel.showCard.collect {} }
            runCurrent()
        }
    }

    @Test
    fun `the card stays hidden before the third day`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel(day = 1).showCard.value shouldBe false
        viewModel(day = 2).showCard.value shouldBe false
        settings.daysOpened.value shouldBe 2
    }

    @Test
    fun `the card shows once opened on a third separate day`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel(day = 1)
        viewModel(day = 2)

        viewModel(day = 3).showCard.value shouldBe true
        settings.daysOpened.value shouldBe 3
    }

    @Test
    fun `reopening on the same day does not count twice`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel(day = 1)
        viewModel(day = 1)
        viewModel(day = 1)

        settings.daysOpened.value shouldBe 1
    }

    @Test
    fun `an answered card stops counting days`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel(day = 1).onNoThanks()

        viewModel(day = 2)
        viewModel(day = 3)

        settings.daysOpened.value shouldBe 1
    }

    @Test
    fun `the card never shows once answered, even after enough days`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel(day = 1)
        viewModel(day = 2)
        val third = viewModel(day = 3)
        third.onNoThanks()
        runCurrent()

        viewModel(day = 4).showCard.value shouldBe false
    }

    @Test
    fun `sharing turns analytics on and marks the card answered`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel(day = 1).onShare()

        privacySettings.analytics.value shouldBe true
        settings.asked.value shouldBe true
    }

    @Test
    fun `declining or dismissing leaves analytics off but marks the card answered`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel(day = 1).onNoThanks()

        privacySettings.analytics.value shouldBe false
        settings.asked.value shouldBe true
    }

    @Test
    fun `the card stays hidden without a library`() = runTest(mainDispatcherRule.testDispatcher) {
        songs.setSongs(emptyList())
        viewModel(day = 1)
        viewModel(day = 2)

        viewModel(day = 3).showCard.value shouldBe false
    }

    companion object {
        private const val SecondsPerDay = 86_400L
    }
}
