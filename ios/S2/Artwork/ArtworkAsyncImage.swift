import SwiftUI

/// `ArtworkImage` for a row whose url isn't known synchronously: an album artist or a playlist's first cover
/// song, both a suspend `ArtworkUrls` call away. Resolves once per row appearance, then hands the result to
/// `ArtworkImage`, which does the actual fetch/decode/cache.
struct ArtworkAsyncImage<Placeholder: View>: View {
    let load: () async throws -> String?
    let points: CGFloat
    @ViewBuilder let placeholder: () -> Placeholder

    @State private var url: URL?

    var body: some View {
        ArtworkImage(url: url, points: points, placeholder: placeholder)
            .task {
                guard let urlString = try? await load() else { return }
                url = URL(string: urlString)
            }
    }
}

extension ArtworkAsyncImage where Placeholder == ArtworkPlaceholder {
    init(load: @escaping () async throws -> String?, points: CGFloat) {
        self.init(load: load, points: points) { ArtworkPlaceholder() }
    }
}
