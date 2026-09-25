package com.simplecityapps.shuttle.ui.shell.player

/**
 * How far the player is open. Shell state, not a route: it overlays every destination and never
 * pops with the back stack (docs/architecture/app-shell.md, section 1).
 */
enum class PlayerLevel { Hidden, Mini, NowPlaying, Queue }

/**
 * How the player is presented at the current window size (app-shell.md, section 2).
 *
 * - [CompactSheet]: below 600 dp, a sheet with a separate Queue level.
 * - [Sheet]: 600 to 1199 dp, a sheet whose Now Playing already shows the queue beside the player.
 * - [Pane]: from 1200 dp, a persistent trailing pane beside the destinations.
 */
enum class PlayerMode { CompactSheet, Sheet, Pane }

/** The levels the player can settle at. Hidden is only reachable, and the only level, with an empty queue. */
fun playerLevels(
    mode: PlayerMode,
    hasQueue: Boolean,
): Set<PlayerLevel> = when {
    !hasQueue -> setOf(PlayerLevel.Hidden)
    mode == PlayerMode.Sheet -> setOf(PlayerLevel.Mini, PlayerLevel.NowPlaying)
    else -> setOf(PlayerLevel.Mini, PlayerLevel.NowPlaying, PlayerLevel.Queue)
}

/** The level one back gesture steps down to, or null when back belongs to the destinations. Back never reaches Hidden. */
fun PlayerLevel.stepDown(): PlayerLevel? = when (this) {
    PlayerLevel.Queue -> PlayerLevel.NowPlaying
    PlayerLevel.NowPlaying -> PlayerLevel.Mini
    PlayerLevel.Mini, PlayerLevel.Hidden -> null
}

/**
 * Maps a level across a change of presentation (app-shell.md, "Size or posture changes"):
 * sheet to pane opens the pane (Queue stays Queue), pane to sheet always drops to Mini, and
 * Compact to Medium or Expanded folds Queue into Now Playing, which shows the queue there.
 */
fun mapPlayerLevel(
    level: PlayerLevel,
    from: PlayerMode,
    to: PlayerMode,
): PlayerLevel = when {
    level == PlayerLevel.Hidden || from == to -> level
    to == PlayerMode.Pane -> if (level == PlayerLevel.Queue) PlayerLevel.Queue else PlayerLevel.NowPlaying
    from == PlayerMode.Pane -> PlayerLevel.Mini
    to == PlayerMode.Sheet && level == PlayerLevel.Queue -> PlayerLevel.NowPlaying
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
    level == PlayerLevel.Queue -> PlayerLevel.NowPlaying
    else -> PlayerLevel.Mini
}
