import SwiftUI

/// Now Playing, after Shuttle Podcasts' player: the cover, a large title and artist, a scrubber with
/// monospaced elapsed and remaining times, the transport, and shuffle, repeat and the queue along the bottom.
/// Presented by `nowPlayingPresentation` (a full-screen cover in `compact`, a form sheet otherwise); the queue
/// opens through `playerSheet` (a sheet in `compact`, a popover otherwise). Bound to `PlayerModel` through
/// `PlayerBinding.swift` only.
struct NowPlayingView: View {
    let model: PlayerModel
    @Environment(\.dismiss) private var dismiss

    /// `model` defaults to the same `PlayerModel` `MiniPlayerView` uses (`AppGraph.dependencies.playerModel`),
    /// so the two always show the same state.
    init(model: PlayerModel = AppGraph.dependencies.playerModel) {
        self.model = model
    }

    var body: some View {
        NowPlayingContent(state: model.nowPlayingState, actions: model.playerActions, onClose: { dismiss() })
    }
}

/// The screen's content: plain values in, so it previews and tests (ViewInspector) without Kotlin.
struct NowPlayingContent: View {
    let state: NowPlayingState
    var actions: PlayerActions = .none
    var onClose: () -> Void = {}

    @Environment(\.layoutTier) private var tier
    @State private var showQueue = false

    var body: some View {
        VStack(spacing: 0) {
            closeRow
            if state.title == nil {
                EmptyState("Nothing Playing", systemImage: "play.circle", message: "Play a song from your library.")
                    .frame(maxHeight: .infinity)
            } else {
                player
            }
        }
        .background(Color(.systemBackground))
    }

