package com.simplecityapps.shuttle.ui.screens.onboarding.privacy

import android.R.attr.text
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.core.view.postDelayed
import androidx.fragment.app.Fragment
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingChild
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingPage
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingParent
import com.simplecityapps.shuttle.ui.snapshot.Snapshot
import com.simplecityapps.shuttle.ui.theme.AppTheme
import com.simplecityapps.shuttle.ui.theme.ColorSchemePreviewParameterProvider

class AnalyticsPermissionScreenFragment :
    Fragment(),
    OnboardingChild {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val onboardingParent = parentFragment as OnboardingParent
        return ComposeView(requireContext()).apply {
            postDelayed(50) {
                onboardingParent.hideBackButton()
                onboardingParent.toggleNextButton(true)
                onboardingParent.showNextButton(getString(R.string.onboarding_button_next))
            }
            setContent {
                AppTheme {
                    AnalyticsPermissionScreen()
                }
            }
        }
    }

    override val page = OnboardingPage.AnalyticsPermission

    override fun getParent() = parentFragment as? OnboardingParent

    override fun handleNextButtonClick() {
        getParent()?.goToNext()
    }
}

@Composable
private fun AnalyticsPermissionScreen(
    modifier: Modifier = Modifier,
    viewModel: AnalyticsPermissionViewModel = hiltViewModel()
) {
    val analyticsChecked by viewModel.analyticsEnabled.collectAsStateWithLifecycle()
    val crashlyticsChecked by viewModel.crashlyticsEnabled.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.onHasSeenOnboardingAnalyticsPermission()
    }
    AnalyticsPermissionScreen(
        modifier = modifier,
        analyticsChecked = analyticsChecked,
        crashlyticsChecked = crashlyticsChecked,
        onAnalyticsCheckedChange = viewModel::onAnalyticsCheckedChange,
        onCrashlyticsCheckedChange = viewModel::onCrashlyticsCheckedChange
    )
}

@Composable
private fun AnalyticsPermissionScreen(
    analyticsChecked: Boolean,
    crashlyticsChecked: Boolean,
    onAnalyticsCheckedChange: (Boolean) -> Unit,
    onCrashlyticsCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            contentDescription = null,
            modifier = Modifier
                .size(196.dp)
                .padding(top = 40.dp),
            tint = MaterialTheme.colorScheme.primary,
            painter = painterResource(R.drawable.ic_outline_analytics_24)
        )
        Spacer(modifier = Modifier.weight(1f))

        Text(
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            text = stringResource(R.string.onboarding_data_collection_title)
        )

        ListItem(
            headlineContent = {
                Text(text = stringResource(R.string.pref_crash_reporting_title))
            },
            supportingContent = {
                Text(text = stringResource(R.string.pref_crash_reporting_subtitle))
            },
            trailingContent = {
                Switch(
                    checked = crashlyticsChecked,
                    onCheckedChange = onCrashlyticsCheckedChange
                )
            }
        )

        ListItem(
            headlineContent = {
                Text(text = stringResource(R.string.pref_firebase_analytics_title))
            },
            supportingContent = {
                Text(text = stringResource(R.string.pref_firebase_analytics_subtitle))
            },
            trailingContent = {
                Switch(
                    checked = analyticsChecked,
                    onCheckedChange = onAnalyticsCheckedChange
                )
            }
        )
    }
}

@Snapshot
@Preview
@Composable
private fun Preview(@PreviewParameter(ColorSchemePreviewParameterProvider::class) colorScheme: ColorScheme) {
    MaterialTheme(colorScheme = colorScheme) {
        AnalyticsPermissionScreen(
            analyticsChecked = false,
            crashlyticsChecked = true,
            onAnalyticsCheckedChange = {},
            onCrashlyticsCheckedChange = {}
        )
    }
}
