import Shared
import SwiftUI

/// A song's, album's or album artist's artwork (`ArtworkImage`), drawn from an `ArtworkSource`, with a
/// placeholder that says what is missing (a symbol for an artist, an album).
struct RemoteArtwork<Placeholder: View>: View {
    let source: ArtworkSource
    let points: CGFloat
    @ViewBuilder let placeholder: () -> Placeholder

    init(_ source: ArtworkSource, points: CGFloat, @ViewBuilder placeholder: @escaping () -> Placeholder) {
        self.source = source
        self.points = points
        self.placeholder = placeholder
    }

    var body: some View {
        ArtworkImage(source: source, points: points, placeholder: placeholder)
    }
}

extension RemoteArtwork where Placeholder == ArtworkPlaceholder {
    init(_ source: ArtworkSource, points: CGFloat) {
        self.init(source, points: points) { ArtworkPlaceholder() }
    }
}

/// Which item's artwork to draw, as a value: its identity plus where its artwork may be. Lets a view that takes
/// plain values (the mini player, Now Playing, the queue) be handed artwork without knowing about the graph, and
/// lets a test say "this row has artwork" without Kotlin. Equal by `id` only.
///
/// `candidates` comes from :shared's `ArtworkUrls` (`IosAppGraph.artworkUrls`): the media server's image, then the
/// S2 artwork API's, which `ArtworkLoader` tries in turn. `cacheKey` names the item in `ArtworkLoader`'s memory
/// cache, so a tile seen before draws at once without asking the server again; it carries the item's kind, so an
/// album and an artist that share a name don't share a cover, and the provider's artwork version, so a refreshed cover
/// misses the cache rather than staying stale.
struct ArtworkSource: Equatable {
    let id: AnyHashable
    let cacheKey: String
    let candidates: () async throws -> [ArtworkCandidate]

    init(id: AnyHashable, cacheKey: String? = nil, candidates: @escaping () async throws -> [ArtworkCandidate]) {
        self.id = id
        self.cacheKey = cacheKey ?? "\(id)"
        self.candidates = candidates
    }

    static func == (lhs: ArtworkSource, rhs: ArtworkSource) -> Bool { lhs.id == rhs.id }

    /// An item's cache key: its kind and identity, plus the provider's artwork version when there is one, so the
    /// key changes exactly when the artwork does (Android's `artworkCacheKey`, `ArtworkKeys.kt`).
    static func itemKey(_ kind: String, _ id: String, version: String?) -> String {
        let key = "\(kind):\(id)"
        guard let version else { return key }
        return "\(key)_\(version)"
    }

    static func song(_ song: Song) -> ArtworkSource {
        ArtworkSource(id: song.id, cacheKey: itemKey("song", "\(song.id)", version: song.artworkVersion)) {
            try await AppGraph.shared.artworkUrls.requests(song: song).compactMap(ArtworkCandidate.init)
        }
    }

    static func album(_ album: Album) -> ArtworkSource {
        ArtworkSource(id: album.stableId, cacheKey: itemKey("album", album.stableId, version: album.artworkVersion)) {
            try await AppGraph.shared.artworkUrls.requests(album: album).compactMap(ArtworkCandidate.init)
        }
    }

    static func albumArtist(_ albumArtist: AlbumArtist) -> ArtworkSource {
        ArtworkSource(id: albumArtist.stableId, cacheKey: itemKey("artist", albumArtist.stableId, version: albumArtist.artworkVersion)) {
            try await AppGraph.shared.artworkUrls.requests(albumArtist: albumArtist).compactMap(ArtworkCandidate.init)
        }
    }
}

extension ArtworkCandidate {
    /// Kotlin's request as a value; nil for a url Foundation can't parse.
    init?(_ request: ArtworkRequest) {
        guard let url = URL(string: request.url) else { return nil }
        self.init(url: url, authorization: request.authorization, unmeteredOnly: request.unmeteredOnly)
    }
}

extension View {
    /// Frames artwork to a square tile of `points` and styles it (`artworkStyle`): continuous corners of
    /// `cornerRadius` (an `ArtworkCorner` token) and the hairline.
    func artworkTile(_ points: CGFloat, cornerRadius: CGFloat = ArtworkCorner.row) -> some View {
        frame(width: points, height: points)
            .artworkStyle(cornerRadius: cornerRadius)
    }

    /// Frames and clips artwork to a circle of `points` across, with the hairline: an artist's picture.
    func artworkCircle(_ points: CGFloat) -> some View {
        frame(width: points, height: points)
            .clipShape(Circle())
            .overlay { Circle().strokeBorder(ArtworkHairline.color, lineWidth: ArtworkHairline.width) }
    }

    /// Clips artwork to S2's continuous corner and draws a 1 px hairline over it, after Shuttle Podcasts'
    /// `artworkStyle`. The hairline is what keeps a white-cornered cover from bleeding into a white list (and a
    /// black one into a dark list); `Color.primary` at 8% gives near-black in light mode and near-white in dark.
    /// Rows and tiles take this and no shadow; heroes and the player add `artworkShadow` on top.
    func artworkStyle(cornerRadius: CGFloat = ArtworkCorner.row) -> some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        return clipShape(shape)
            .overlay { shape.strokeBorder(ArtworkHairline.color, lineWidth: ArtworkHairline.width) }
    }
}

/// The artwork edge `artworkStyle` and `artworkCircle` draw.
enum ArtworkHairline {
    static let color = Color.primary.opacity(0.08)
    static let width = Spacing.hairline
}

/// A stable identity for list rows and `RemoteArtwork`'s `id`: the group key Kotlin computed when
/// present, else the display name, matching Android's own fallback for artists/albums it couldn't group.
extension AlbumArtist {
    var stableId: String { groupKey.key ?? name ?? friendlyArtistName ?? "" }
}

extension Album {
    var stableId: String { groupKey?.key ?? name ?? "" }
}
