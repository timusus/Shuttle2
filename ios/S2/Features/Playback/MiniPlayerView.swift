import Shared
import SwiftUI

/// The bar every tab's screens inset at the bottom (`tabViewBottomAccessory` / safe-area inset
/// candidate, #593): song, artist, play/pause and next; tapping opens Now Playing. Bound to
/// `PlayerModel` over the Kotlin `IosPlayerController` (#588); the shared `PlayerViewModel` isn't
/// ported yet (phase 4 wave 5, docs/architecture/ios-port/phase-5-ios-app.md).
struct MiniPlayerView: View {
    let model: PlayerModel
    @Binding var showNowPlaying: Bool

    /// `model` defaults to the app's single `PlayerModel`, built once in `IosAppDependencies`: a view
    /// struct like this one is re-initialised on every parent body, so a fresh `PlayerModel` per init
    /// would restart its flows every time (`.claude/rules/ios.md`).
    init(
        showNowPlaying: Binding<Bool>,
        model: PlayerModel = AppGraph.dependencies.playerModel
    ) {
        self.model = model
        self._showNowPlaying = showNowPlaying
    }

    var body: some View {
        MiniPlayerBar(
            title: model.title,
            artist: model.artist,
            isPlaying: model.isPlaying,
            onTap: { showNowPlaying = true },
            onPlayPause: model.togglePlayPause,
            onNext: model.next
        )
    }
}

/// The mini player's content: plain values in, so it previews and tests (ViewInspector) without Kotlin.
struct MiniPlayerBar: View {
    let title: String?
    let artist: String?
    let isPlaying: Bool
    let onTap: () -> Void
    let onPlayPause: () -> Void
    let onNext: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Button(action: onTap) {
                HStack(spacing: 12) {
                    Image(systemName: "music.note")
                        .frame(width: 40, height: 40)
                        .background(.quaternary, in: RoundedRectangle(cornerRadius: 6))
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 0) {
                        Text(title ?? "Not Playing")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(title == nil ? .secondary : .primary)
                            .lineLimit(1)
                        if let artist {
                            Text(artist)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }
                    }
                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(accessibilityLabel)
            .accessibilityHint("Opens Now Playing")

            Button(action: onPlayPause) {
                Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                    .font(.title3)
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel(isPlaying ? "Pause" : "Play")

            Button(action: onNext) {
                Image(systemName: "forward.fill")
                    .font(.title3)
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel("Next")
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(.bar)
        .accessibilityIdentifier("miniPlayer")
    }

    private var accessibilityLabel: String {
        [title ?? "Not Playing", artist].compactMap { $0 }.joined(separator: ", ")
    }
}

extension View {
    /// Insets the mini player at the bottom of this screen. Apply it INSIDE the `NavigationStack`, to the root screen
    /// and to every pushed one (`routeDestinations`): attached to the stack itself (Shuttle Podcasts found) the bar
    /// draws but reserves no safe area and receives no touches.
    func miniPlayerInset(showNowPlaying: Binding<Bool>) -> some View {
        safeAreaInset(edge: .bottom, spacing: 0) {
            MiniPlayerView(showNowPlaying: showNowPlaying)
        }
    }
}

#Preview("Playing") {
    MiniPlayerBar(title: "Paranoid Android", artist: "Radiohead", isPlaying: true, onTap: {}, onPlayPause: {}, onNext: {})
}

#Preview("Not playing") {
    MiniPlayerBar(title: nil, artist: nil, isPlaying: false, onTap: {}, onPlayPause: {}, onNext: {})
}
