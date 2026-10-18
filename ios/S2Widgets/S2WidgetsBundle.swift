import SwiftUI
import WidgetKit

/// The widget extension reads the App Group snapshot the app writes and doesn't link the Kotlin framework; its buttons run in the app.
@main
struct S2WidgetsBundle: WidgetBundle {
    var body: some Widget {
        NowPlayingWidget()
        if #available(iOS 18.0, *) {
            PlaybackControl()
        }
    }
}
