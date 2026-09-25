package com.simplecityapps.shuttle.designsystem.catalog

import androidx.compose.runtime.Composable

/**
 * One component in the catalogue. [id] is the catalogue key (`row-song`), [states] what the board
 * shows, for the review page. Boards that don't change with the scheme ([schemeSensitive] false)
 * record in the brand scheme only.
 */
class CatalogEntry(
    val id: String,
    val title: String,
    val states: List<String>,
    val schemeSensitive: Boolean = true,
    val fontScales: List<Float> = listOf(1f),
    val board: @Composable (BoardWidth) -> Unit,
)

val CatalogEntries = listOf(
    CatalogEntry(
        "theme-colour",
        "Colour",
        listOf("every role with its on-colour", "High contrast", "the six root accents"),
    ) { ThemeColourBoard(it) },
    CatalogEntry(
        "theme-type",
        "Type",
        listOf("the 15 styles", "the 15 emphasized styles", "font scale 1.0 and 2.0"),
        schemeSensitive = false,
        fontScales = listOf(1f, 2f),
    ) { ThemeTypeBoard(it) },
    CatalogEntry(
        "theme-shape",
        "Shape",
        listOf("shape scale with the Expressive tokens", "MaterialShapes in use", "morph strips"),
        schemeSensitive = false,
    ) { ThemeShapeBoard(it) },
    CatalogEntry(
        "button",
        "Button",
        listOf("filled, tonal, outlined, text", "enabled, pressed, focused, disabled", "extra small to large, with and without icon"),
    ) { ButtonBoard(it) },
    CatalogEntry(
        "button-group",
        "Button group",
        listOf("standard, each button pressed", "connected, each option checked", "connected pressed and disabled"),
    ) { ButtonGroupBoard(it) },
    CatalogEntry(
        "icon-button",
        "Icon button",
        listOf("standard, filled, tonal, outlined", "enabled, pressed, unchecked, checked, disabled", "extra small to large"),
    ) { IconButtonBoard(it) },
    CatalogEntry(
        "artwork",
        "Artwork",
        listOf("rounded and circle at 40, 56 and grid", "hero", "loading", "placeholder per media type"),
    ) { ArtworkBoard(it) },
    CatalogEntry(
        "row-song",
        "Song row",
        listOf("default", "track number", "playing", "selected", "missing file", "downloading", "offline", "long text"),
    ) { SongRowBoard(it) },
    CatalogEntry("row-album", "Album row", listOf("default", "selected", "no artwork", "long text")) { AlbumRowBoard(it) },
    CatalogEntry("row-artist", "Artist row", listOf("default", "selected", "no image")) { ArtistRowBoard(it) },
    CatalogEntry("row-playlist", "Playlist row", listOf("user", "smart", "empty", "selected")) { PlaylistRowBoard(it) },
    CatalogEntry(
        "top-bar",
        "Top bar",
        listOf("large flexible expanded with subtitle and actions", "collapsed", "title only with back", "detail bar", "long title"),
    ) { TopBarBoard(it) },
    CatalogEntry("search", "Search", listOf("collapsed", "focused empty with recent searches", "typing with results", "no results")) { SearchBoard(it) },
    CatalogEntry("nav-bar", "Navigation bar", listOf("4 items, each selection", "dot and count badges", "3 items", "long count")) { NavBarBoard(it) },
    CatalogEntry("nav-rail", "Navigation rail", listOf("collapsed: selected, badged, secondary selected", "expanded with secondary items")) { NavRailBoard(it) },
    CatalogEntry("chip-sort-filter", "Sort and filter chips", listOf("sort field and order", "filters selected and unselected", "removable source filters", "disabled")) {
        ChipBoard(it)
    },
    CatalogEntry("menu", "Menu", listOf("overflow with icons, groups and a destructive item", "sort with checked items")) { MenuBoard(it) },
    CatalogEntry("song-actions-sheet", "Actions sheet", listOf("song target with destructive item", "playlist target with long title")) { ActionsSheetBoard(it) },
    CatalogEntry("dialog", "Dialog", listOf("confirm", "destructive with icon", "choice list", "text input with confirm disabled", "text input filled")) { DialogBoard(it) },
    CatalogEntry("snackbar", "Snackbar", listOf("message", "with action", "long with action and dismiss", "above the nav bar")) { SnackbarBoard(it) },
    CatalogEntry("toolbar-selection", "Selection toolbar", listOf("floating, 1 selected", "floating, many with overflow", "overflow open", "docked alternative")) {
        SelectionToolbarBoard(it)
    },
    CatalogEntry("mini-player", "Mini player", listOf("playing", "paused", "loading", "above the navigation bar")) { MiniPlayerBoard(it) },
    CatalogEntry("player-controls", "Player controls", listOf("playing", "paused", "buffering", "shuffle on, repeat all", "repeat one")) {
        PlayerControlsBoard(it)
    },
    CatalogEntry("seek-bar", "Seek bar", listOf("playing", "paused", "dragging", "over an hour")) { SeekBarBoard(it) },
    CatalogEntry("progress", "Playback progress", listOf("playing", "paused", "indeterminate")) { ProgressBoard(it) },
    CatalogEntry("queue-row", "Queue row", listOf("played", "current", "upcoming", "dragging")) { QueueRowBoard(it) },
    CatalogEntry("row-genre", "Genre row", listOf("default", "selected", "one song")) { GenreRowBoard(it) },
    CatalogEntry("row-folder", "Folder row", listOf("folder", "file", "file without artwork", "selected")) { FolderRowBoard(it) },
    CatalogEntry(
        "grid-tile",
        "Grid tile",
        listOf("album, artist, playlist with the scalloped mask", "playing", "selected", "long text", "placeholder"),
    ) { GridTileBoard(it) },
    CatalogEntry("section-header", "Section header", listOf("with action", "plain", "sticky letter header")) { SectionHeaderBoard(it) },
    CatalogEntry("state-empty", "Empty state", listOf("with action", "without action")) { EmptyStateBoard(it) },
    CatalogEntry("state-loading", "Loading state", listOf("full screen", "determinate", "inline and pull to refresh")) { LoadingStateBoard(it) },
    CatalogEntry("state-error", "Error state", listOf("retry", "provider sign-in")) { ErrorStateBoard(it) },
)

