package com.simplecityapps.shuttle.ui.screens.settings

import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.ui.screens.settings.model.AndroidSettingsCatalog
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingItem
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsCatalog
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsDestination
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsLink
import com.simplecityapps.shuttle.ui.shell.SettingsRoute

/** The page beside the Settings list until one is opened, as list-detail layouts show their first item. */
val SettingsPlaceholderPage: SettingsDestination = SettingsDestination.entries.first()

/** The route a settings page's link opens. */
val SettingsLink.route: NavKey
    get() = when (this) {
        SettingsLink.Equalizer -> EqualizerRoute
        SettingsLink.ExcludedSongs -> ExcludedSongsRoute
        SettingsLink.WhatsNew -> WhatsNewRoute
        SettingsLink.Licences -> LicencesRoute
        SettingsLink.LiveLog -> LiveLogRoute
        SettingsLink.Scrobbling -> ScrobblingRoute
    }

/**
 * The page the Settings list has open beside it: the entry directly above the top-most [SettingsRoute] on [stack], with
 * a page opened from a page (Folder rules, the equalizer, ...) standing for the page it belongs to, or
 * [SettingsPlaceholderPage] while nothing is above the list. Null without a Settings list on the stack, or when the
 * entry above it belongs to no page.
 */
internal fun settingsPageBesideList(
    stack: List<NavKey>,
    catalog: SettingsCatalog = AndroidSettingsCatalog
): SettingsDestination? {
    val list = stack.lastIndexOf(SettingsRoute)
    if (list < 0) return null
    return when (val above = stack.getOrNull(list + 1)) {
        null -> SettingsPlaceholderPage
        is SettingsDestinationRoute -> above.destination
        FolderRulesRoute -> SettingsDestination.Sources
        else -> SettingsLink.entries.firstOrNull { it.route == above }?.let { link -> catalog.pageLinkingTo(link) }
    }
}

private fun SettingsCatalog.pageLinkingTo(link: SettingsLink): SettingsDestination? = screens.firstOrNull { screen -> screen.items.any { it is SettingItem.Navigate && it.target == link } }?.destination
