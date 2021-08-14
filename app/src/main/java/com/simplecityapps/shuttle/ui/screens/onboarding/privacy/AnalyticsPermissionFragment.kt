package com.simplecityapps.shuttle.ui.screens.onboarding.privacy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.remote_config.AnalyticsManager
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingChild
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingPage
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingParent
import javax.inject.Inject

class AnalyticsPermissionFragment : Fragment(), OnboardingChild {

    @Inject
    lateinit var preferenceManager: GeneralPreferenceManager

    @Inject
    lateinit var analyticsManager: AnalyticsManager


    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_onboarding_privacy, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bugsnagSwitch: SwitchCompat = view.findViewById(R.id.crashReportingSwitch)
        bugsnagSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            preferenceManager.crashReportingEnabled = isChecked
        }
        val firebaseAnalyticsSwitch: SwitchCompat = view.findViewById(R.id.analyticsSwitch)
        firebaseAnalyticsSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            preferenceManager.firebaseAnalyticsEnabled = isChecked
            analyticsManager.enableAnalytics(isChecked)
        }
    }


    // OnboardingChild Implementation

    override val page = OnboardingPage.StoragePermission

    override fun getParent() = parentFragment as OnboardingParent

    override fun handleNextButtonClick() {
        getParent().goToNext()
    }
}