/** One recorded board image. */
class CatalogShot(
    val entry: CatalogEntry,
    val darkTheme: Boolean,
    val width: BoardWidth,
    val scheme: CatalogScheme,
    val fontScale: Float,
) {
    /** Path under the catalogue directory: `<id>/<id>_<light|dark>_<width>_<scheme>[_fs2].png`. */
    val path: String
        get() {
            val theme = if (darkTheme) "dark" else "light"
            val scale = if (fontScale == 1f) "" else "_fs${fontScale.toString().removeSuffix(".0")}"
            return "${entry.id}/${entry.id}_${theme}_${width.name.lowercase()}_${scheme.name.lowercase()}$scale.png"
        }

    override fun toString(): String = path.substringAfter('/').removeSuffix(".png")
}

/**
 * Every board image: light and dark at compact width in each recorded scheme, and at expanded
 * width in the brand scheme only, since the seeds change colour and not layout.
 */
fun catalogShots(entries: List<CatalogEntry> = CatalogEntries): List<CatalogShot> = entries.flatMap { entry ->
    val schemes = if (entry.schemeSensitive) CatalogScheme.entries.filter { it.recorded } else listOf(CatalogScheme.Brand)
    listOf(false, true).flatMap { dark ->
        schemes.flatMap { scheme -> entry.fontScales.map { CatalogShot(entry, dark, BoardWidth.Compact, scheme, it) } } +
            CatalogShot(entry, dark, BoardWidth.Expanded, CatalogScheme.Brand, 1f)
    }
}
