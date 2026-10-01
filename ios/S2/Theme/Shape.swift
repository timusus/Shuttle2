import SwiftUI

/// The shape scale (docs/design/ios-design-language.md §Shape). Corners are continuous everywhere; artists are
/// circles. `artworkStyle(_:)` clips to a shape and draws the hairline, `artworkTile(_:shape:)` frames a square
/// first; a surface that isn't artwork takes the shape directly (`.background(.s2SurfaceContainer, in: S2Shape.card)`).
struct S2Shape: InsettableShape, Equatable {
    enum Kind: Equatable {
        case rounded(CGFloat)
        case circle
        case capsule
    }

    let kind: Kind
    var insetAmount: CGFloat = 0

    init(_ kind: Kind) {
        self.kind = kind
    }

    func path(in rect: CGRect) -> Path {
        let inset = rect.insetBy(dx: insetAmount, dy: insetAmount)
        switch kind {
        case .rounded(let radius):
            return RoundedRectangle(cornerRadius: max(radius - insetAmount, 0), style: .continuous).path(in: inset)
        case .circle:
            return Circle().path(in: inset)
        case .capsule:
            return Capsule(style: .continuous).path(in: inset)
        }
    }

    func inset(by amount: CGFloat) -> S2Shape {
        var shape = self
        shape.insetAmount += amount
        return shape
    }

    /// The corner radius, nil for a circle or capsule.
    var cornerRadius: CGFloat? {
        if case .rounded(let radius) = kind { radius } else { nil }
    }

    // MARK: Artwork

    /// Row artwork (48-56 pt): songs, albums, playlists, the queue, the mini player's cover.
    static let artworkRow = S2Shape(.rounded(8))
    /// Shelf and grid tiles.
    static let artworkTile = S2Shape(.rounded(16))
    /// A detail screen's hero cover.
    static let artworkHero = S2Shape(.rounded(20))
    /// The Now Playing cover.
    static let artworkPlayer = S2Shape(.rounded(20))
    /// Every artist picture, at every size: rows, tiles, shelves, the detail hero, placeholders.
    static let artist = S2Shape(.circle)

    /// `role` for artwork from `source`, or `.artist` when it's an artist's picture: the one place the artist rule
    /// lives, so a row or tile given an artist's source draws a circle without being told.
    static func artwork(_ role: S2Shape, for source: ArtworkSource?) -> S2Shape {
        source?.isArtist == true ? .artist : role
    }

    // MARK: Surfaces and controls

    /// Cards, notices, the floating mini player, full-width cards (Home's resume card).
    static let card = S2Shape(.rounded(16))
    /// A small control or icon container: a text field, a source card's icon, a segmented choice.
    static let control = S2Shape(.rounded(10))
    /// Prominent buttons (Play, Shuffle), chips, the scrubber and the player's bottom bar.
    static let capsule = S2Shape(.capsule)
}
