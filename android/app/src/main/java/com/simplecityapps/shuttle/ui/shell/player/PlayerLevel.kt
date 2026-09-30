package com.simplecityapps.shuttle.ui.shell.player

/**
 * How far the player is open. Shell state, not a route: it overlays every destination and never
 * pops with the back stack (docs/architecture/app-shell.md, section 1).
 *
 * [Mini] is the mini player on the nav bar and [Full] the full-screen player. The queue and the
 * other panels are not levels: they open inside the full player, from their buttons
 * ([PlayerUiState.panel]).
 */
enum class PlayerLevel { Hidden, Mini, Full }

/**
 * How the player is presented at the current window size (app-shell.md, section 2).
 *
 * - [CompactSheet]: below 600 dp, a full-screen sheet over the destinations and nav bar.
 * - [Sheet]: 600 to 1199 dp, a full-height sheet whose open panel sits beside the player.
 * - [Pane]: from 1200 dp, a persistent trailing pane beside the destinations.
 */
enum class PlayerMode { CompactSheet, Sheet, Pane }

/** The levels the player can settle at. Hidden is only reachable, and the only level, with an empty queue. */
fun playerLevels(hasQueue: Boolean): Set<PlayerLevel> = if (hasQueue) setOf(PlayerLevel.Mini, PlayerLevel.Full) else setOf(PlayerLevel.Hidden)

/** The level one back gesture steps down to, or null when back belongs to the destinations. Back never reaches Hidden. */
fun PlayerLevel.stepDown(): PlayerLevel? = if (this == PlayerLevel.Full) PlayerLevel.Mini else null

/**
 * Maps a level across a change of presentation (app-shell.md, "Size or posture changes"):
 * sheet to pane opens the pane, pane to sheet always drops to Mini.
 */
fun mapPlayerLevel(
    level: PlayerLevel,
    from: PlayerMode,
    to: PlayerMode,
): PlayerLevel = when {
    level == PlayerLevel.Hidden || from == to -> level
    to == PlayerMode.Pane -> PlayerLevel.Full
    from == PlayerMode.Pane -> PlayerLevel.Mini
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
    else -> PlayerLevel.Mini
}
