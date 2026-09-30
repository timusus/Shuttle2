import AVKit
import SwiftUI

/// The system AirPlay route picker (`AVRoutePickerView`), iOS's stand-in for Android's Cast button: tapping it opens
/// the system's output picker (AirPlay speakers, Bluetooth, the phone), and the glyph takes `activeTint` while a
/// route other than the device is active. After Shuttle Podcasts' `AirPlayButton`.
struct AirPlayButton: UIViewRepresentable {
    var activeTint: Color = .s2Accent
    var inactiveTint: Color = .secondary

    func makeUIView(context: Context) -> AVRoutePickerView {
        let picker = AVRoutePickerView()
        picker.prioritizesVideoDevices = false
        configure(picker)
        return picker
    }

    func updateUIView(_ picker: AVRoutePickerView, context: Context) {
        configure(picker)
    }

    private func configure(_ picker: AVRoutePickerView) {
        picker.tintColor = UIColor(inactiveTint)
        picker.activeTintColor = UIColor(activeTint)
        // The inner button has an empty title, which VoiceOver reads as nothing; name it and the view.
        picker.accessibilityLabel = Self.accessibilityLabel
        for case let button as UIButton in picker.subviews {
            button.accessibilityLabel = Self.accessibilityLabel
        }
    }

    static let accessibilityLabel = "AirPlay"
}
