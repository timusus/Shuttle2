import Foundation
import Testing
@testable import S2

/// `Route.cacheKey` (the `ViewModelCache` key each route resolves to) and JSON round-tripping (what
/// `Navigator.StoredPath`/`StoredCategoryPaths` lean on for `@SceneStorage`).
struct RouteTests {
    @Test func cacheKeysAreStableAndDistinctPerCase() {
        #expect(Route.libraryCategory(.songs).cacheKey == "libraryCategory:songs")
        #expect(Route.album(albumKey: "a1", albumArtistKey: "ar1").cacheKey == "album:a1|ar1")
        #expect(Route.album(albumKey: nil, albumArtistKey: nil).cacheKey == "album:|")
        #expect(Route.albumArtist(albumArtistKey: "ar1").cacheKey == "albumArtist:ar1")
        #expect(Route.genre(name: "Rock").cacheKey == "genre:Rock")
        #expect(Route.playlist(id: 42).cacheKey == "playlist:42")
        #expect(Route.smartPlaylist(id: "recently-added").cacheKey == "smartPlaylist:recently-added")
    }

    @Test func encodesAndDecodesThroughJSON() throws {
        let routes: [Route] = [
            .libraryCategory(.albums),
            .album(albumKey: "a1", albumArtistKey: nil),
            .genre(name: "Jazz"),
            .playlist(id: 7),
            .smartPlaylist(id: "history"),
        ]
        let data = try JSONEncoder().encode(routes)
        let decoded = try JSONDecoder().decode([Route].self, from: data)
        #expect(decoded == routes)
    }
}
