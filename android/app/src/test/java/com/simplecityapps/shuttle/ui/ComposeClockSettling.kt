package com.simplecityapps.shuttle.ui

import androidx.compose.ui.test.junit4.ComposeTestRule

/**
 * Pauses the main clock, runs [block] (default: nothing, to settle whatever is already composed),
 * then steps the clock forward by [settleMillis] so an animated recomposition
 * (animateColorAsState, animateColorScheme, ...) settles at a deterministic frame instead of
 * racing host timing (#539, #532) — a spring can otherwise take longer to converge than a loaded
 * host's real-time idle budget allows, throwing AppNotIdleException. Restores autoAdvance
 * afterwards so later interactions (taps, their own transitions) still drive the clock normally;
 * pausing around an interaction that opens a sheet or navigates can starve that transition of
 * frames and leave it visually incomplete, so [block] should only set content, not tap or type —
 * settle right before the capture instead (see PlayerExtrasScreenshotTest.shot).
 */
fun ComposeTestRule.settlingClock(settleMillis: Long = 1_000L, block: () -> Unit = {}) {
    mainClock.autoAdvance = false
    block()
    mainClock.advanceTimeBy(settleMillis)
    mainClock.autoAdvance = true
}
