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
    /// An artist's picture, which is always a circle (`S2Shape.artwork(_:for:)`).
    let isArtist: Bool

    init(id: AnyHashable, cacheKey: String? = nil, isArtist: Bool = false, candidates: @escaping () async throws -> [ArtworkCandidate]) {
        self.id = id
        self.cacheKey = cacheKey ?? "\(id)"
        self.isArtist = isArtist
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

    /// An artist's row, tile or search result: the hero's rule (#823), which `requests(albumArtist:)` resolves from
    /// their library, so the row shows what their page does.
    static func albumArtist(_ albumArtist: AlbumArtist) -> ArtworkSource {
        ArtworkSource(id: albumArtist.stableId, cacheKey: itemKey("artist", albumArtist.stableId, version: albumArtist.artworkVersion), isArtist: true) {
            try await AppGraph.shared.artworkUrls.requests(albumArtist: albumArtist).compactMap(ArtworkCandidate.init)
        }
    }
}

extension ArtworkSource {
    /// An artist page's hero (#781): the shared rule's chain, so the image shown and the tint come from one place.
    static func artistHero(_ hero: ArtistHeroArtwork) -> ArtworkSource {
        let artist = itemKey("artist", hero.artist.stableId, version: hero.artist.artworkVersion)
        let album = hero.fallbackAlbum.map { itemKey("album", $0.stableId, version: $0.artworkVersion) } ?? "none"
        let key = "artistHero:\(artist)|online=\(hero.onlineLookup)|\(album)"
        return ArtworkSource(id: key, cacheKey: key) {
            try await AppGraph.shared.artworkUrls.requests(hero: hero).compactMap(ArtworkCandidate.init)
        }
    }
}

extension ArtworkCandidate {
    /// Kotlin's request as a value; nil for a url Foundation can't parse.
    init?(_ request: ArtworkRequest) {
        guard let url = URL(string: request.url) else { return nil }
        self.init(url: url, authorization: request.authorization, unmeteredOnly: request.unmeteredOnly, headers: request.headers, minimumSize: Int(request.minimumSize))
    }
}

extension View {
    /// Frames artwork to a square tile of `points` and styles it (`artworkStyle`): clipped to `shape` (an `S2Shape`
    /// artwork role, `.artist` for an artist's picture) with the hairline.
    func artworkTile(_ points: CGFloat, shape: S2Shape = .artworkRow) -> some View {
        frame(width: points, height: points)
            .artworkStyle(shape)
    }

    /// Clips artwork to an `S2Shape` and draws a 1 px hairline over it, after Shuttle Podcasts'
    /// `artworkStyle`. The hairline is what keeps a white-cornered cover from bleeding into a white list (and a
    /// black one into a dark list); `Color.primary` at 8% gives near-black in light mode and near-white in dark.
    /// Rows and tiles take this and no shadow; heroes and the player add `artworkShadow` on top.
    func artworkStyle(_ shape: S2Shape = .artworkRow) -> some View {
        clipShape(shape)
            .overlay { shape.strokeBorder(ArtworkHairline.color, lineWidth: ArtworkHairline.width) }
    }
}

/// The artwork edge `artworkStyle` draws.
enum ArtworkHairline {
    static let color = Color.primary.opacity(0.08)
    static let width = Spacing.hairline
}

/// A stable identity for list rows and `RemoteArtwork`'s `id`. Kotlin's group key when it has one: for an album the
/// whole key (name, album artist and identity), so same-named albums by different artists, or of different releases,
/// stay apart. A model without one is told apart by the fields that name it (never a count or duration, which change
/// across syncs), so two nameless entries differ unless they are named the same.
extension AlbumArtist {
    var stableId: String {
        if let key = groupKey.key { return "artist|\(key)" }
        return ["artist", name, friendlyArtistName].map { $0 ?? "" }.joined(separator: "|")
    }
}

extension Album {
    var stableId: String {
        if let groupKey, groupKey.key != nil || groupKey.identity != nil { return "album|\(groupKey.encode())" }
        return ["album", name, albumArtist, friendlyArtistName, year.map { "\($0.intValue)" }].map { $0 ?? "" }.joined(separator: "|")
    }
}
