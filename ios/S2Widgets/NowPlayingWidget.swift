import AppIntents
import SwiftUI
import UIKit
import WidgetKit

/// The current song, its cover and a play/pause button (#758): small and medium on the Home Screen, with Next on
/// medium; rectangular, circular and inline on the Lock Screen. Nothing playing shows a play button, which shuffles
/// the library (`TogglePlaybackIntent`).
struct NowPlayingWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: NowPlayingStore.nowPlayingWidgetKind, provider: NowPlayingProvider()) { entry in
            NowPlayingWidgetView(entry: entry)
        }
        .configurationDisplayName("Now Playing")
        .description("What's playing in Shuttle Music, with play and pause.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular, .accessoryCircular, .accessoryInline])
    }
}

struct NowPlayingEntry: TimelineEntry {
    let date: Date
    /// Nil when nothing is queued.
    let snapshot: NowPlayingSnapshot?
    let artwork: UIImage?

    static let placeholder = NowPlayingEntry(
        date: .now,
        snapshot: NowPlayingSnapshot(itemID: "", title: "Song Title", artist: "Artist", album: nil, isPlaying: false, hasArtwork: false),
        artwork: nil
    )
}

/// One entry, read from the store, kept until the app reloads the widget: the app writes on every change of song or
/// play state, so there's nothing to schedule.
struct NowPlayingProvider: TimelineProvider {
    func placeholder(in context: Context) -> NowPlayingEntry {
        .placeholder
    }

    func getSnapshot(in context: Context, completion: @escaping (NowPlayingEntry) -> Void) {
        completion(context.isPreview && NowPlayingStore().read() == nil ? .placeholder : current())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<NowPlayingEntry>) -> Void) {
        completion(Timeline(entries: [current()], policy: .never))
    }

    private func current() -> NowPlayingEntry {
        let store = NowPlayingStore()
        return NowPlayingEntry(date: .now, snapshot: store.read(), artwork: store.readArtwork().flatMap(UIImage.init(data:)))
    }
}

struct NowPlayingWidgetView: View {
    let entry: NowPlayingEntry
    @Environment(\.widgetFamily) private var family

    private var isPlaying: Bool { entry.snapshot?.isPlaying == true }
    private var title: String { entry.snapshot?.title ?? String(localized: "Not Playing") }
    /// The artist, else the album; the app's name when nothing is playing.
    private var subtitle: String? {
        guard let snapshot = entry.snapshot else { return "Shuttle Music" }
        return snapshot.artist ?? snapshot.album
    }

    var body: some View {
        switch family {
        case .systemMedium: medium
        case .accessoryRectangular: rectangular
        case .accessoryCircular: circular
        case .accessoryInline: inline
        default: small
        }
    }

    private var small: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top) {
                artwork.frame(width: 56, height: 56)
                Spacer(minLength: 0)
                playPauseButton(size: 36)
            }
            Spacer(minLength: 0)
            titles
        }
        .containerBackground(.fill.tertiary, for: .widget)
    }

    private var medium: some View {
        HStack(spacing: 12) {
            artwork.aspectRatio(1, contentMode: .fit)
            VStack(alignment: .leading, spacing: 8) {
                titles
                Spacer(minLength: 0)
                HStack(spacing: 16) {
                    playPauseButton(size: 40)
                    Button(intent: SkipToNextIntent()) {
                        Image(systemName: "forward.fill").font(.title3)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Next Song")
                    .disabled(entry.snapshot == nil)
                }
            }
            Spacer(minLength: 0)
        }
        .containerBackground(.fill.tertiary, for: .widget)
    }

    private var rectangular: some View {
        HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 0) {
                Text(title).font(.headline).widgetAccentable().lineLimit(1)
                if let subtitle { Text(subtitle).font(.caption).lineLimit(1) }
            }
            Spacer(minLength: 0)
            Button(intent: TogglePlaybackIntent()) {
                Image(systemName: isPlaying ? "pause.fill" : "play.fill")
            }
            .buttonStyle(.plain)
            .accessibilityLabel(isPlaying ? "Pause" : "Play")
        }
        .containerBackground(.clear, for: .widget)
    }

    private var circular: some View {
        Button(intent: TogglePlaybackIntent()) {
            ZStack {
                AccessoryWidgetBackground()
                Image(systemName: isPlaying ? "pause.fill" : "play.fill").font(.title2).widgetAccentable()
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(isPlaying ? "Pause" : "Play")
        .containerBackground(.clear, for: .widget)
    }

    private var inline: some View {
        Label(title, systemImage: entry.snapshot == nil ? "music.note" : isPlaying ? "play.fill" : "pause.fill")
            .containerBackground(.clear, for: .widget)
    }

    private var titles: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.headline).lineLimit(2)
            if let subtitle { Text(subtitle).font(.subheadline).foregroundStyle(.secondary).lineLimit(1) }
        }
    }

    @ViewBuilder
    private var artwork: some View {
        if let image = entry.artwork {
            // The cover fills the frame it's given, cropped to it
            Color.clear
                .overlay(Image(uiImage: image).resizable().aspectRatio(contentMode: .fill))
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .accessibilityHidden(true)
        } else {
            RoundedRectangle(cornerRadius: 8)
                .fill(.quaternary)
                .overlay(Image(systemName: "music.note").font(.title2).foregroundStyle(.secondary))
                .accessibilityHidden(true)
        }
    }

    private func playPauseButton(size: CGFloat) -> some View {
        Button(intent: TogglePlaybackIntent()) {
            Image(systemName: isPlaying ? "pause.circle.fill" : "play.circle.fill")
                .resizable()
                .frame(width: size, height: size)
                .widgetAccentable()
        }
        .buttonStyle(.plain)
        .accessibilityLabel(isPlaying ? "Pause" : "Play")
    }
}
