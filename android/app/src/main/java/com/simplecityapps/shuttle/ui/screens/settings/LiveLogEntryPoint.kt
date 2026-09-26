package com.simplecityapps.shuttle.ui.screens.settings

import androidx.compose.runtime.Composable

/**
 * Renders the debug-only Live log screen. Bound with `@BindsOptionalOf`: only the debug build ships an
 * implementation (`android/app/src/debug`), so release resolves an empty `Optional` and the Settings row that
 * would open it is never shown (see [com.simplecityapps.shuttle.ui.screens.settings.model.SettingsCatalog]).
 */
interface LiveLogEntryPoint {
    @Composable
    fun Content(onNavigateUp: () -> Unit)
}
