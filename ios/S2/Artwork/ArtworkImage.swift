import SwiftUI

/// A cover that had to be fetched fades in over the placeholder; one that was already decoded draws in
/// the first frame with no fade, because a list scrolling back over its rows should not shimmer.
private let artworkArrival = Animation.easeInOut(duration: 0.3)

/// Cover art for a song, album or album artist. `url` comes from :shared's `ArtworkUrls`
/// (`IosAppGraph.artworkUrls`) — nil when the item has none, or when artwork is set to local-only — and
/// `ArtworkLoader` does the download, the decode at exactly the size drawn, and the caching.
///
/// The frame is passed in **points**, because that times `displayScale` is the pixel size the decode is
/// clamped to; guessing a size before the first request would mint a second cache entry for the same
/// tile. A nil url is not a loading state: nothing to request means the placeholder, at once and for
/// good, instead of a spinner that never resolves.
///
/// Call sites go through `RemoteArtwork`, which looks the url up first (`Components/RemoteArtwork.swift`).
struct ArtworkImage<Placeholder: View>: View {
    let url: URL?
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
        "\(url?.absoluteString ?? "")@\(maxPixelSize)"
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
        guard let url, maxPixelSize > 0 else {
            image = nil
            return
        }

        // A cover already decoded at this size is drawn in this frame, rather than flashing the
        // placeholder for a row a list has scrolled back over.
        if let hit = ArtworkLoader.shared.cached(url, maxPixelSize: maxPixelSize) {
            image = hit
            return
        }
        if image != nil { image = nil }

        guard let loaded = await ArtworkLoader.shared.image(for: url, maxPixelSize: maxPixelSize) else { return }
        if Task.isCancelled { return }
        if reduceMotion {
            image = loaded
        } else {
            withAnimation(artworkArrival) { image = loaded }
        }
    }
}

extension ArtworkImage where Placeholder == ArtworkPlaceholder {
    init(url: URL?, points: CGFloat) {
        self.init(url: url, points: points) { ArtworkPlaceholder() }
    }
}

/// The default placeholder: a neutral tile with a music note, until the real cover arrives or in place
/// of one that doesn't exist.
struct ArtworkPlaceholder: View {
    var body: some View {
        Color(.secondarySystemBackground)
            .overlay {
                Image(systemName: "music.note")
                    .resizable()
                    .scaledToFit()
                    .padding(8)
                    .foregroundStyle(.primary.opacity(0.15))
            }
    }
}
