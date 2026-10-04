import Shared
import Testing
@testable import S2

/// The stack's one zoom source (`ZoomSourceSelection`, #698): a tap makes its tile the only source for the route it
/// opens, across every screen in the stack, and the next tap anywhere replaces it.
@MainActor
struct MotionZoomSourceTests {
    private let home = "root|home"
    private let albumX = Route.album(albumKey: "x", albumArtistKey: "a", albumIdentity: nil).cacheKey
    private let albumY = Route.album(albumKey: "y", albumArtistKey: "a", albumIdentity: nil).cacheKey
    private let artist = Route.albumArtist(albumArtistKey: "a").cacheKey

    @Test func noTileIsASourceBeforeATap() {
        let selection = ZoomSourceSelection()
        #expect(selection.activeKey == nil)
        #expect(selection.sourceID(albumX, screen: home, tile: "recentlyPlayed|x") != albumX)
    }

    @Test func theTappedTileIsTheOnlySourceOnItsScreen() {
        let selection = ZoomSourceSelection()
        selection.select(screen: home, tile: "recentlyPlayed|x")
        #expect(selection.sourceID(albumX, screen: home, tile: "recentlyPlayed|x") == albumX)
        // The same album in another section isn't.
        #expect(selection.sourceID(albumX, screen: home, tile: "mostPlayed|x") != albumX)
    }

    @Test func theSameTileKeyOnAnotherScreenIsNotTheSource() {
        let selection = ZoomSourceSelection()
        selection.select(screen: artist, tile: "detailShelf|y")
        #expect(selection.sourceID(albumY, screen: artist, tile: "detailShelf|y") == albumY)
        // Album X's "More by" shelf has the same tile key for Y; the screen keeps them apart.
        #expect(selection.sourceID(albumY, screen: albumX, tile: "detailShelf|y") != albumY)
    }

    @Test func aTapOnAPushedScreenReplacesHomesSource() {
        // Home → album X → its artist → X's tile on the artist's shelf: Home never comes back in between, and its
        // tile for X stops being a source the moment the artist's is tapped.
        let selection = ZoomSourceSelection()
        selection.select(screen: home, tile: "recentlyPlayed|x")
        selection.select(screen: artist, tile: "detailShelf|x")
        #expect(selection.sourceID(albumX, screen: home, tile: "recentlyPlayed|x") != albumX)
        #expect(selection.sourceID(albumX, screen: artist, tile: "detailShelf|x") == albumX)
    }

    @Test func aNewerTapOnTheSameScreenReplacesTheOlder() {
        let selection = ZoomSourceSelection()
        selection.select(screen: home, tile: "recentlyPlayed|x")
        selection.select(screen: home, tile: "mostPlayed|x")
        #expect(selection.sourceID(albumX, screen: home, tile: "recentlyPlayed|x") != albumX)
        #expect(selection.sourceID(albumX, screen: home, tile: "mostPlayed|x") == albumX)
    }

    @Test func tilesKeepIdsOfTheirOwnThatNeverCollide() {
        let selection = ZoomSourceSelection()
        let onHome = selection.sourceID(albumX, screen: home, tile: "detailShelf|x")
        let onArtist = selection.sourceID(albumX, screen: artist, tile: "detailShelf|x")
        #expect(onHome != onArtist)
    }

    @Test func zoomTilesSelectsUnderItsScreen() {
        let selection = ZoomSourceSelection()
        ZoomTiles(selection: selection, screen: artist).select("detailShelf|x")
        #expect(selection.activeKey == ZoomTile.key(screen: artist, tile: "detailShelf|x"))
    }
}
