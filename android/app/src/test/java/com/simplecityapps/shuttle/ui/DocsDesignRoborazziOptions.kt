package com.simplecityapps.shuttle.ui

import com.github.takahirom.roborazzi.RoborazziOptions

/**
 * Shared [RoborazziOptions] for the `docs/design` screenshot tests (#458). Linux renders text
 * and rounded-corner anti-aliasing a little differently than the macOS-recorded goldens; observed
 * diffs peak at ~0.13% of pixels, so 0.2% verifies identically on both while still catching a real
 * UI regression (a changed colour or size moves far more than 0.2% of pixels).
 */
val DocsDesignRoborazziOptions = RoborazziOptions(
    captureType = RoborazziOptions.CaptureType.Screenshot(),
    compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.002f),
)
