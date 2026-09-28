import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// The Library grid's columns (#624): two on every iPhone in portrait beside the letter index, three or more in
/// landscape, more on an iPad; and an artist tile's corners, the same as an album's.
@MainActor
struct LibraryGridTests {
    @Test(arguments: [320, 375, 393, 430] as [CGFloat])
    func everyIPhoneInPortraitHasTwoColumns(width: CGFloat) {
        #expect(LibraryGrid<EmptyView>.columnCount(width: width, tier: .compact) == 2)
    }

    @Test func aGridWithoutAnIndexStillHasTwoColumnsOnAnIPhone() {
        #expect(LibraryGrid<EmptyView>.columnCount(width: 393, tier: .compact, indexWidth: 0) == 2)
    }

    /// iPhone 16 and 16 Pro Max in landscape: the width inside the notch's safe area.
    @Test(arguments: [(734, LayoutTier.compact), (814, LayoutTier.regular)] as [(CGFloat, LayoutTier)])
    func landscapeIPhonesHaveAtLeastThreeColumns(width: CGFloat, tier: LayoutTier) {
        #expect(LibraryGrid<EmptyView>.columnCount(width: width, tier: tier) >= 3)
    }

    @Test func anIPadScalesUp() {
        // An 11-inch iPad in portrait, then landscape beside the sidebar.
        #expect(LibraryGrid<EmptyView>.columnCount(width: 834, tier: .regular) == 4)
        #expect(LibraryGrid<EmptyView>.columnCount(width: 890, tier: .wide) >= 4)
    }

    @Test func accessibilityTextSizesTakeOneFullWidthColumnOnAnIPhone() {
        #expect(LibraryGrid<EmptyView>.columnCount(width: 393, tier: .compact, accessibilitySize: true) == 1)
    }

    @Test func anArtistTileHasAnAlbumTilesCorners() throws {
        let artist = AlbumArtist(
            name: "Radiohead", artists: ["Radiohead"], albumCount: 2, songCount: 2, playCount: 0,
            groupKey: AlbumArtistGroupKey(key: "radiohead"), mediaProviders: [.jellyfin], artworkVersion: nil
        )
        let tile = LibraryTile(title: "Radiohead", subtitle: nil, artwork: .albumArtist(artist), placeholderSymbol: "music.mic")
        let clip = try tile.inspect().find(ViewType.Color.self).clipShape(RoundedRectangle.self)
        #expect(clip.cornerSize.width == ArtworkCorner.tile)
    }
}
