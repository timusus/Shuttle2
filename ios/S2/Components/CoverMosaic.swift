import Shared
import SwiftUI

/// A playlist's or genre's artwork, which has none of its own (#643, as Android's `CoverMosaic`): a 2x2 mosaic of
/// four of its songs' album covers, each a rounded square `Spacing.tiny` from the next; the one cover when there are
/// fewer than four; its `GeneratedArtwork` with none, or until they load. It fills the square it's given and draws
/// its own corners and hairline (`cornerRadius` for the whole, half that for a mosaic's covers), so don't add
/// `artworkStyle` around it.
struct CoverMosaic: View {
    let covers: [ArtworkSource]
    /// The `GeneratedArtwork`'s seed and glyph: the item's name and kind.
    let seed: String
    let symbol: String
    var cornerRadius: CGFloat = ArtworkCorner.row

    /// How many covers make a mosaic (Android's `MOSAIC_COVERS`, the shared `ObservePlaylistCovers.CoverCount`).
    static let count = 4

    /// Whether it draws the 2x2 mosaic, rather than one cover or the generated artwork.
    var isMosaic: Bool { covers.count >= Self.count }

    var body: some View {
        GeometryReader { proxy in
            content(side: min(proxy.size.width, proxy.size.height))
                .frame(width: proxy.size.width, height: proxy.size.height)
        }
        .aspectRatio(1, contentMode: .fit)
        .accessibilityHidden(true)
    }

    @ViewBuilder
    private func content(side: CGFloat) -> some View {
        if isMosaic {
            let cell = (side - Spacing.tiny) / 2
            Grid(horizontalSpacing: Spacing.tiny, verticalSpacing: Spacing.tiny) {
                ForEach(0 ..< 2, id: \.self) { row in
                    GridRow {
                        ForEach(covers[row * 2 ..< row * 2 + 2], id: \.id) { cover in
                            RemoteArtwork(cover, points: cell) { ArtworkPlaceholder(symbol: "square.stack") }
                                .artworkTile(cell, cornerRadius: cornerRadius / 2)
                        }
                    }
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        } else if let cover = covers.first {
            RemoteArtwork(cover, points: side) { GeneratedArtwork(seed: seed, symbol: symbol) }
                .artworkTile(side, cornerRadius: cornerRadius)
        } else {
            GeneratedArtwork(seed: seed, symbol: symbol)
                .artworkTile(side, cornerRadius: cornerRadius)
        }
    }
}

extension CoverMosaic {
    /// A genre's artwork from its cover songs (`GenreListViewModel.covers`, `HomeUiState.covers`).
    static func genre(_ name: String, covers: [Song], cornerRadius: CGFloat = ArtworkCorner.row) -> CoverMosaic {
        CoverMosaic(covers: covers.map(ArtworkSource.song), seed: name, symbol: GeneratedArtwork.genreSymbol, cornerRadius: cornerRadius)
    }

    /// A playlist's artwork from its cover songs (`PlaylistListUiState.covers`, `HomeUiState.covers`).
    static func playlist(_ name: String, covers: [Song], cornerRadius: CGFloat = ArtworkCorner.row) -> CoverMosaic {
        CoverMosaic(covers: covers.map(ArtworkSource.song), seed: name, symbol: GeneratedArtwork.playlistSymbol, cornerRadius: cornerRadius)
    }
}

/// Artwork for an item that has none of its own (#646): one of a few muted tones, picked by the item's name so a
/// genre or playlist keeps its tone from launch to launch, screen to screen and platform to platform (Android's
/// `GeneratedArtwork`), under a small glyph for its kind. The title under the tile says which one it is.
struct GeneratedArtwork: View {
    let seed: String
    let symbol: String

    /// A genre's glyph; every genre shares it, as on Android.
    static let genreSymbol = "music.quarternote.3"
    /// A playlist's glyph.
    static let playlistSymbol = "music.note.list"

    var body: some View {
        GeometryReader { proxy in
            let glyph = min(proxy.size.width, proxy.size.height) * ArtworkPalette.generatedGlyphScale
            ZStack {
                ArtworkPalette.tone(seed)
                Image(systemName: symbol)
                    .resizable()
                    .scaledToFit()
                    .fontWeight(.medium)
                    .frame(width: glyph, height: glyph)
                    .foregroundStyle(ArtworkPalette.generatedGlyph)
            }
        }
        .accessibilityHidden(true)
    }
}

/// A built-in auto playlist's tile (Favourites, Recently Added ...): the accent-tinted glyph on a subtle
/// rounded-square fill, the same in light and dark, in place of a tone-seeded `GeneratedArtwork`.
struct AutoPlaylistArtwork: View {
    let symbol: String
    var cornerRadius: CGFloat = ArtworkCorner.row

    var body: some View {
        GeometryReader { proxy in
            let glyph = min(proxy.size.width, proxy.size.height) * ArtworkPalette.placeholderGlyphScale
            ZStack {
                Color(.secondarySystemFill)
                Image(systemName: symbol)
                    .resizable()
                    .scaledToFit()
                    .fontWeight(.medium)
                    .frame(width: glyph, height: glyph)
                    .foregroundStyle(.tint)
            }
        }
        .accessibilityHidden(true)
    }
}
