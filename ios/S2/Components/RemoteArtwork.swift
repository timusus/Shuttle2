import S2Playback
import Shared
import SwiftUI

/// A song's, album's or album artist's artwork (`ArtworkImage`), drawn from an `ArtworkSource`, with a
/// placeholder that says what is missing (a symbol for an artist, an album).
struct RemoteArtwork<Placeholder: View>: View {
    let source: ArtworkSource
    let points: CGFloat
    @ViewBuilder let placeholder: () -> Placeholder

    init(_ source: ArtworkSource, points: CGFloat, @ViewBuilder placeholder: @escaping () -> Placeholder) {
        self.source = source
        self.points = points
        self.placeholder = placeholder
    }

    var body: some View {
        ArtworkImage(source: source, points: points, placeholder: placeholder)
    }
}

extension RemoteArtwork where Placeholder == ArtworkPlaceholder {
    init(_ source: ArtworkSource, points: CGFloat) {
        self.init(source, points: points) { ArtworkPlaceholder() }
    }
}

extension View {
    /// Frames artwork to a square tile of `points` and styles it (`artworkStyle`): clipped to `shape` (an `S2Shape`
    /// artwork role, `.artist` for an artist's picture) with the hairline.
    func artworkTile(_ points: CGFloat, shape: S2Shape = .artworkRow) -> some View {
        frame(width: points, height: points)
            .artworkStyle(shape)
    }

    /// Clips artwork to an `S2Shape` and draws a 1 px hairline over it, after Shuttle Podcasts'
    /// `artworkStyle`. The hairline is what keeps a white-cornered cover from bleeding into a white list (and a
    /// black one into a dark list); `Color.primary` at 8% gives near-black in light mode and near-white in dark.
    /// Rows and tiles take this and no shadow; heroes and the player add `artworkShadow` on top.
    func artworkStyle(_ shape: S2Shape = .artworkRow) -> some View {
        clipShape(shape)
            .overlay { shape.strokeBorder(ArtworkHairline.color, lineWidth: ArtworkHairline.width) }
    }
}

/// The artwork edge `artworkStyle` draws.
enum ArtworkHairline {
    static let color = Color.primary.opacity(0.08)
    static let width = Spacing.hairline
}
