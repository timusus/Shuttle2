import Foundation
import Network
import Shared

/// Which of Home's items can play offline, by the player's own rule (`SongStreamResolver`); kept here because downloads are per song and iOS-only.
struct CarPlayOfflineIndex {
    private let playable: [Song]
    private let albums: Set<AlbumGroupKey>
    private let artists: Set<AlbumArtistGroupKey>
    private let genres: Set<String>

    init(songs: [Song], downloads: [String: OfflineDownload]) {
        let downloaded = Set(downloads.compactMap { $0.value.state == .completed ? $0.key : nil })
        let playable = songs.filter { Self.isPlayable($0, downloaded: downloaded) }
        self.playable = playable
        albums = Set(playable.map(\.albumGroupKey))
        // An artist plays the songs they're by (`isByArtist`): each album artist of the song's album, and each it credits
        artists = Set(playable.flatMap { $0.albumArtistKeys + $0.artistCredits.map(\.groupKey) })
        genres = Set(playable.flatMap(\.genres))
    }

    /// `playablePlaylists` is the ids of the playlists with a song that plays offline (`ObservePlayablePlaylists`), nil
    /// until it's known, when a playlist is shown rather than hidden.
    func isPlayable(_ item: HomeItem, playablePlaylists: Set<Int64>?) -> Bool {
        switch onEnum(of: item) {
        case .albumItem(let it):
            it.album.mediaProviders.contains { !$0.remote } || it.album.groupKey.map(albums.contains) == true
        case .artistItem(let it):
            it.albumArtist.mediaProviders.contains { !$0.remote } || artists.contains(it.albumArtist.groupKey)
        case .genreItem(let it):
            genres.contains(it.genre.name)
        case .smartPlaylistItem(let it):
            playable.contains { it.smartPlaylistId.songQuery.predicate($0).boolValue }
        case .playlistItem(let it):
            playablePlaylists?.contains(it.playlist.id) ?? true
        }
    }

    private static func isPlayable(_ song: Song, downloaded: Set<String>) -> Bool {
        !song.mediaProvider.remote || downloaded.contains(song.path)
    }
}

/// Whether there's a network path, from `NWPathMonitor`, reported on the main actor as it changes. Until the first
/// update it reports online, as `NetworkPathMonitor` does, so nothing is hidden at launch.
@MainActor
final class CarPlayNetworkMonitor {
    private let monitor = NWPathMonitor()
    private(set) var isOnline = true

    init(onChange: @escaping @MainActor @Sendable (Bool) -> Void) {
        monitor.pathUpdateHandler = { [weak self] path in
            let online = path.status == .satisfied
            Task { @MainActor in
                guard let self, self.isOnline != online else { return }
                self.isOnline = online
                onChange(online)
            }
        }
        monitor.start(queue: DispatchQueue(label: "com.simplecityapps.shuttle.carplay-network"))
    }

    func cancel() {
        monitor.cancel()
    }
}
