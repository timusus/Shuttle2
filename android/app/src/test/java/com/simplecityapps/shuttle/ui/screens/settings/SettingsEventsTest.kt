package com.simplecityapps.shuttle.ui.screens.settings

import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.ui.screens.settings.SettingsDestinationViewState.Row
import com.simplecityapps.shuttle.ui.screens.settings.model.AndroidSettingsCatalog
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingItem
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsAction
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsDestination
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsLink
import com.simplecityapps.shuttle.ui.text.SettingsString
import io.kotest.matchers.shouldBe
import org.junit.Test

class SettingsEventsTest {
    private var navigatedUp = false
    private var openedPro = false
    private val openedDestinations = mutableListOf<SettingsDestination>()
    private val switchChanges = mutableListOf<Pair<String, Boolean>>()
    private val actions = mutableListOf<SettingsAction>()
    private val openedLinks = mutableListOf<SettingsLink>()

    private val callbacks = SettingsCallbacks(
        onNavigateUp = { navigatedUp = true },
        onOpenPro = { openedPro = true },
        onOpenDestination = { openedDestinations += it },
        onSwitchChange = { item, checked -> switchChanges += item.key to checked },
        onAction = { actions += it },
        onOpenLink = { openedLinks += it }
    )

    private fun rows(
        destination: SettingsDestination,
        uiState: SettingsUiState = SettingsUiState(),
        sdkInt: Int = 34
    ) = settingsDestinationViewState(AndroidSettingsCatalog.screen(destination), uiState, sdkInt, null).groups.flatMap { it.rows }

    private fun navigate(destination: SettingsDestination, title: SettingsString) = rows(destination).filterIsInstance<Row.Navigate>().first { it.item.title == title }

    @Test
    fun `the Pro row opens Shuttle Music Pro`() {
        callbacks.onEvent(SettingsEvent.OpenPro)

        openedPro shouldBe true
    }

    @Test
    fun `back leaves settings`() {
        callbacks.onEvent(SettingsEvent.NavigateUp)

        navigatedUp shouldBe true
    }

    @Test
    fun `a root row opens its destination`() {
        val playback = settingsRootViewState(SettingsUiState(), SettingsProState.Upsell).rows.first { it.destination == SettingsDestination.PlaybackAndSound }

        callbacks.onEvent(playback.tapEvent())

        openedDestinations shouldBe listOf(SettingsDestination.PlaybackAndSound)
    }

    @Test
    fun `tapping a switch asks for the opposite value`() {
        val pureBlack = rows(SettingsDestination.Appearance, SettingsScenarios.darkPureBlack).filterIsInstance<Row.Switch>().first { it.item.key == AppearanceSettings.PureBlack.key }

        callbacks.onEvent(pureBlack.tapEvent())

        switchChanges shouldBe listOf(AppearanceSettings.PureBlack.key to false)
    }

    @Test
    fun `USB DAC direct output toggles on`() {
        val usbDac = rows(SettingsDestination.PlaybackAndSound, sdkInt = 34).filterIsInstance<Row.Switch>().first { it.item.key == PlaybackSettings.UsbDacDirectOutput.key }

        callbacks.onEvent(usbDac.tapEvent())

        switchChanges shouldBe listOf(PlaybackSettings.UsbDacDirectOutput.key to true)
    }

    @Test
    fun `about opens the changelog`() {
        callbacks.onEvent(navigate(SettingsDestination.About, SettingsString.PREF_VIEW_CHANGELOG_TITLE).tapEvent())

        openedLinks shouldBe listOf(SettingsLink.WhatsNew)
    }

    @Test
    fun `about opens the licences`() {
        callbacks.onEvent(navigate(SettingsDestination.About, SettingsString.PREF_VIEW_LICENSES_TITLE).tapEvent())

        openedLinks shouldBe listOf(SettingsLink.Licences)
    }

    @Test
    fun `playback opens the equalizer`() {
        callbacks.onEvent(navigate(SettingsDestination.PlaybackAndSound, SettingsString.DSP_EQUALIZER_TITLE).tapEvent())

        openedLinks shouldBe listOf(SettingsLink.Equalizer)
    }

    @Test
    fun `sharing logs runs as soon as it is tapped`() {
        val share = rows(SettingsDestination.About, SettingsScenarios.fileLoggingOn).filterIsInstance<Row.Action>().first { it.item.title == SettingsString.PREF_SHARE_DEBUG_LOGS_TITLE }

        onActionEvent(null, ActionEvent.Tap(share.item)).event()?.let(callbacks::onEvent)

        actions shouldBe listOf(SettingsAction.ShareDebugLogs)
    }

    @Test
    fun `a confirmed action runs only on Confirm`() {
        val clear = rows(SettingsDestination.Library).filterIsInstance<Row.Action>().first { it.item.action == SettingsAction.ClearArtworkCache }

        val tap = onActionEvent(null, ActionEvent.Tap(clear.item))
        tap.event()?.let(callbacks::onEvent)
        actions shouldBe emptyList()
        onActionEvent(tap.pending, ActionEvent.Confirm).event()?.let(callbacks::onEvent)

        actions shouldBe listOf(SettingsAction.ClearArtworkCache)
    }

    @Test
    fun `a picked choice reports its index`() {
        val picks = mutableListOf<Pair<String, Int>>()
        val theme = AndroidSettingsCatalog.screen(SettingsDestination.Appearance).items.filterIsInstance<SettingItem.Choice<*>>()
            .first { it.key == AppearanceSettings.Theme.key }
        val choices = SettingsCallbacks(onChoiceSelect = { item, index -> picks += item.key to index })

        onChoiceDialogEvent(theme.key, ChoiceDialogEvent.Select(1)).event(theme)?.let(choices::onEvent)

        picks shouldBe listOf(AppearanceSettings.Theme.key to 1)
    }
}
