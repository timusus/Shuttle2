import Shared
import SwiftUI

/// `ArtworkImage` for a song, album or album artist, looking up its url from :shared's `ArtworkUrls`
/// (`IosAppGraph.artworkUrls`) first. `id` identifies the item across redraws — a group key, or the
/// item's own id — so scrolling a shelf doesn't re-fetch a tile that's already resolved.
struct RemoteArtwork: View {
    let id: AnyHashable
    let points: CGFloat
    let load: () async throws -> String?

    @State private var url: URL?

    var body: some View {
        ArtworkImage(url: url, points: points)
            .task(id: id) {
                guard let urlString = try? await load() else {
                    url = nil
                    return
                }
                url = URL(string: urlString)
            }
    }
}
