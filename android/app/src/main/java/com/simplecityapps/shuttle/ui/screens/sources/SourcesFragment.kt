package com.simplecityapps.shuttle.ui.screens.sources

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.ui.screens.settings.SourcesSettingsRoute
import com.simplecityapps.shuttle.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Settings > Sources in the legacy [com.simplecityapps.shuttle.ui.MainActivity], until the shell replaces it (#381). */
@AndroidEntryPoint
class SourcesFragment : Fragment() {
    @Inject
    lateinit var appearanceSettings: AppearanceSettings

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val theme by appearanceSettings.theme.flow.collectAsStateWithLifecycle(appearanceSettings.theme.value)
            val accent by appearanceSettings.accent.flow.collectAsStateWithLifecycle(appearanceSettings.accent.value)
            AppTheme(theme = theme, accent = accent) {
                SourcesSettingsRoute(onNavigateUp = { findNavController().popBackStack() })
            }
        }
    }
}
