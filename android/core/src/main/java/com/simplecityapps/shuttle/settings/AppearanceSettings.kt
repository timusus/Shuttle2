package com.simplecityapps.shuttle.settings

import javax.inject.Inject
import javax.inject.Singleton

/** Light, dark or follow the system. Stored by ordinal, so the order is fixed. */
enum class ThemeMode {
    DayNight,
    Light,
    Dark
}

/** The theme accent colour. Stored by ordinal, so the order is fixed; new accents go at the end. */
enum class Accent {
    Default,
    Orange,
    Cyan,
    Purple,
    Green,
    Amber
}

@Singleton
class AppearanceSettings @Inject constructor(
    store: SettingsStore
) {
    val theme = store.preference(Theme)
    val accent = store.preference(AccentColour)
    val pureBlack = store.preference(PureBlack)
    val dynamicColour = store.preference(DynamicColour)
    val colourFromArtwork = store.preference(ColourFromArtwork)
    val showHomeOnLaunch = store.preference(ShowHomeOnLaunch)
    val widgetBackgroundOpacity = store.preference(WidgetBackgroundOpacity)

    companion object {
        val Theme = Setting.enumOrdinalString("pref_theme", ThemeMode.DayNight, ThemeMode.entries)
        val AccentColour = Setting.enumOrdinalString("pref_theme_accent", Accent.Default, Accent.entries)

        /** Shown as "Pure black"; the key predates the rename from "Extra dark". */
        val PureBlack = Setting.boolean("pref_theme_extra_dark", false)

        /** Material You wallpaper colours in place of the accent (API 31+). New with the Compose settings. */
        val DynamicColour = Setting.boolean("pref_theme_dynamic_colour", false)

        /** Seeds the player and artwork detail screens from their artwork (app-shell decision 1). New with the Compose settings. */
        val ColourFromArtwork = Setting.boolean("pref_theme_colour_from_artwork", true)

        val ShowHomeOnLaunch = Setting.boolean("pref_show_home_on_launch", false)

        /** A percentage. The key predates the Glance widgets, so old values carry over. */
        val WidgetBackgroundOpacity = Setting.int("widget_background_opacity", 100)
    }
}
