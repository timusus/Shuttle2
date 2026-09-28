import SwiftUI
import Testing
@testable import S2

/// The Library grid's columns (#624): two on every iPhone in portrait beside the letter index, three or more in
/// landscape, more on an iPad; and the artist picture's shape.
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

    @Test func theArtistShapeIsRounderThanAnAlbumTileButNotACircle() {
        let side: CGFloat = 160
        let radius = side * ArtworkCorner.artistFraction
        #expect(radius > ArtworkCorner.tile)
        #expect(radius < side / 2)
        // A continuous-corner square: the midpoints of its edges are on it, its corners are cut.
        let path = ArtistArtworkShape().path(in: CGRect(x: 0, y: 0, width: side, height: side))
        #expect(path.contains(CGPoint(x: side / 2, y: 1)))
        #expect(!path.contains(CGPoint(x: 1, y: 1)))
        #expect(path.contains(CGPoint(x: side * 0.15, y: side * 0.15)))
    }
}