    private var closeRow: some View {
        HStack {
            Button(action: onClose) {
                Image(systemName: "chevron.down")
                    .font(.title3.weight(.semibold))
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .foregroundStyle(.secondary)
            .accessibilityLabel("Close")
            .accessibilityIdentifier("nowPlaying.close")
            Spacer()
        }
        .padding(.horizontal, Spacing.small)
    }

    private var player: some View {
        VStack(spacing: Spacing.large) {
            artwork
                .frame(maxHeight: .infinity)

            VStack(spacing: Spacing.tiny) {
                Text(state.title ?? "")
                    .font(.s2Title2)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .accessibilityAddTraits(.isHeader)
                if let subtitle {
                    Text(subtitle)
                        .font(.body)
                        .foregroundStyle(.s2SecondaryText)
                        .multilineTextAlignment(.center)
                        .lineLimit(1)
                }
            }

            NowPlayingScrubber(positionMs: state.positionMs, durationMs: state.durationMs, onSeek: actions.seek)

            NowPlayingTransport(isPlaying: state.isPlaying, actions: actions)

            bottomBar
        }
        .padding(.horizontal, Spacing.large)
        .padding(.bottom, Spacing.medium)
    }

    /// The cover: square, as large as the space left allows, decoded at the tier's largest size.
    private var artwork: some View {
        Group {
            if let source = state.artwork {
                RemoteArtwork(source, points: artworkPoints)
            } else {
                ArtworkPlaceholder()
            }
        }
        .aspectRatio(1, contentMode: .fit)
        .frame(maxWidth: artworkPoints)
        .clipShape(RoundedRectangle(cornerRadius: Radius.large, style: .continuous))
        .shadow(color: .black.opacity(0.15), radius: 12, y: 6)
        .accessibilityHidden(true)
    }

    /// The largest the cover draws: a phone's width in `compact`, the form sheet's column otherwise.
    private var artworkPoints: CGFloat {
        tier == .compact ? 420 : 440
    }

    private var subtitle: String? {
        let parts = [state.artist, state.album].compactMap { $0 }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    private var bottomBar: some View {
        HStack {
            Button(action: actions.toggleShuffle) {
                Image(systemName: "shuffle")
                    .modeGlyph(isOn: state.shuffleOn)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Shuffle")
            .accessibilityValue(state.shuffleOn ? "On" : "Off")
            .accessibilityIdentifier("nowPlaying.shuffle")

            Spacer()

            Button(action: actions.toggleRepeat) {
                Image(systemName: state.repeatMode == .one ? "repeat.1" : "repeat")
                    .modeGlyph(isOn: state.repeatMode != .off)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Repeat")
            .accessibilityValue(repeatValue)
            .accessibilityIdentifier("nowPlaying.repeat")

            Spacer()

            Button { showQueue = true } label: {
                Image(systemName: "list.bullet")
                    .modeGlyph(isOn: false)
            }
            .buttonStyle(.plain)
            .disabled(state.queue.isEmpty)
            .accessibilityLabel("Queue")
            .accessibilityIdentifier("nowPlaying.queue")
            .playerSheet(isPresented: $showQueue, tier: tier) {
                NowPlayingQueueList(queue: state.queue) { index in
                    actions.selectQueueItem(index)
                }
            }
        }
    }

    private var repeatValue: String {
        switch state.repeatMode {
        case .off: "Off"
        case .all: "All"
        case .one: "One"
        }
    }
}

private extension Image {
    /// A bottom-bar glyph: the accent while its mode is on, secondary otherwise, in a 44pt target.
    func modeGlyph(isOn: Bool) -> some View {
        font(.title3)
            .foregroundStyle(isOn ? AnyShapeStyle(.tint) : AnyShapeStyle(.s2SecondaryText))
            .frame(width: 44, height: 44)
            .contentShape(Rectangle())
    }
}

/// The position slider with elapsed and remaining times under it. Holds the dragged position locally and
/// seeks once, on release.
struct NowPlayingScrubber: View {
    let positionMs: Int
    let durationMs: Int
    let onSeek: (Int) -> Void

    @State private var isScrubbing = false
    @State private var scrubMs: Double = 0

    var body: some View {
        let current = isScrubbing ? Int(scrubMs) : positionMs
        VStack(spacing: Spacing.xsmall) {
            Slider(
                value: Binding(
                    get: { isScrubbing ? scrubMs : Double(positionMs) },
                    set: { newValue in
                        isScrubbing = true
                        scrubMs = newValue
                    }
                ),
                in: 0...Double(max(durationMs, 1)),
                onEditingChanged: { editing in
                    if !editing {
                        onSeek(Int(scrubMs))
                        isScrubbing = false
                    }
                }
            )
            .accessibilityLabel("Playback position")
            .accessibilityValue("\(Self.formatted(ms: current)) of \(Self.formatted(ms: durationMs))")

            HStack {
                Text(Self.formatted(ms: current))
                Spacer()
                Text("-" + Self.formatted(ms: max(0, durationMs - current)))
            }
            .font(.s2Time)
            .foregroundStyle(.s2SecondaryText)
            .accessibilityHidden(true)
        }
    }

    static func formatted(ms: Int) -> String {
        let totalSeconds = max(0, ms) / 1000
        return String(format: "%d:%02d", totalSeconds / 60, totalSeconds % 60)
    }
}

/// Previous, play/pause and next; glyphs scale with Dynamic Type up to a cap, targets never under 44pt.
struct NowPlayingTransport: View {
    let isPlaying: Bool
    let actions: PlayerActions

    @ScaledMetric(relativeTo: .largeTitle) private var playSize: CGFloat = 64
    @ScaledMetric(relativeTo: .title) private var skipSize: CGFloat = 30

    var body: some View {
        HStack(spacing: Spacing.xlarge) {
            glyphButton("backward.fill", size: min(skipSize, 40), label: "Previous", action: actions.previous)
            glyphButton(
                isPlaying ? "pause.circle.fill" : "play.circle.fill",
                size: min(playSize, 84),
                label: isPlaying ? "Pause" : "Play",
                action: actions.playPause
            )
            .foregroundStyle(.tint)
            glyphButton("forward.fill", size: min(skipSize, 40), label: "Next", action: actions.next)
        }
    }

    private func glyphButton(_ systemImage: String, size: CGFloat, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: size))
                .frame(minWidth: 44, minHeight: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

/// The queue, from Now Playing's queue button: every item with the current one marked, tap to skip to it.
struct NowPlayingQueueList: View {
    let queue: [NowPlayingQueueRow]
    let onSelect: (Int) -> Void

    var body: some View {
        NavigationStack {
            List {
                ForEach(Array(queue.enumerated()), id: \.element.id) { index, item in
                    Button { onSelect(index) } label: {
                        row(item)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(item.isCurrent ? "\(item.title), now playing" : item.title)
                }
            }
            .listStyle(.plain)
            .navigationTitle("Up Next")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private func row(_ item: NowPlayingQueueRow) -> some View {
        HStack(spacing: Spacing.smallMedium) {
            Group {
                if let artwork = item.artwork {
                    RemoteArtwork(artwork, points: ArtworkSize.row)
                } else {
                    ArtworkPlaceholder()
                }
            }
            .artworkTile(ArtworkSize.row)

            VStack(alignment: .leading, spacing: Spacing.tiny) {
                Text(item.title)
                    .font(.body.weight(item.isCurrent ? .semibold : .regular))
                    .foregroundStyle(item.isCurrent ? AnyShapeStyle(.tint) : AnyShapeStyle(.primary))
                    .lineLimit(1)
                if let artist = item.artist {
                    Text(artist).font(.subheadline).foregroundStyle(.s2SecondaryText).lineLimit(1)
                }
            }
            Spacer(minLength: 0)
            if item.isCurrent {
                Image(systemName: "speaker.wave.2.fill")
                    .foregroundStyle(.tint)
                    .accessibilityHidden(true)
            }
        }
        .contentShape(Rectangle())
    }
}

#Preview("Playing") {
    NowPlayingContent(
        state: NowPlayingState(
            title: "Paranoid Android", artist: "Radiohead", album: "OK Computer", isPlaying: true,
            positionMs: 90_000, durationMs: 386_000,
            queue: [
                .init(id: 1, title: "Paranoid Android", artist: "Radiohead", isCurrent: true),
                .init(id: 2, title: "Hyperballad", artist: "Björk", isCurrent: false),
            ],
            shuffleOn: true, repeatMode: .one
        )
    )
}

#Preview("Nothing playing") {
    NowPlayingContent(state: .idle)
}

#Preview("Queue") {
    NowPlayingQueueList(
        queue: [
            .init(id: 1, title: "Paranoid Android", artist: "Radiohead", isCurrent: true),
            .init(id: 2, title: "Hyperballad", artist: "Björk", isCurrent: false),
        ],
        onSelect: { _ in }
    )
}
