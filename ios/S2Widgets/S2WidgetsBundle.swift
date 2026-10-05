import SwiftUI
import WidgetKit

/// The widget extension (#758): Now Playing on the Home Screen and Lock Screen and, from iOS 18, a play/pause control
/// for Control Center and the Action button. It reads what the app writes to the App Group (`NowPlayingStore`) and
/// doesn't link the Kotlin framework; its buttons run the playback App Intents in the app.
@main
struct S2WidgetsBundle: WidgetBundle {
    var body: some Widget {
        NowPlayingWidget()
        if #available(iOS 18.0, *) {
            PlaybackControl()
        }
    }
}
