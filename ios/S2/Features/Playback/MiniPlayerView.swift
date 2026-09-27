import Shared
import SwiftUI

/// The bar every tab's screens inset at the bottom (`tabViewBottomAccessory` / safe-area inset
/// candidate, #593): the song's cover, title and artist, play/pause and next; tapping opens Now Playing.
/// Bound to `PlayerModel` through `PlayerBinding` (the one place a later re-wire to the shared
/// `PlayerViewModel`, phase 4 wave 5, touches).
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
        let state = model.nowPlayingState
        let actions = model.playerActions
        MiniPlayerBar(
            title: state.title,
            artist: state.artist,
            artwork: state.artwork,
            isPlaying: state.isPlaying,
            onTap: { showNowPlaying = true },
            onPlayPause: actions.playPause,
            onNext: actions.next
        )
    }
}

/// The mini player's content: plain values in, so it previews and tests (ViewInspector) without Kotlin.
struct MiniPlayerBar: View {
    let title: String?
    let artist: String?
    let artwork: ArtworkSource?
    let isPlaying: Bool
    let onTap: () -> Void
    let onPlayPause: () -> Void
    let onNext: () -> Void

    var body: some View {
        HStack(spacing: Spacing.small) {
            Button(action: onTap) {
                HStack(spacing: Spacing.smallMedium) {
                    cover
                        .artworkTile(ArtworkSize.row)
                    VStack(alignment: .leading, spacing: 0) {
                        Text(title ?? "Not Playing")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(title == nil ? .secondary : .primary)
                            .lineLimit(1)
                            .accessibilityIdentifier("miniPlayer.title")
                        if let artist {
                            Text(artist)
                                .font(.footnote)
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
            .accessibilityIdentifier("miniPlayer.open")

            Button(action: onPlayPause) {
                Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                    .font(.title2)
                    .contentTransition(.symbolEffect(.replace))
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(isPlaying ? "Pause" : "Play")
            .accessibilityIdentifier("miniPlayer.playPause")

            Button(action: onNext) {
                Image(systemName: "forward.fill")
                    .font(.title3)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Next")
            .accessibilityIdentifier("miniPlayer.next")
        }
        .padding(.leading, Spacing.small)
        .padding(.trailing, Spacing.xsmall)
        .padding(.vertical, Spacing.xsmall)
        .background(.bar)
        .overlay(alignment: .top) {
            Divider()
        }
    }

    /// The song's cover, or the placeholder tile when nothing is queued.
    @ViewBuilder
    private var cover: some View {
        if let artwork {
            RemoteArtwork(artwork, points: ArtworkSize.row)
        } else {
            ArtworkPlaceholder()
        }
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
    MiniPlayerBar(
        title: "Paranoid Android", artist: "Radiohead", artwork: nil, isPlaying: true,
        onTap: {}, onPlayPause: {}, onNext: {}
    )
}

#Preview("Not playing") {
    MiniPlayerBar(title: nil, artist: nil, artwork: nil, isPlaying: false, onTap: {}, onPlayPause: {}, onNext: {})
}
