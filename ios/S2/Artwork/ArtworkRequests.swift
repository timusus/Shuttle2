import Shared

/// Where an item's artwork may be, as artwork code sees it: the composition root binds Kotlin's `ArtworkUrls` to
/// `ArtworkLoader`, so nothing that draws artwork reaches for the graph.
protocol ArtworkRequests: Sendable {
    func requests(song: Song) async throws -> [ArtworkRequest]
    func requests(album: Album) async throws -> [ArtworkRequest]
    func requests(albumArtist: AlbumArtist) async throws -> [ArtworkRequest]
    func requests(hero: ArtistHeroArtwork) async throws -> [ArtworkRequest]
}

/// What an unbound loader (tests, previews) looks up with: nothing, so only sources with their own candidates draw.
struct NoArtworkRequests: ArtworkRequests {
    func requests(song: Song) async throws -> [ArtworkRequest] { [] }
    func requests(album: Album) async throws -> [ArtworkRequest] { [] }
    func requests(albumArtist: AlbumArtist) async throws -> [ArtworkRequest] { [] }
    func requests(hero: ArtistHeroArtwork) async throws -> [ArtworkRequest] { [] }
}

// Kotlin's lookups are suspend functions that only read; the graph holds the one instance for the app's lifetime.
extension ArtworkUrls: ArtworkRequests, @unchecked @retroactive Sendable {}
