import Shared
import SwiftUI

/// The Library tab. For now it lists `IosAppGraph.librarySongs`, an in-memory Kotlin `StateFlow`,
/// observed through SKIE's `Observing`: the end-to-end proof that Kotlin state reaches SwiftUI.
struct LibraryView: View {
    let songs: SkieSwiftStateFlow<[Song]>

    init(songs: SkieSwiftStateFlow<[Song]> = AppGraph.shared.librarySongs) {
        self.songs = songs
    }

    var body: some View {
        Observing(songs) { songs in
            SongList(songs: songs)
        }
        .navigationTitle(AppTab.library.title)
    }
}

/// A plain list of songs: title over artist and album.
struct SongList: View {
    let songs: [Song]

    var body: some View {
        List(songs, id: \.id) { song in
            VStack(alignment: .leading, spacing: 2) {
                Text(song.name ?? "Unknown")
                    .font(.body)
                Text([song.friendlyArtistName, song.album].compactMap { $0 }.joined(separator: " · "))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .listStyle(.plain)
    }
}
