import Shared
import SwiftUI

/// `ArtworkImage` for a song, album or album artist, looking up its url from :shared's `ArtworkUrls`
/// (`IosAppGraph.artworkUrls`) first. `id` identifies the item across redraws — a group key, or the
/// item's own id — so scrolling a shelf doesn't re-fetch a tile that's already resolved.
struct RemoteArtwork<Placeholder: View>: View {
    let id: AnyHashable
    let points: CGFloat
    let load: () async throws -> String?
    @ViewBuilder let placeholder: () -> Placeholder

    @State private var url: URL?

    var body: some View {
        ArtworkImage(url: url, points: points, placeholder: placeholder)
            .task(id: id) {
                guard let urlString = try? await load() else {
                    url = nil
                    return
                }
                url = URL(string: urlString)
            }
    }
}

extension RemoteArtwork where Placeholder == ArtworkPlaceholder {
    init(id: AnyHashable, points: CGFloat, load: @escaping () async throws -> String?) {
        self.init(id: id, points: points, load: load) { ArtworkPlaceholder() }
    }

    init(_ source: ArtworkSource, points: CGFloat) {
        self.init(id: source.id, points: points, load: source.load)
    }
}

/// Which item's artwork to draw, as a value: its identity plus how to look its url up. Lets a view that takes
/// plain values (the mini player, Now Playing, the queue) be handed artwork without knowing about the graph, and
/// lets a test say "this row has artwork" without Kotlin. Equal by `id` only.
struct ArtworkSource: Equatable {
    let id: AnyHashable
    let load: () async throws -> String?

    static func == (lhs: ArtworkSource, rhs: ArtworkSource) -> Bool { lhs.id == rhs.id }

    static func song(_ song: Song) -> ArtworkSource {
        ArtworkSource(id: song.id) { try await AppGraph.shared.artworkUrls.url(song: song) }
    }

    static func album(_ album: Album) -> ArtworkSource {
        ArtworkSource(id: album.stableId) { try await AppGraph.shared.artworkUrls.url(album: album) }
    }

    static func albumArtist(_ albumArtist: AlbumArtist) -> ArtworkSource {
        ArtworkSource(id: albumArtist.stableId) { try await AppGraph.shared.artworkUrls.url(albumArtist: albumArtist) }
    }
}

extension View {
    /// Frames and clips artwork to a square tile of `points`, rounded by `cornerRadius` (a `Radius` token).
    func artworkTile(_ points: CGFloat, cornerRadius: CGFloat = Radius.small) -> some View {
        frame(width: points, height: points)
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
    }

    /// Frames and clips artwork to a circle of `points` across: an artist's picture.
    func artworkCircle(_ points: CGFloat) -> some View {
        frame(width: points, height: points)
            .clipShape(Circle())
    }
}

/// A stable identity for list rows and `RemoteArtwork`'s `id`: the group key Kotlin computed when
/// present, else the display name, matching Android's own fallback for artists/albums it couldn't group.
extension AlbumArtist {
    var stableId: String { groupKey.key ?? name ?? friendlyArtistName ?? "" }
}

extension Album {
    var stableId: String { groupKey?.key ?? name ?? "" }
}
