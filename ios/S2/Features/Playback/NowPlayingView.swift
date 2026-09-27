import Shared
import SwiftUI

/// Now Playing: large title/artist, a scrubber with elapsed/remaining, previous/play-pause/next, and
/// the queue with tap-to-skip. Bound to `PlayerModel` over the Kotlin `IosPlayerController` (#588);
/// the shared `PlayerViewModel` isn't ported yet (phase 4 wave 5,
/// docs/architecture/ios-port/phase-5-ios-app.md).
struct NowPlayingView: View {
    let model: PlayerModel
    @Environment(\.dismiss) private var dismiss

    /// `model` defaults to the same `PlayerModel` `MiniPlayerView` uses (`AppGraph.dependencies.playerModel`),
    /// so the two always show the same state.
    init(
        model: PlayerModel = AppGraph.dependencies.playerModel
    ) {
        self.model = model
    }

    var body: some View {
        NavigationStack {
            NowPlayingContent(
                title: model.title,
                artist: model.artist,
                album: model.album,
                isPlaying: model.isPlaying,
                positionMs: model.positionMs,
                durationMs: model.durationMs,
                queue: model.queue,
                onSeek: model.seek(toMs:),
                onPlayPause: model.togglePlayPause,
                onNext: model.next,
                onPrevious: model.previous,
                onSelectQueueItem: model.play(at:)
            )
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close", systemImage: "chevron.down") { dismiss() }
                }
            }
        }
    }
}

/// The screen's content: plain values in, so it previews and tests (ViewInspector) without Kotlin.
struct NowPlayingContent: View {
    let title: String?
    let artist: String?
    let album: String?
    let isPlaying: Bool
    let positionMs: Int
    let durationMs: Int
    let queue: [PlayerModel.QueueRow]
    let onSeek: (Int) -> Void
    let onPlayPause: () -> Void
    let onNext: () -> Void
    let onPrevious: () -> Void
    let onSelectQueueItem: (Int) -> Void

    @State private var isScrubbing = false
    @State private var scrubMs: Double = 0

    var body: some View {
        if title == nil {
            ContentUnavailableView("Now Playing", systemImage: "play.circle", description: Text("Nothing is playing"))
        } else {
            List {
                Section {
                    VStack(spacing: 16) {
                        Image(systemName: "music.note")
                            .font(.system(size: 64))
                            .foregroundStyle(.secondary)
                            .frame(width: 220, height: 220)
                            .background(.quaternary, in: RoundedRectangle(cornerRadius: 12))
                            .accessibilityHidden(true)

                        VStack(spacing: 4) {
                            Text(title ?? "")
                                .font(.title2.weight(.semibold))
                                .multilineTextAlignment(.center)
                                .lineLimit(2)
                            if artist != nil || album != nil {
                                Text([artist, album].compactMap { $0 }.joined(separator: " · "))
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                                    .multilineTextAlignment(.center)
                                    .lineLimit(1)
                            }
                        }

                        scrubber

                        HStack(spacing: 40) {
                            Button(action: onPrevious) {
                                Image(systemName: "backward.fill").font(.title2)
                            }
                            .accessibilityLabel("Previous")

                            Button(action: onPlayPause) {
                                Image(systemName: isPlaying ? "pause.circle.fill" : "play.circle.fill")
                                    .font(.system(size: 56))
                            }
                            .accessibilityLabel(isPlaying ? "Pause" : "Play")

                            Button(action: onNext) {
                                Image(systemName: "forward.fill").font(.title2)
                            }
                            .accessibilityLabel("Next")
                        }
                        .padding(.top, 4)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                }
                .listRowSeparator(.hidden)
                .listRowBackground(Color.clear)

                if !queue.isEmpty {
                    Section("Up Next") {
                        ForEach(Array(queue.enumerated()), id: \.element.id) { index, item in
                            Button {
                                onSelectQueueItem(index)
                            } label: {
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(item.title)
                                            .foregroundStyle(item.isCurrent ? Color.accentColor : .primary)
                                        if let artist = item.artist {
                                            Text(artist)
                                                .font(.caption)
                                                .foregroundStyle(.secondary)
                                        }
                                    }
                                    Spacer()
                                    if item.isCurrent {
                                        Image(systemName: "speaker.wave.2.fill")
                                            .foregroundStyle(.secondary)
                                            .accessibilityHidden(true)
                                    }
                                }
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(item.isCurrent ? "\(item.title), now playing" : item.title)
                        }
                    }
                }
            }
            .listStyle(.plain)
        }
    }

    @ViewBuilder
    private var scrubber: some View {
        let duration = max(Double(durationMs), 1)
        let current = isScrubbing ? scrubMs : Double(positionMs)
        VStack(spacing: 4) {
            Slider(
                value: Binding(
                    get: { current },
                    set: { newValue in
                        isScrubbing = true
                        scrubMs = newValue
                    }
                ),
                in: 0...duration,
                onEditingChanged: { editing in
                    if !editing {
                        onSeek(Int(scrubMs))
                        isScrubbing = false
                    }
                }
            )
            .accessibilityLabel("Playback position")
            .accessibilityValue("\(formatted(ms: Int(current))) of \(formatted(ms: durationMs))")

            HStack {
                Text(formatted(ms: Int(current)))
                Spacer()
                Text("-" + formatted(ms: max(0, durationMs - Int(current))))
            }
            .font(.caption)
            .foregroundStyle(.secondary)
            .monospacedDigit()
        }
        .padding(.horizontal, 8)
    }

    private func formatted(ms: Int) -> String {
        let totalSeconds = max(0, ms) / 1000
        return String(format: "%d:%02d", totalSeconds / 60, totalSeconds % 60)
    }
}

#Preview("Playing") {
    NowPlayingContent(
        title: "Paranoid Android",
        artist: "Radiohead",
        album: "OK Computer",
        isPlaying: true,
        positionMs: 90_000,
        durationMs: 386_000,
        queue: [
            .init(id: 1, title: "Paranoid Android", artist: "Radiohead", isCurrent: true),
            .init(id: 2, title: "Hyperballad", artist: "Björk", isCurrent: false),
        ],
        onSeek: { _ in },
        onPlayPause: {},
        onNext: {},
        onPrevious: {},
        onSelectQueueItem: { _ in }
    )
}

#Preview("Nothing playing") {
    NowPlayingContent(
        title: nil, artist: nil, album: nil, isPlaying: false, positionMs: 0, durationMs: 0,
        queue: [], onSeek: { _ in }, onPlayPause: {}, onNext: {}, onPrevious: {},
        onSelectQueueItem: { _ in }
    )
}
