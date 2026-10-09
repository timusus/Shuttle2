import SwiftUI

/// Previous, play/pause and next as one family: a 72 pt tint-filled play circle whose glyph swaps with a replace
/// transition, and previous and next as glyphs of the same weight in the same tint, each in a 60 pt target. All
/// scale with Dynamic Type up to a cap and press down on touch. While play is intended but no audio is out yet, a
/// spinner takes the glyph's place in the circle, and a tap pauses.
struct NowPlayingTransport: View {
    /// Play is intended (`PlayIntent`).
    let isPlaying: Bool
    var isLoading = false
    let actions: PlayerActions

    @Environment(\.artworkTint) private var tint
    @Environment(\.artworkTintInk) private var tintInk
    @ScaledMetric(relativeTo: .largeTitle) private var playDiameter: CGFloat = 72
    @ScaledMetric(relativeTo: .title) private var skipSize: CGFloat = 30
    @State private var playTrigger = false
    @State private var skipTrigger = false

    static let maxPlayDiameter: CGFloat = 88
    static let maxSkipSize: CGFloat = 38
    /// The play glyph's size as a share of the circle, and the transport's weight.
    static let playGlyphRatio: CGFloat = 0.4
    static let weight: Font.Weight = .semibold
    /// The skip buttons' target: larger than the 44 pt minimum, as the screen's primary controls.
    static let skipTarget: CGFloat = 60

    var body: some View {
        let diameter = min(playDiameter, Self.maxPlayDiameter)
        let skip = min(skipSize, Self.maxSkipSize)
        HStack(spacing: Spacing.large) {
            skipButton("backward.fill", size: skip, label: "Previous", action: actions.previous)

            Button {
                actions.playPause()
                playTrigger.toggle()
            } label: {
                Group {
                    if isLoading {
                        ProgressView()
                            .controlSize(.large)
                            .tint(tintInk)
                    } else {
                        Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                            .font(.s2ScaledGlyph(diameter * Self.playGlyphRatio, weight: Self.weight))
                            .foregroundStyle(tintInk)
                            .contentTransition(.symbolEffect(.replace))
                    }
                }
                .frame(width: diameter, height: diameter)
                .background(Circle().fill(tint))
                .contentShape(Circle())
            }
            .buttonStyle(.pressScale)
            .accessibilityLabel(isPlaying ? "Pause" : "Play")
            .accessibilityValue(isLoading ? "Loading" : "")
            .accessibilityIdentifier("nowPlaying.playPause")

            skipButton("forward.fill", size: skip, label: "Next", action: actions.next)
        }
        .sensoryFeedback(.impact(weight: .medium), trigger: playTrigger)
        .sensoryFeedback(.impact(weight: .light), trigger: skipTrigger)
    }

    private func skipButton(_ systemImage: String, size: CGFloat, label: String, action: @escaping () -> Void) -> some View {
        Button {
            action()
            skipTrigger.toggle()
        } label: {
            Image(systemName: systemImage)
                .font(.s2ScaledGlyph(size, weight: Self.weight))
                .foregroundStyle(tint)
                .frame(minWidth: Self.skipTarget, minHeight: Self.skipTarget)
                .contentShape(Rectangle())
        }
        .buttonStyle(.pressScale)
        .accessibilityLabel(label)
    }
}
