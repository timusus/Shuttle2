import SwiftUI

/// Equalizer & Playback Settings, pushed from the Audio sheet's button: Settings' Playback & Sound screen on its own
/// (the shuffle rule, the Equalizer row, ReplayGain and its pre-amp), so the Equalizer and ReplayGain are one tap
/// from the player. The same rows and view model as Settings, not a copy.
struct PlaybackSettingsView: View {
    static let cacheKey = "playbackSettings"

    var body: some View {
        SettingsView(cacheKey: Self.cacheKey, destinations: [.playbackAndSound], title: "Playback Settings")
            // The sheet's own stack, so the Equalizer row's link pushes inside it.
            .navigationDestination(for: Route.self) { route in
                if route == .equalizer { EqualizerView() }
            }
    }
}
