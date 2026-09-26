package com.simplecityapps.shuttle.ui.screens.settings.model

import androidx.annotation.StringRes
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.settings.Setting

/** One of the top-level settings screens. [legacyKeys] are the old preference screens whose rows moved here. */
enum class SettingsDestination(
    @StringRes val title: Int,
    val legacyKeys: List<String>
) {
    Appearance(R.string.pref_category_title_display, listOf("pref_screen_display", "pref_screen_widget")),
    PlaybackAndSound(R.string.settings_destination_playback_and_sound, listOf("pref_screen_playback")),
    Sources(R.string.settings_destination_sources, emptyList()),
    Library(R.string.settings_destination_library, listOf("pref_screen_media", "pref_screen_artwork")),
    Privacy(R.string.pref_category_title_privacy, listOf("pref_screen_privacy")),
    About(R.string.settings_destination_about, listOf("pref_screen_app_info", "pref_screen_debug"))
}

data class SettingsScreen(
    val destination: SettingsDestination,
    val groups: List<SettingsGroup>
) {
    val items: List<SettingItem> get() = groups.flatMap { it.items }
}

/** A run of rows, under a header when [title] is set. */
data class SettingsGroup(
    @StringRes val title: Int?,
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
    ClearArtworkCache,
    DownloadAllArtwork,
    CopyDebugLogs
}

/** A confirmation dialog shown before a destructive or costly action runs. */
data class Confirmation(
    @StringRes val title: Int,
    @StringRes val message: Int,
    @StringRes val confirm: Int
)

/** A switch that takes a row over while it's on: the row is disabled and shows [hint] in place of its value. */
data class SettingOverride(
    val setting: Setting<Boolean>,
    @StringRes val hint: Int
)

data class ChoiceOption<T>(
    val value: T,
    @StringRes val label: Int
)

/**
 * One row. Rows that store a value carry their [Setting], so key and default have one source; the rest carry
 * the key of the legacy preference they replace, if any.
 */
sealed interface SettingItem {
    @get:StringRes
    val title: Int

    @get:StringRes
    val summary: Int?

    /** Hidden below this API level. */
    val minSdk: Int

    /** Disabled while this switch is off. */
    val dependsOn: Setting<Boolean>?

    /** The preference key this row stores or replaces, if it has one. */
    val key: String?

    data class Switch(
        val setting: Setting<Boolean>,
        @StringRes override val title: Int,
        @StringRes override val summary: Int? = null,
        override val minSdk: Int = 1,
        override val dependsOn: Setting<Boolean>? = null
    ) : SettingItem {
        override val key: String get() = setting.key
    }

    data class Choice<T>(
        val setting: Setting<T>,
        @StringRes override val title: Int,
        val options: List<ChoiceOption<T>>,
        @StringRes override val summary: Int? = null,
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
        @StringRes override val title: Int,
        val range: ClosedFloatingPointRange<Float>,
        val steps: Int,
        val fromFloat: (Float) -> T,
        @StringRes override val summary: Int? = null,
        override val minSdk: Int = 1,
        override val dependsOn: Setting<Boolean>? = null
    ) : SettingItem {
        override val key: String get() = setting.key
    }

    data class Navigate(
        val target: SettingsLink,
        @StringRes override val title: Int,
        @StringRes override val summary: Int? = null,
        override val key: String? = null,
        override val minSdk: Int = 1,
        override val dependsOn: Setting<Boolean>? = null
    ) : SettingItem

    data class Action(
        val action: SettingsAction,
        @StringRes override val title: Int,
        @StringRes override val summary: Int? = null,
        val confirmation: Confirmation? = null,
        override val key: String? = null,
        override val minSdk: Int = 1,
        override val dependsOn: Setting<Boolean>? = null
    ) : SettingItem
}
