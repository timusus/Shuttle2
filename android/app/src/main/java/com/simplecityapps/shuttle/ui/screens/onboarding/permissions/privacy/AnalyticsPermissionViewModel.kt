package com.simplecityapps.shuttle.ui.screens.onboarding.permissions.privacy

import androidx.lifecycle.ViewModel
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.remote_config.AnalyticsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class AnalyticsPermissionViewModel @Inject constructor(
    private val analyticsManager: AnalyticsManager,
    private val preferenceManager: GeneralPreferenceManager
) : ViewModel() {

    private val _crashlyticsEnabled = MutableStateFlow(false)
    val crashlyticsEnabled = _crashlyticsEnabled

    private val _analyticsEnabled = MutableStateFlow(false)
    val analyticsEnabled = _analyticsEnabled

    fun onHasSeenOnboardingAnalyticsPermission() {
        preferenceManager.hasSeenOnboardingAnalyticsDialog = true
    }

    fun onCrashlyticsCheckedChange(checked: Boolean) {
        preferenceManager.crashReportingEnabled = checked
        _crashlyticsEnabled.update { preferenceManager.crashReportingEnabled }
    }

    fun onAnalyticsCheckedChange(checked: Boolean) {
        analyticsManager.enableAnalytics(enabled = checked)
        preferenceManager.firebaseAnalyticsEnabled = checked
        _analyticsEnabled.update { preferenceManager.firebaseAnalyticsEnabled }
    }
}
