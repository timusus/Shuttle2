import SwiftUI

/// The spacing scale, Shuttle Podcasts' (`Theme/Spacing.swift`) value for value, which is Android's own fixed
/// tokens, so the apps space the same content the same way.
///
/// The rule: a gap in an S2 surface is a token, never a literal.
enum Spacing {
    /// Divider thickness.
    static let hairline: CGFloat = 1
    /// Minimal padding (icon insets).
    static let tiny: CGFloat = 2
    /// Tight spacing: a title and its subtitle, badge padding.
    static let xsmall: CGFloat = 4
    /// Standard inner padding.
    static let small: CGFloat = 8
    /// Between related elements: artwork and its text, the header-to-content gap.
    static let smallMedium: CGFloat = 12
    /// Standard content padding.
    static let medium: CGFloat = 16
    /// Section separators, generous spacing.
    static let large: CGFloat = 24
    /// Screen-level breathing room above a major block.
    static let xlarge: CGFloat = 32
}

/// Corner radii for artwork, and for any card or surface of the same size class. Corners are continuous
/// everywhere: `artworkStyle(cornerRadius:)` clips and draws the hairline, `artworkTile` just clips.
/// Controls (Play, Shuffle, the scrubber) are capsules, not a radius.
enum ArtworkCorner {
    /// Row artwork (48-56 pt): songs, albums, artists, playlists, the queue, the mini player's cover.
    static let row: CGFloat = 8
    /// Shelf and grid tiles (albums and artists alike), the floating mini player, notices and other small cards.
    static let tile: CGFloat = 16
    /// A detail screen's hero cover, and full-width cards (Home's resume card).
    static let hero: CGFloat = 20
    /// The Now Playing cover.
    static let player: CGFloat = 20
}

/// The artwork sizes rows, tiles and heroes draw at, in points (also the decode size `ArtworkImage` requests).
/// A size that grows on an iPad is a compact / regular pair plus a function of the `LayoutTier`: call the
/// function (`ArtworkSize.shelf(tier)`) so the breakpoint lives here.
enum ArtworkSize {
    /// A song, playlist, genre, artist or queue row; the mini player.
    static let row: CGFloat = 48
    /// An album row, whose cover carries more of the row.
    static let albumRow: CGFloat = 56

    /// A Home shelf tile at compact width.
    static let shelf: CGFloat = 150
    /// A Home shelf tile at regular and wide width.
    static let shelfRegular: CGFloat = 180
    /// An artist tile on a shelf.
    static let artistShelf: CGFloat = 120

    /// The smallest cell of an adaptive grid: `GridItem(.adaptive(minimum: ArtworkSize.gridMinimum))`.
    static let gridMinimum: CGFloat = 160
    /// The smallest Library grid tile at compact width: small enough that two fit beside the letter index on the
    /// narrowest iPhone (`LibraryGrid.minimumTile`).
    static let gridMinimumCompact: CGFloat = 120

    /// A detail screen's hero at compact width.
    static let hero: CGFloat = 240
    /// A detail screen's hero in the two-column regular layout.
    static let heroRegular: CGFloat = 300

    /// The largest the Now Playing cover draws; it shrinks to fit the space it has below that.
    static let playerMaximum: CGFloat = 420

    static func shelf(_ tier: LayoutTier) -> CGFloat {
        tier == .compact ? shelf : shelfRegular
    }

    static func hero(_ tier: LayoutTier) -> CGFloat {
        tier == .compact ? hero : heroRegular
    }
}

/// Shadows. Rows and tiles carry none, only `artworkStyle`'s hairline: a shadow is for the one cover a screen
/// is about. Apply with `.artworkShadow(.hero)`.
struct ArtworkShadow: Equatable {
    let opacity: Double
    let radius: CGFloat
    let y: CGFloat

    /// A detail screen's hero cover.
    static let hero = ArtworkShadow(opacity: 0.22, radius: 18, y: 8)
    /// The Now Playing cover.
    static let player = ArtworkShadow(opacity: 0.3, radius: 24, y: 12)
}

extension View {
    /// A drop shadow from the `ArtworkShadow` scale. Apply after clipping, or the shadow is clipped too.
    func artworkShadow(_ shadow: ArtworkShadow) -> some View {
        self.shadow(color: .black.opacity(shadow.opacity), radius: shadow.radius, x: 0, y: shadow.y)
    }
}

/// Width limits and breakpoints for the regular and wide tiers, after Shuttle Podcasts' `AdaptiveLayout`.
/// Screens read these rather than carrying their own constants.
enum AdaptiveLayout {
    /// Scrolling content (Home, details) never grows wider than this; centre it beyond.
    static let contentMaxWidth: CGFloat = 1000
    /// From this container width a screen goes two-column: Now Playing's cover beside its controls, a detail's
    /// hero beside its tracks.
    static let twoColumnMinWidth: CGFloat = 700
    /// The gutter between grid cells.
    static let gridSpacing: CGFloat = Spacing.medium

    /// The leading and trailing margin of scrolling content.
    static func contentInset(_ tier: LayoutTier) -> CGFloat {
        switch tier {
        case .compact: Spacing.medium
        case .regular: Spacing.large
        case .wide: Spacing.xlarge
        }
    }
}
