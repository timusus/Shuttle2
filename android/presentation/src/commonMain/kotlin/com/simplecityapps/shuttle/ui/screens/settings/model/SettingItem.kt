package com.simplecityapps.shuttle.ui.screens.settings.model

import com.simplecityapps.shuttle.settings.Setting
import com.simplecityapps.shuttle.ui.text.StringKey

/** One of the top-level settings screens. [legacyKeys] are the old preference screens whose rows moved here. */
enum class SettingsDestination(
    val title: StringKey,
    val legacyKeys: List<String>
) {
    Appearance(StringKey.PREF_CATEGORY_TITLE_DISPLAY, listOf("pref_screen_display", "pref_screen_widget")),
    PlaybackAndSound(StringKey.SETTINGS_DESTINATION_PLAYBACK_AND_SOUND, listOf("pref_screen_playback")),
    Sources(StringKey.SETTINGS_DESTINATION_SOURCES, emptyList()),
    Library(StringKey.SETTINGS_DESTINATION_LIBRARY, listOf("pref_screen_media", "pref_screen_artwork")),
    Privacy(StringKey.PREF_CATEGORY_TITLE_PRIVACY, listOf("pref_screen_privacy")),
    About(StringKey.SETTINGS_DESTINATION_ABOUT, listOf("pref_screen_app_info", "pref_screen_debug"))
}

data class SettingsScreen(
    val destination: SettingsDestination,
    val groups: List<SettingsGroup>
) {
    val items: List<SettingItem> get() = groups.flatMap { it.items }
}

/** A run of rows, under a header when [title] is set. */
data class SettingsGroup(
    val title: StringKey?,
    val items: List<SettingItem>
)

/** Where a [SettingItem.Navigate] row goes. The UI step maps each to a route. */
enum class SettingsLink {
    Equalizer,
    ExcludedSongs,
    WhatsNew,
    Licences,
    LiveLog
}

/** What a [SettingItem.Action] row does. The UI step maps each to a handler. */
enum class SettingsAction {
    Rescan,
    ExportBackup,
    ImportBackup,
    ClearArtworkCache,
    DownloadAllArtwork,
    CopyDebugLogs
}

/** A confirmation dialog shown before a destructive or costly action runs. */
data class Confirmation(
    val title: StringKey,
    val message: StringKey,
    val confirm: StringKey
)

/** A switch that takes a row over while it's on: the row is disabled and shows [hint] in place of its value. */
data class SettingOverride(
    val setting: Setting<Boolean>,
    val hint: StringKey
)

data class ChoiceOption<T>(
    val value: T,
    val label: StringKey
)

/**
 * One row. Rows that store a value carry their [Setting], so key and default have one source; the rest carry
 * the key of the legacy preference they replace, if any.
 */
sealed interface SettingItem {
    val title: StringKey

    val summary: StringKey?

    /** Hidden below this API level. */
    val minSdk: Int

    /** Disabled while this switch is off. */
    val dependsOn: Setting<Boolean>?

    /** The preference key this row stores or replaces, if it has one. */
    val key: String?

    data class Switch(
        val setting: Setting<Boolean>,
        override val title: StringKey,
        override val summary: StringKey? = null,
        override val minSdk: Int = 1,
        override val dependsOn: Setting<Boolean>? = null
    ) : SettingItem {
        override val key: String get() = setting.key
    }

    data class Choice<T>(
        val setting: Setting<T>,
        override val title: StringKey,
        val options: List<ChoiceOption<T>>,
        override val summary: StringKey? = null,
        override val minSdk: Int = 1,
        override val dependsOn: Setting<Boolean>? = null,
        /** Only while its switch's row is shown: a switch hidden below its API level takes nothing over. */
        val overriddenBy: SettingOverride? = null
    ) : SettingItem {
        override val key: String get() = setting.key
    }

    /** [fromFloat] converts the slider position back to the stored type; [steps] as in Compose's Slider. */
    data class Slider<T : Number>(
        val setting: Setting<T>,
        override val title: StringKey,
        val range: ClosedFloatingPointRange<Float>,
        val steps: Int,
        val fromFloat: (Float) -> T,
        override val summary: StringKey? = null,
        override val minSdk: Int = 1,
        override val dependsOn: Setting<Boolean>? = null
    ) : SettingItem {
        override val key: String get() = setting.key
    }

    data class Navigate(
        val target: SettingsLink,
        override val title: StringKey,
        override val summary: StringKey? = null,
        override val key: String? = null,
        override val minSdk: Int = 1,
        override val dependsOn: Setting<Boolean>? = null
    ) : SettingItem

    data class Action(
        val action: SettingsAction,
        override val title: StringKey,
        override val summary: StringKey? = null,
        val confirmation: Confirmation? = null,
        override val key: String? = null,
        override val minSdk: Int = 1,
        override val dependsOn: Setting<Boolean>? = null
    ) : SettingItem
}
