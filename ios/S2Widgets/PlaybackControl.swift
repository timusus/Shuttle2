import AppIntents
import SwiftUI
import WidgetKit

/// Play or pause from Control Center, the Lock Screen's controls or the Action button (iOS 18). Its state is the
/// app's last snapshot; the app reloads it on every change of play state.
@available(iOS 18.0, *)
struct PlaybackControl: ControlWidget {
    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: NowPlayingStore.playbackControlKind, provider: Provider()) { isPlaying in
            ControlWidgetToggle("Shuttle Music", isOn: isPlaying, action: SetPlaybackIntent()) { isPlaying in
                Label(isPlaying ? "Playing" : "Paused", systemImage: isPlaying ? "pause.fill" : "play.fill")
            }
        }
        .displayName("Play or Pause")
        .description("Plays or pauses Shuttle Music.")
    }

    struct Provider: ControlValueProvider {
        var previewValue: Bool { false }

        func currentValue() async throws -> Bool {
            NowPlayingStore().read()?.isPlaying ?? false
        }
    }
}
