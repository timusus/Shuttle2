import Foundation
import Shared

/// What plays with no network, for Home: the network path, the downloads, the index built from them and the
/// playlists with a song that plays offline. `onChange` is called when Home should redraw.
@MainActor
final class CarPlayOfflineState {
    private let graph: IosAppGraph
    private let onChange: () -> Void
    private var network: CarPlayNetworkMonitor?
    private var downloads: [String: OfflineDownload] = [:]
    private var playlists: [Playlist] = []
    /// What plays offline, built when Home is drawn offline and dropped when the library or the downloads change.
    private var offlineIndex: CarPlayOfflineIndex?
    /// The ids of the playlists with a song that plays offline, nil until known; restarted when the playlists or the downloads change.
    private(set) var playablePlaylists: Set<Int64>?
    private var downloadsTask: Task<Void, Never>?
    private var playablePlaylistsTask: Task<Void, Never>?
    private var playablePlaylistsInputs: PlayablePlaylistsInputs?

    private struct PlayablePlaylistsInputs: Equatable {
        let playlistIds: Set<Int64>
        let downloaded: Set<String>
    }

    var isOffline: Bool { !(network?.isOnline ?? true) }

    init(graph: IosAppGraph, offlineDownloads: OfflineDownloads, onChange: @escaping () -> Void) {
        self.graph = graph
        self.onChange = onChange
        network = CarPlayNetworkMonitor { [weak self] _ in
            self?.observePlayablePlaylists()
            self?.onChange()
        }
        let flow = offlineDownloads.downloads
        downloads = flow.value
        downloadsTask = Task { [weak self] in
            for await downloads in flow {
                guard let self else { return }
                self.downloads = downloads
                self.observePlayablePlaylists()
                self.songsChanged()
            }
        }
    }

    /// Nil online; offline, what plays, built on first use for `songs`.
    func index(songs: [Song]) -> CarPlayOfflineIndex? {
        guard isOffline else { return nil }
        if let offlineIndex { return offlineIndex }
        let index = CarPlayOfflineIndex(songs: songs, downloads: downloads)
        offlineIndex = index
        return index
    }

    func playlistsChanged(_ playlists: [Playlist]) {
        self.playlists = playlists
        observePlayablePlaylists()
    }

    /// The library or the downloads changed: what plays offline may have too.
    func songsChanged() {
        offlineIndex = nil
        if isOffline { onChange() }
    }

    func stop() {
        downloadsTask?.cancel()
        downloadsTask = nil
        playablePlaylistsTask?.cancel()
        playablePlaylistsTask = nil
        playablePlaylistsInputs = nil
        playablePlaylists = nil
        network?.cancel()
        network = nil
        offlineIndex = nil
        downloads = [:]
    }

    /// Re-asks which playlists have a song that plays offline, for the current playlists and completed downloads.
    /// Only while offline, and only when the playlist ids or the completed download paths changed: `downloads` emits on
    /// every progress tick, and each restart re-reads every playlist's songs.
    private func observePlayablePlaylists() {
        guard isOffline else {
            playablePlaylistsTask?.cancel()
            playablePlaylistsTask = nil
            playablePlaylistsInputs = nil
            playablePlaylists = nil
            return
        }
        let downloaded = Set(downloads.compactMap { $0.value.state == .completed ? $0.key : nil })
        let inputs = PlayablePlaylistsInputs(playlistIds: Set(playlists.map(\.id)), downloaded: downloaded)
        guard inputs != playablePlaylistsInputs else { return }
        playablePlaylistsInputs = inputs
        playablePlaylistsTask?.cancel()
        let flow = graph.observePlayablePlaylists.invoke(playlists: playlists, downloadedPaths: downloaded)
        playablePlaylistsTask = Task { [weak self] in
            for await ids in flow {
                guard let self else { return }
                self.playablePlaylists = Set(ids.map(\.int64Value))
                self.songsChanged()
            }
        }
    }
}
