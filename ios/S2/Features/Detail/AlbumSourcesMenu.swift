import Shared
import SwiftUI

extension AlbumSources {
    /// The album's sources, or nil unless merging is on and the album has songs in two or more of them.
    static func choices(for songs: [Song], context: CopyContext?) -> AlbumSources? {
        guard let context, context.mergeCopies else { return nil }
        let sources = AlbumSourcesKt.albumSources(albumSongs: songs, primaryAlbums: context.preferences.primaryAlbums)
        return sources.hasChoice ? sources : nil
    }

    /// "2 Sources", for the album header's meta line.
    var headerText: String { "\(sourceCount) Sources" }
}

extension AlbumSource {
    /// "10 of 12 songs".
    func songsText(of total: Int32) -> String { "\(songCount) of \(total) songs" }
}

/// The album's More menu entry "Primary Source": Automatic first, then each source, a checkmark on the current choice.
struct PrimarySourceMenu: View {
    let sources: AlbumSources
    /// nil is Automatic.
    let onSelect: (MediaProviderType?) -> Void

    var body: some View {
        Menu {
            Button { onSelect(nil) } label: {
                Text("Automatic")
                if sources.automatic { Image(systemName: "checkmark") }
            }
            ForEach(sources.sources, id: \.provider) { source in
                Button { onSelect(source.provider) } label: {
                    Text(source.provider.title)
                    Text(source.songsText(of: sources.songCount))
                    if source.primary { Image(systemName: "checkmark") }
                }
            }
        } label: {
            Label("Primary Source", systemImage: "externaldrive.connected.to.line.below")
        }
    }
}
