package com.simplecityapps.shuttle.entitlement

/** Where the paywall was opened from. */
enum class PaywallSource(val value: String) {
    LibraryTrialChip("library_trial_chip"),
    QueueTrialChip("queue_trial_chip"),
    Settings("settings"),
    AddServer("add_server"),
    ServerPlayback("server_playback"),
    ServerDownload("server_download")
}
