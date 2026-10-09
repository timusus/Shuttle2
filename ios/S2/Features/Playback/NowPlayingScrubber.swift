import SwiftUI

/// The position scrubber: a capsule track filled in the tint to the position, 6 pt at rest and 10 pt while dragged,
/// with the elapsed and remaining times under it. A drag anywhere on it holds the dragged position locally and seeks
/// once, on release. VoiceOver reads it as one adjustable element ("1:30 of 6:26"), each swipe seeking
/// `accessibilityStepMs`.
struct NowPlayingScrubber: View {
    let positionMs: Int
    let durationMs: Int
    /// True while a finger is on the track: Now Playing's swipe-down dismiss stands aside.
    var isScrubbing: Binding<Bool> = .constant(false)
    /// The elapsed and remaining times' colour: the player's caption ink.
    var timeInk: Color = .secondary
    let onSeek: (Int) -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var dragging = false
    @State private var scrubMs: Double = 0

    /// The track's height at rest and while dragged.
    static let trackHeight: CGFloat = 6
    static let draggingTrackHeight: CGFloat = 10

    init(
        positionMs: Int,
        durationMs: Int,
        isScrubbing: Binding<Bool> = .constant(false),
        timeInk: Color = .secondary,
        onSeek: @escaping (Int) -> Void
    ) {
        self.positionMs = positionMs
        self.durationMs = durationMs
        self.isScrubbing = isScrubbing
        self.timeInk = timeInk
        self.onSeek = onSeek
    }

    var body: some View {
        let current = dragging ? Int(scrubMs) : positionMs
        let fraction = durationMs > 0 ? min(1, max(0, Double(current) / Double(durationMs))) : 0
        VStack(spacing: Spacing.xsmall) {
            GeometryReader { proxy in
                let width = proxy.size.width
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(Color.primary.opacity(0.15))
                    Capsule()
                        .fill(.tint)
                        .frame(width: width * fraction)
                }
                .frame(height: dragging ? Self.draggingTrackHeight : Self.trackHeight)
                .frame(maxHeight: .infinity)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { value in
                            if !dragging {
                                dragging = true
                                isScrubbing.wrappedValue = true
                            }
                            let x = min(max(value.location.x, 0), width)
                            scrubMs = width > 0 ? Double(x / width) * Double(durationMs) : 0
                        }
                        .onEnded { _ in
                            onSeek(Int(scrubMs))
                            dragging = false
                            isScrubbing.wrappedValue = false
                        }
                )
            }
            .frame(height: TouchTarget.minimum / 2 + Self.draggingTrackHeight)
            .animation(Motion.press.reduced(reduceMotion), value: dragging)
            .accessibilityElement()
            .accessibilityLabel("Playback position")
            .accessibilityValue("\(Self.formatted(ms: current)) of \(Self.formatted(ms: durationMs))")
            .accessibilityAdjustableAction { direction in
                if let target = Self.adjusted(positionMs: positionMs, durationMs: durationMs, direction) {
                    onSeek(target)
                }
            }
            .accessibilityIdentifier("nowPlaying.scrubber")

            HStack {
                Text(Self.formatted(ms: current))
                Spacer()
                Text("-" + Self.formatted(ms: max(0, durationMs - current)))
            }
            .font(.s2Time)
            .foregroundStyle(timeInk)
            .accessibilityHidden(true)
        }
    }

    /// How far one VoiceOver adjustment seeks.
    static let accessibilityStepMs = 15_000

    /// Where one VoiceOver adjustment seeks to, clamped to the song.
    static func adjusted(positionMs: Int, durationMs: Int, _ direction: AccessibilityAdjustmentDirection) -> Int? {
        switch direction {
        case .increment: min(durationMs, positionMs + accessibilityStepMs)
        case .decrement: max(0, positionMs - accessibilityStepMs)
        @unknown default: nil
        }
    }

    static func formatted(ms: Int) -> String {
        let totalSeconds = max(0, ms) / 1000
        return String(format: "%d:%02d", totalSeconds / 60, totalSeconds % 60)
    }
}
