import SwiftUI

/// One line of text that scrolls itself once when it is too long for the space it is given. Ported
/// from Shuttle Podcasts' `Components/MarqueeText.swift`. Style it from outside like `Text`
/// (`.font(.s2HeroTitle)`, `.foregroundStyle`); the pass restarts when the text changes.
///
/// For places where the text is the SUBJECT — Now Playing's title and artist — and truncating it
/// would hide the part that identifies it, but where a second line would cost height the layout
/// does not have. Everywhere the title is one row among many, plain truncation is still right: a
/// list of moving lines is noise.
///
/// The pass runs once, not forever. It starts after ``startDelay`` so the line can be read where it
/// landed, crosses at ``speed`` points per second — walking pace for the eye, not a ticker — and
/// then returns to the start and stays there, truncated. A single trip says "there is more here"
/// without the card becoming a thing that never settles.
///
/// With Reduce Motion on, nothing moves and the line is simply truncated.
struct MarqueeText: View {

    private let text: String
    private let startDelay: Double
    private let speed: CGFloat

    /// - Parameters:
    ///   - text: the single line to draw.
    ///   - startDelay: seconds at rest before the pass begins.
    ///   - speed: points per second for the pass.
    init(_ text: String, startDelay: Double = 1.5, speed: CGFloat = 30) {
        self.text = text
        self.startDelay = startDelay
        self.speed = speed
    }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var textWidth: CGFloat = 0
    @State private var availableWidth: CGFloat = 0
    @State private var offset: CGFloat = 0
    /// While the pass runs the full-width line is drawn and slid; at rest the truncated one is,
    /// so the resting state ends in a real ellipsis rather than a hard clip.
    @State private var isScrolling = false

    private var overflow: CGFloat { max(0, textWidth - availableWidth) }

    var body: some View {
        // The line that MOVES is an overlay; the layout is a hidden copy of the same text. A
        // scrolling line has to be laid out at its full width, and a full-width line in the layout
        // itself widens the box it is measured against — which reads back as "it fits", ends the
        // pass and re-arms it, forever. An overlay cannot resize its parent, so the ghost gives a
        // width that stays still while the text moves.
        Text(text)
            .lineLimit(1)
            .hidden()
            .accessibilityHidden(true)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                GeometryReader { proxy in
                    Color.clear.preference(key: AvailableWidthKey.self, value: proxy.size.width)
                }
            )
            // The same line at its natural width, measured off-screen: `fixedSize` refuses the
            // proposed width, so the reader below it reports what the text actually wants.
            .background(alignment: .leading) {
                Text(text)
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
                    .hidden()
                    .accessibilityHidden(true)
                    .background(
                        GeometryReader { proxy in
                            Color.clear.preference(key: TextWidthKey.self, value: proxy.size.width)
                        }
                    )
            }
            .overlay(alignment: .leading) { line }
            .onPreferenceChange(AvailableWidthKey.self) { availableWidth = $0 }
            .onPreferenceChange(TextWidthKey.self) { textWidth = $0 }
            .clipped()
            // Re-armed when the text changes or the measurements settle, and cancelled when the
            // view goes away mid-pass — a card that is dismissed must not keep animating.
            .task(id: Pass(text: text, overflow: overflow, reduceMotion: reduceMotion)) {
                await runPass()
            }
            .accessibilityElement()
            .accessibilityLabel(text)
    }

    @ViewBuilder
    private var line: some View {
        if isScrolling {
            Text(text)
                .lineLimit(1)
                .fixedSize(horizontal: true, vertical: false)
                .offset(x: offset)
        } else {
            Text(text)
                .lineLimit(1)
                .truncationMode(.tail)
        }
    }

    @MainActor
    private func runPass() async {
        isScrolling = false
        offset = 0
        guard overflow > 0, !reduceMotion else { return }
        guard await sleep(startDelay) else { return }

        let duration = Double(overflow / speed)
        isScrolling = true
        withAnimation(.linear(duration: duration)) { offset = -overflow }
        guard await sleep(duration + 1) else { return reset() }

        // Back to the start rather than wrapping: a tail sliding in from the right to meet its own
        // head is the artefact a one-pass marquee exists to avoid.
        withAnimation(.easeInOut(duration: 0.35)) { offset = 0 }
        guard await sleep(0.4) else { return reset() }
        isScrolling = false
    }

    /// `true` if the wait completed, `false` if the task was cancelled.
    private func sleep(_ seconds: Double) async -> Bool {
        (try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))) != nil
    }

    /// Cancellation lands here: leave the line where it can be read, not mid-slide.
    @MainActor
    private func reset() {
        offset = 0
        isScrolling = false
    }

    /// What a pass is FOR — a different string, or the same string with different room. Rounded so
    /// that sub-point layout jitter does not restart the animation.
    private struct Pass: Equatable {
        let text: String
        let overflow: CGFloat
        let reduceMotion: Bool

        static func == (lhs: Pass, rhs: Pass) -> Bool {
            lhs.text == rhs.text
                && lhs.reduceMotion == rhs.reduceMotion
                && lhs.overflow.rounded() == rhs.overflow.rounded()
        }
    }

    private struct AvailableWidthKey: PreferenceKey {
        static let defaultValue: CGFloat = 0
        static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
            value = max(value, nextValue())
        }
    }

    private struct TextWidthKey: PreferenceKey {
        static let defaultValue: CGFloat = 0
        static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
            value = max(value, nextValue())
        }
    }
}
