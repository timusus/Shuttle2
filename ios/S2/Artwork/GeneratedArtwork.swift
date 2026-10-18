import SwiftUI

/// The tone is hashed from the name so an item keeps its colour across launches, screens and platforms.
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
