import SwiftUI

/// A cover that had to be fetched fades in over the placeholder; one that was already decoded draws in
/// the first frame with no fade, because a list scrolling back over its rows should not shimmer.
private let artworkArrival = Animation.easeInOut(duration: 0.3)

/// Cover art for a song, album or album artist. `source` says where it may be — :shared's `ArtworkUrls`
/// (`IosAppGraph.artworkUrls`) lists the candidates, none when artwork is set to local-only — and
/// `ArtworkLoader` tries them in order and does the download, the decode at exactly the size drawn, and
/// the caching.
///
/// The frame is passed in **points**, because that times `displayScale` is the pixel size the decode is
/// clamped to; guessing a size before the first request would mint a second cache entry for the same
/// tile. No artwork is not a loading state: nothing found means the placeholder, instead of a spinner
/// that never resolves.
///
/// Call sites go through `RemoteArtwork` (`Components/RemoteArtwork.swift`).
struct ArtworkImage<Placeholder: View>: View {
    let source: ArtworkSource?
    /// The longest edge of the frame this artwork is drawn into, in points.
    let points: CGFloat
    @ViewBuilder let placeholder: () -> Placeholder

    @State private var image: UIImage?

    @Environment(\.displayScale) private var displayScale
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// The longest side in pixels: the decode size and the cache key.
    private var maxPixelSize: Int {
        Int((points * displayScale).rounded(.up))
    }

    /// A change here restarts the load, and `.task(id:)` cancels the old one.
    private var loadId: String {
        "\(source?.cacheKey ?? "")@\(maxPixelSize)"
    }

    var body: some View {
        ZStack {
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .transition(.opacity)
            } else {
                placeholder()
                    .transition(.opacity)
            }
        }
        .task(id: loadId) {
            await load()
        }
        .accessibilityHidden(true)
    }

    private func load() async {
        guard let source, maxPixelSize > 0 else {
            image = nil
            return
        }

        // A cover already decoded at this size is drawn in this frame, rather than flashing the
        // placeholder for a row a list has scrolled back over.
        if let hit = ArtworkLoader.shared.cached(source, maxPixelSize: maxPixelSize) {
            image = hit
            return
        }
        if image != nil { image = nil }

        guard let loaded = await ArtworkLoader.shared.image(for: source, maxPixelSize: maxPixelSize) else { return }
        if Task.isCancelled { return }
        if reduceMotion {
            image = loaded
        } else {
            withAnimation(artworkArrival) { image = loaded }
        }
    }
}

/// The placeholder, until the real cover arrives or in place of one that doesn't exist (#646): a neutral system grey
/// with a subtle symbol at about a third of the tile, the same on every screen whatever its tint. `symbol` says
/// what's missing: `music.note` for a song, `square.stack` for an album, `music.mic` for an artist, `guitars` for a
/// genre, `music.note.list` for a playlist.
struct ArtworkPlaceholder: View {
    var symbol: String = "music.note"

    var body: some View {
        GeometryReader { proxy in
            let glyph = min(proxy.size.width, proxy.size.height) * ArtworkPalette.placeholderGlyphScale
            ZStack {
                ArtworkPalette.placeholderFill
                Image(systemName: symbol)
                    .resizable()
                    .scaledToFit()
                    .fontWeight(.medium)
                    .frame(width: glyph, height: glyph)
                    .foregroundStyle(ArtworkPalette.placeholderGlyph)
            }
        }
        .accessibilityHidden(true)
    }
}
