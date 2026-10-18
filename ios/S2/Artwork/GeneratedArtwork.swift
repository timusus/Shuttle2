import SwiftUI

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
