package com.simplecityapps.shuttle.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.settings.AnalyticsConsentSettings
import com.simplecityapps.shuttle.settings.PrivacySettings
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AnalyticsConsentUiState(
    val showCard: Boolean = false,
)

/**
 * The Home analytics consent card (#421): shown once a library is loaded and the app has been opened on
 * [DaysBeforeAsking] separate days. Either answer, or dismissing the card, marks it asked so it never shows again;
 * analytics stays off until the user chooses [onShare]. The choice is [PrivacySettings.analytics], which
 * TelemetryConsentGate applies to PostHog as soon as it changes.
 */
@HiltViewModel
class AnalyticsConsentViewModel @Inject constructor(
    observeSongs: ObserveSongs,
    private val settings: AnalyticsConsentSettings,
    private val privacySettings: PrivacySettings,
    clock: Clock,
) : ViewModel() {
    init {
        val today = LocalDate.now(clock).toEpochDay().toInt()
        if (!settings.asked.value && settings.lastCountedEpochDay.value != today) {
            settings.daysOpened.value += 1
            settings.lastCountedEpochDay.value = today
        }
    }

    val uiState: StateFlow<AnalyticsConsentUiState> =
        combine(
            observeSongs().map { songs -> songs.isNotEmpty() }.distinctUntilChanged(),
            settings.asked.flow,
            settings.daysOpened.flow,
        ) { hasSongs, asked, daysOpened -> AnalyticsConsentUiState(showCard = hasSongs && !asked && daysOpened >= DaysBeforeAsking) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsConsentUiState())

    /** The user chose Share: turns analytics on, starting PostHog, and marks the card answered. */
    fun onShare() {
        privacySettings.analytics.value = true
        settings.asked.value = true
    }

    /** The user chose No thanks, or dismissed the card: marks it answered, leaving analytics off. */
    fun onNoThanks() {
        settings.asked.value = true
    }

    companion object {
        private const val DaysBeforeAsking = 3
    }
}
