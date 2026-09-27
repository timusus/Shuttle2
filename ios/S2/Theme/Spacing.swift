import CoreGraphics

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

/// Corner radii for artwork and tiles. Podcasts uses these values ad hoc at its call sites; S2 names them so a
/// row, a shelf tile and a hero agree.
enum Radius {
    /// Row artwork (44-56 pt) and the mini player's cover.
    static let small: CGFloat = 6
    /// Shelf tiles and the resume hero's cover.
    static let medium: CGFloat = 8
    /// Hero artwork: detail headers and Now Playing.
    static let large: CGFloat = 12
}

/// The artwork sizes rows and bars draw at, in points (also the decode size `ArtworkImage` requests).
enum ArtworkSize {
    /// A song, playlist, artist or queue row; the mini player.
    static let row: CGFloat = 44
    /// An album row, whose cover carries more of the row.
    static let albumRow: CGFloat = 56
    /// A Home shelf tile.
    static let shelf: CGFloat = 120
    /// A detail screen's hero.
    static let hero: CGFloat = 160
}
