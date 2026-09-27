import Shared
import SwiftUI

/// `ArtworkImage` for a song, album or album artist, looking up its url from :shared's `ArtworkUrls`
/// (`IosAppGraph.artworkUrls`) first. `id` identifies the item across redraws — a group key, or the
/// item's own id — so scrolling a shelf doesn't re-fetch a tile that's already resolved.
struct RemoteArtwork<Placeholder: View>: View {
    let id: AnyHashable
    let points: CGFloat
    let load: () async throws -> String?
    @ViewBuilder let placeholder: () -> Placeholder

    @State private var url: URL?

    var body: some View {
        ArtworkImage(url: url, points: points, placeholder: placeholder)
            .task(id: id) {
                guard let urlString = try? await load() else {
                    url = nil
                    return
                }
                url = URL(string: urlString)
            }
    }
}

extension RemoteArtwork where Placeholder == ArtworkPlaceholder {
    init(id: AnyHashable, points: CGFloat, load: @escaping () async throws -> String?) {
        self.init(id: id, points: points, load: load) { ArtworkPlaceholder() }
    }
}

/// A stable identity for list rows and `RemoteArtwork`'s `id`: the group key Kotlin computed when
/// present, else the display name, matching Android's own fallback for artists/albums it couldn't group.
extension AlbumArtist {
    var stableId: String { groupKey.key ?? name ?? friendlyArtistName ?? "" }
}

extension Album {
    var stableId: String { groupKey?.key ?? name ?? "" }
}
