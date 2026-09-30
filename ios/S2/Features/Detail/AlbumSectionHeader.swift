import Shared
import SwiftUI

/// The artist screen's sticky header over one album's songs (#631): the album's small cover, its title, "year · N
/// songs" and a chevron that turns as the section unfolds. The thumbnail opens the album and the rest of the header folds and
/// unfolds the section; a long press offers the album's own actions, which VoiceOver reads as named actions.
struct AlbumSectionHeader: View {
    let album: Album
    let songCount: Int
    let isExpanded: Bool
    var onToggle: () -> Void = {}
    var onPlay: () -> Void = {}
    var onShuffle: () -> Void = {}
    var onPlayNext: () -> Void = {}
    var onAddToQueue: () -> Void = {}
    var onOpenAlbum: () -> Void = {}

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @ScaledMetric(relativeTo: .body) private var thumbSize = ArtworkSize.row

    private var title: String { album.name ?? "Unknown Album" }
    private var subtitle: String { eyebrow(album.year.map { String($0.intValue) }, pluralized(songCount, "song")) }

    var body: some View {
        HStack(spacing: 0) {
            Button(action: onOpenAlbum) {
                RemoteArtwork(.album(album), points: thumbSize)
                    .artworkTile(thumbSize)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Open \(title)")
            .accessibilityIdentifier("artistDetail.albumThumbnail")
            Button(action: onToggle) {
                // The leading padding is part of the button, so the gap after the thumbnail toggles instead of being dead space
                HStack(spacing: Spacing.smallMedium) {
                    VStack(alignment: .leading, spacing: Spacing.tiny) {
                        Text(title)
                            .font(.headline)
                            .foregroundStyle(Color.primary)
                            .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 1)
                        Text(subtitle)
                            .font(.subheadline)
                            .foregroundStyle(.s2SecondaryText)
                            .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 1)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    Image(systemName: "chevron.right")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.tint)
                        .rotationEffect(.degrees(isExpanded ? 90 : 0))
                        .accessibilityHidden(true)
                }
                .padding(.leading, Spacing.smallMedium)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(title), \(subtitle)")
            .accessibilityValue(isExpanded ? "Expanded" : "Collapsed")
            .accessibilityHint(isExpanded ? "Hides the album's songs" : "Shows the album's songs")
            .accessibilityAddTraits(.isHeader)
            .accessibilityAction(named: "Play", onPlay)
            .accessibilityAction(named: "Shuffle", onShuffle)
            .accessibilityAction(named: "Play Next", onPlayNext)
            .accessibilityAction(named: "Add to Queue", onAddToQueue)
            .accessibilityAction(named: "Go to Album", onOpenAlbum)
            .accessibilityIdentifier("artistDetail.albumHeader")
        }
        .textCase(nil)
        .contextMenu {
            Button("Play", systemImage: "play.fill", action: onPlay)
            Button("Shuffle", systemImage: "shuffle", action: onShuffle)
            Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward", action: onPlayNext)
            Button("Add to Queue", systemImage: "text.append", action: onAddToQueue)
            Button("Go to Album", systemImage: "square.stack", action: onOpenAlbum)
        }
    }
}

/// The artist screen's sort orders as the Songs header's menu lists them.
extension ArtistSongSortOrder {
    static let menuOrder: [ArtistSongSortOrder] = [.albumNewest, .albumOldest, .albumTitle, .songTitle, .mostPlayed]

    var menuTitle: String {
        switch self {
        case .albumNewest: "Album, Newest First"
        case .albumOldest: "Album, Oldest First"
        case .albumTitle: "Album Title"
        case .songTitle: "Song Title"
        case .mostPlayed: "Most Played"
        }
    }
}
