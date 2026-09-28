import Shared
import SwiftUI

/// Album detail (P5-7, polished in #624): a hero tinted from the cover (artwork, title, artist · year · songs ·
/// duration, Play/Shuffle), then the album's tracks, split into "Disc N" groups when it has more than one. A tap
/// plays the album from that track; its context menu has the shared media actions. Modeled on Android's
/// `AlbumDetailScreen.kt`.
struct AlbumDetailView: View {
    let albumKey: String?
    let albumArtistKey: String?

    var body: some View {
        let route = Route.album(albumKey: albumKey, albumArtistKey: albumArtistKey)
        let models = ViewModelCache.shared.viewModel(route.cacheKey) {
            AlbumDetailModels(
                graph: AppGraph.shared,
                groupKey: AlbumGroupKey(key: albumKey, albumArtistGroupKey: albumArtistKey.map { AlbumArtistGroupKey(key: $0) })
            )
        }
        Observing(models.album.uiState, models.actions.uiState) { state, actions in
            AlbumDetailContent(
                state: state,
                isPlaying: AppGraph.dependencies.playerBinding.isPlaying,
                onPlay: { index in
                    models.actions.dispatch(action: MediaActionPlay(selection: MediaSelectionSongs(songs: state.songs), position: Int32(index), context: state.playContext))
                },
                onShuffle: {
                    models.actions.dispatch(action: MediaActionShuffle(selection: MediaSelectionSongs(songs: state.songs), context: state.playContext))
                },
                onPlayNext: { song in
                    models.actions.dispatch(action: MediaActionPlayNext(selection: MediaSelectionSongs(song: song)))
                },
                onAddToQueue: { song in
                    models.actions.dispatch(action: MediaActionAddToQueue(selection: MediaSelectionSongs(song: song)))
                }
            )
            .mediaActionResults(actions.events, handled: { models.actions.onEventHandled(id: $0) })
        }
    }
}

/// The Album detail screen's ViewModels, cached together under its route's key.
final class AlbumDetailModels: ViewModelGroup {
    let album: AlbumDetailViewModel
    let actions: MediaActionsViewModel

    init(graph: IosAppGraph, groupKey: AlbumGroupKey) {
        album = graph.albumDetailViewModelFactory.create(groupKey: groupKey)
        actions = graph.mediaActionsViewModel
    }

    var members: [Lifecycle_viewmodelViewModel] { [album, actions] }
}

/// The Album detail screen from an `AlbumDetailUiState`, in a `DetailScaffold` tinted from the album's cover.
struct AlbumDetailContent: View {
    let state: AlbumDetailUiState
    /// Whether the player is playing, so the current track's indicator animates or holds still.
    var isPlaying: Bool = false
    var onPlay: (Int) -> Void = { _ in }
    var onShuffle: () -> Void = {}
    var onPlayNext: (Song) -> Void = { _ in }
    var onAddToQueue: (Song) -> Void = { _ in }

    var body: some View {
        switch state.loadingState {
        case .loading:
            ProgressView()
        case .empty:
            EmptyState("Album Not Found", systemImage: "square.stack")
        case .ready:
            if let album = state.album {
                DetailScaffold(title: album.name ?? "Album", tintSource: .album(album)) { layout in
                    DetailHero(
                        title: album.name ?? "Unknown Album",
                        subtitle: subtitle(album),
                        layout: layout,
                        onPlay: { onPlay(0) },
                        onShuffle: onShuffle
                    ) { points in
                        RemoteArtwork(.album(album), points: points)
                            .artworkTile(points, cornerRadius: ArtworkCorner.hero)
                    }
                } rows: {
                    ForEach(discs, id: \.disc) { group in
                        Section {
                            ForEach(group.songs, id: \.id) { song in
                                trackRow(song, album: album)
                            }
                        } header: {
                            if discs.count > 1 {
                                Text("Disc \(group.disc)")
                                    .font(.s2GroupHeader)
                                    .foregroundStyle(.s2SecondaryText)
                                    .textCase(.uppercase)
                            }
                        }
                    }
                }
            } else {
                EmptyState("Album Not Found", systemImage: "square.stack")
            }
        }
    }

    private func trackRow(_ song: Song, album: Album) -> some View {
        let index = state.songs.firstIndex(where: { $0.id == song.id }) ?? 0
        return Button { onPlay(index) } label: {
            TrackRow(
                number: song.track.map { Int($0.intValue) },
                title: song.name ?? "Unknown",
                subtitle: trackArtist(song, album: album),
                durationMs: Int64(song.duration),
                playback: rowPlayback(song, current: state.currentSong, isPlaying: isPlaying)
            )
        }
        .buttonStyle(.plain)
        .contextMenu {
            Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") { onPlayNext(song) }
            Button("Add to Queue", systemImage: "text.append") { onAddToQueue(song) }
        }
    }

    /// Songs grouped by disc (falling back to disc 1) in the order the ViewModel already sorted them, as
    /// Android's screen does (`songs.groupBy { it.disc ?: 1 }.toSortedMap()`).
    private var discs: [(disc: Int32, songs: [Song])] {
        var order: [Int32] = []
        var groups: [Int32: [Song]] = [:]
        for song in state.songs {
            let disc = song.disc?.int32Value ?? 1
            if groups[disc] == nil { order.append(disc) }
            groups[disc, default: []].append(song)
        }
        return order.sorted().map { (disc: $0, songs: groups[$0] ?? []) }
    }

    /// A track's own artist, only where it differs from the album's (a compilation, a guest): on every row of a
    /// one-artist album it would repeat the hero.
    private func trackArtist(_ song: Song, album: Album) -> String? {
        guard let artist = song.friendlyArtistName, artist != (album.friendlyArtistName ?? album.albumArtist) else { return nil }
        return artist
    }

    private func subtitle(_ album: Album) -> String {
        eyebrow(
            album.friendlyArtistName ?? album.albumArtist,
            album.year.map { String($0.intValue) },
            pluralized(state.songs.count, "song"),
            totalDuration(state.songs)
        )
    }
}
