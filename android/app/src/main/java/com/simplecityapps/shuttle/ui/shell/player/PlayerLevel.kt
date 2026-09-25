package com.simplecityapps.shuttle.ui.shell.player

/**
 * How far the player is open. Shell state, not a route: it overlays every destination and never
 * pops with the back stack (docs/architecture/app-shell.md, section 1).
 *
 * [NowPlaying] is the sheet at rest, as tall as its artwork, title, transport and bar need, with the
 * library showing above it on a tall phone. [Expanded] is the sheet at full height, where the
 * Now Playing list scrolls its artwork away to show the open panel.
 */
enum class PlayerLevel { Hidden, Mini, NowPlaying, Expanded }

/**
 * How the player is presented at the current window size (app-shell.md, section 2).
 *
 * - [CompactSheet]: below 600 dp, a sheet that rests at its content's height and expands to full height.
 * - [Sheet]: 600 to 1199 dp, a full-height sheet whose Now Playing shows the open panel beside the player.
 * - [Pane]: from 1200 dp, a persistent trailing pane beside the destinations.
 */
enum class PlayerMode { CompactSheet, Sheet, Pane }

/**
 * The levels the player can settle at. Hidden is only reachable, and the only level, with an empty
 * queue. Only a compact sheet that rests below full height ([partialRest]) has an Expanded level.
 */
fun playerLevels(
    mode: PlayerMode,
    hasQueue: Boolean,
    partialRest: Boolean = true,
): Set<PlayerLevel> = when {
    !hasQueue -> setOf(PlayerLevel.Hidden)
    mode == PlayerMode.CompactSheet && partialRest -> setOf(PlayerLevel.Mini, PlayerLevel.NowPlaying, PlayerLevel.Expanded)
    else -> setOf(PlayerLevel.Mini, PlayerLevel.NowPlaying)
}

/** The level one back gesture steps down to, or null when back belongs to the destinations. Back never reaches Hidden. */
fun PlayerLevel.stepDown(): PlayerLevel? = when (this) {
    PlayerLevel.Expanded -> PlayerLevel.NowPlaying
    PlayerLevel.NowPlaying -> PlayerLevel.Mini
    PlayerLevel.Mini, PlayerLevel.Hidden -> null
}

/**
 * Maps a level across a change of presentation (app-shell.md, "Size or posture changes"):
 * sheet to pane opens the pane, pane to sheet always drops to Mini, and only a compact sheet keeps
 * Expanded; elsewhere the open panel shows at Now Playing.
 */
fun mapPlayerLevel(
    level: PlayerLevel,
    from: PlayerMode,
    to: PlayerMode,
): PlayerLevel = when {
    level == PlayerLevel.Hidden || from == to -> level
    to == PlayerMode.Pane -> PlayerLevel.NowPlaying
    from == PlayerMode.Pane -> PlayerLevel.Mini
    level == PlayerLevel.Expanded -> PlayerLevel.NowPlaying
    else -> level
}

/**
 * The level to settle at once [allowed] changes: [level] if it is still allowed, otherwise the
 * closest allowed one. A queue appearing reveals the mini player; a queue emptying hides the sheet.
 */
fun resolvePlayerLevel(
    level: PlayerLevel,
    allowed: Set<PlayerLevel>,
): PlayerLevel = when {
    level in allowed -> level
    PlayerLevel.Hidden in allowed -> PlayerLevel.Hidden
    level == PlayerLevel.Expanded -> PlayerLevel.NowPlaying
    else -> PlayerLevel.Mini
}
