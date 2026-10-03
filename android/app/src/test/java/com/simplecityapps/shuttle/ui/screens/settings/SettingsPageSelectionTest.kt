package com.simplecityapps.shuttle.ui.screens.settings

import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsDestination
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsLink
import com.simplecityapps.shuttle.ui.shell.HomeRoute
import com.simplecityapps.shuttle.ui.shell.SettingsRoute
import io.kotest.matchers.shouldBe
import org.junit.Test

class SettingsPageSelectionTest {

    @Test
    fun `with nothing above the list, the stand-in page is the one beside it`() {
        settingsPageBesideList(listOf(HomeRoute, SettingsRoute)) shouldBe SettingsPlaceholderPage
    }

    @Test
    fun `the page directly above the list is the one beside it, whatever was opened from it`() {
        val sources = SettingsDestinationRoute(SettingsDestination.Sources)
        settingsPageBesideList(listOf(HomeRoute, SettingsRoute, sources)) shouldBe SettingsDestination.Sources
        settingsPageBesideList(listOf(HomeRoute, SettingsRoute, sources, FolderRulesRoute)) shouldBe SettingsDestination.Sources
    }

    @Test
    fun `a page under a page, directly above the list, stands for the page it belongs to`() {
        settingsPageBesideList(listOf(HomeRoute, SettingsRoute, FolderRulesRoute)) shouldBe SettingsDestination.Sources
        settingsPageBesideList(listOf(HomeRoute, SettingsRoute, EqualizerRoute)) shouldBe SettingsDestination.PlaybackAndSound
        settingsPageBesideList(listOf(HomeRoute, SettingsRoute, ExcludedSongsRoute)) shouldBe SettingsDestination.Library
        settingsPageBesideList(listOf(HomeRoute, SettingsRoute, WhatsNewRoute)) shouldBe SettingsDestination.About
        settingsPageBesideList(listOf(HomeRoute, SettingsRoute, LicencesRoute)) shouldBe SettingsDestination.About
    }

    @Test
    fun `every link's page is a Settings page`() {
        SettingsLink.entries.forEach { link ->
            (settingsPageBesideList(listOf(HomeRoute, SettingsRoute, link.route)) != null) shouldBe true
        }
    }

    @Test
    fun `without the list on the stack, nothing is beside it`() {
        settingsPageBesideList(listOf(HomeRoute, SettingsDestinationRoute(SettingsDestination.Sources))) shouldBe null
    }
}
