package com.simplecityapps.shuttle.entitlement

/**
 * What Shuttle Music Pro unlocks. The first use of any of them starts the one shared trial ([ServerAccessGate]); after
 * it, each asks for the upgrade instead. What isn't here (local playback, Chromecast, the graphic equalizer, editing
 * one song's tags, downloaded songs) is never gated.
 */
enum class ProFeature(
    /** Where the paywall opens from when this feature is refused. */
    val paywallSource: PaywallSource
) {
    /** Adding a remote server, streaming from one, and new downloads from one. */
    Servers(PaywallSource.ServerPlayback),

    /** Browsing the library from a car. The car can't show the paywall, so it shows an upgrade item instead. */
    AndroidAuto(PaywallSource.AndroidAuto),

    /** Editing the tags of more than one song at once. */
    BatchTagEdit(PaywallSource.BatchTagEdit),

    /** Replay gain, and the parametric/AutoEq equalizer once there is one. Turning replay gain off stays free. */
    AdvancedAudio(PaywallSource.AdvancedAudio)
}
