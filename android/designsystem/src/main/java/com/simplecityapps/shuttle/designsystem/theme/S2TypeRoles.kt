package com.simplecityapps.shuttle.designsystem.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle

/*
 * S2's semantic type roles: what a piece of text is, mapped onto the [S2Typography] scale, so a screen says
 * `MaterialTheme.typography.rowTitle` rather than choosing an M3 style itself. The names follow the iOS app's
 * roles (`Font.s2RowTitle` and friends, ios/S2/Theme/Typography.swift) where the two apps have the same thing.
 * A role reads the theme's [Typography], so a nested theme or font scale changes it with the rest.
 */

/** A detail screen's title under its hero artwork (album, artist, playlist, genre, song info). */
val Typography.heroTitle: TextStyle get() = headlineSmall

/** The line under a hero title: artist, year, counts. On `onSurfaceVariant`. */
val Typography.heroSubtitle: TextStyle get() = bodyMedium

/** The now-playing song title, the one emphasized line on the player at every size. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val Typography.playerTitle: TextStyle get() = headlineMediumEmphasized

/** The now-playing artist line under [playerTitle]. */
val Typography.playerSubtitle: TextStyle get() = titleLarge

/** A screen-level title that isn't a hero: an empty or error state's. */
val Typography.screenTitle: TextStyle get() = titleLarge

/** The lead section of a screen, over its [sectionTitle] sections, such as Home's first shelf. */
val Typography.leadSectionTitle: TextStyle get() = headlineSmall

/** An in-content section: a Home shelf after the first. */
val Typography.sectionTitle: TextStyle get() = titleLarge

/** A label that titles a run of rows rather than a section: a letter or disc header. On `primary`. */
val Typography.groupHeader: TextStyle get() = titleSmall

/** A list row's title, on `onSurface`. */
val Typography.rowTitle: TextStyle get() = bodyLarge

/** A list row's second line (artist, album, counts), and a section header's subtitle. On `onSurfaceVariant`. */
val Typography.rowSubtitle: TextStyle get() = bodyMedium

/** A row's trailing duration or count. */
val Typography.rowMeta: TextStyle get() = labelMedium

/** A grid tile's or shelf card's title. */
val Typography.tileTitle: TextStyle get() = titleSmall

/** A grid tile's second line, on `onSurfaceVariant`. */
val Typography.tileSubtitle: TextStyle get() = bodyMedium

/** Supporting text under a title or control: an empty state's message, a setting's summary, a hint. */
val Typography.supporting: TextStyle get() = bodyMedium

/** A scrubber's elapsed and remaining times. */
val Typography.time: TextStyle get() = labelMedium
