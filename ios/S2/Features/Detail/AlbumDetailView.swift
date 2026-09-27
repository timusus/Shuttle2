import Shared
import SwiftUI

/// Album detail (P5-7): hero (artwork, title, artist/year), Play/Shuffle, then the album's songs, split into
/// "Disc N" sections when it has more than one. A tap plays the album from that song; its context menu has the
/// shared media actions. Modeled on Android's `AlbumDetailScreen.kt`.
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
                onPlay: { index in
                    models.actions.dispatch(action: MediaActionPlay(selection: MediaSelectionSongs(songs: state.songs), position: Int32(index)))
                },
                onShuffle: {
                    models.actions.dispatch(action: MediaActionShuffle(selection: MediaSelectionSongs(songs: state.songs)))
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

/// The Album detail screen from an `AlbumDetailUiState`.
struct AlbumDetailContent: View {
    let state: AlbumDetailUiState
    var onPlay: (Int) -> Void = { _ in }
    var onShuffle: () -> Void = {}
    var onPlayNext: (Song) -> Void = { _ in }
    var onAddToQueue: (Song) -> Void = { _ in }

    var body: some View {
        switch state.loadingState {
        case .loading:
            ProgressView()
        case .empty:
            ContentUnavailableView("Album Not Found", systemImage: "square.stack")
        case .ready:
            if let album = state.album {
                List {
                    heroSection(album)
                    ForEach(discs, id: \.disc) { group in
                        Section(discs.count > 1 ? "Disc \(group.disc)" : "") {
                            ForEach(group.songs, id: \.id) { song in
                                let index = state.songs.firstIndex(where: { $0.id == song.id }) ?? 0
                                Button { onPlay(index) } label: {
                                    AlbumSongRow(song: song, playing: song.id == state.currentSong?.id)
                                }
                                .buttonStyle(.plain)
                                .contextMenu {
                                    Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") { onPlayNext(song) }
                                    Button("Add to Queue", systemImage: "text.append") { onAddToQueue(song) }
                                }
                            }
                        }
                    }
                }
                .listStyle(.plain)
                .navigationTitle(album.name ?? "Album")
                .navigationBarTitleDisplayMode(.inline)
            } else {
                ContentUnavailableView("Album Not Found", systemImage: "square.stack")
            }
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

    @ViewBuilder
    private func heroSection(_ album: Album) -> some View {
        Section {
            DetailHero(
                title: album.name ?? "Unknown Album",
                subtitle: subtitle(album),
                onPlay: { onPlay(0) },
                onShuffle: onShuffle
            ) {
                RemoteArtwork(id: album.stableId, points: 160) {
                    try await AppGraph.shared.artworkUrls.url(album: album)
                }
                .frame(width: 160, height: 160)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            .listRowInsets(EdgeInsets())
        }
        .listRowSeparator(.hidden)
    }

    private func subtitle(_ album: Album) -> String {
        var parts: [String] = []
        if let artist = album.friendlyArtistName ?? album.albumArtist { parts.append(artist) }
        if let year = album.year { parts.append(String(year.intValue)) }
        parts.append(pluralized(state.songs.count, "song"))
        return parts.joined(separator: " · ")
    }
}

/// One album track: its number, title/artist and duration; highlighted while it's the current song.
struct AlbumSongRow: View {
    let song: Song
    var playing: Bool = false

    var body: some View {
        HStack {
            if let track = song.track {
                Text("\(track.intValue)")
                    .font(.subheadline.monospacedDigit())
                    .foregroundStyle(.secondary)
                    .frame(width: 24, alignment: .trailing)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(song.name ?? "Unknown")
                    .lineLimit(1)
                    .foregroundStyle(playing ? Color.accentColor : .primary)
                if let artist = song.friendlyArtistName {
                    Text(artist)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
            Spacer()
            Text(Duration.milliseconds(Int64(song.duration)).formatted(.time(pattern: .minuteSecond)))
                .font(.subheadline.monospacedDigit())
                .foregroundStyle(.secondary)
        }
        .contentShape(Rectangle())
    }
}
