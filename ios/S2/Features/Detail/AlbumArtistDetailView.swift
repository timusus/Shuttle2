import Shared
import SwiftUI

/// Album artist detail (P5-7, polished in #624): a hero tinted from the artist's picture (circular artwork, name,
/// albums · songs, Play/Shuffle), a shelf of the artist's album tiles (each zooming into `Route.album`), then every
/// one of the artist's songs across those albums. A tap plays from that song; its context menu has the shared media
/// actions. Modeled on Android's `AlbumArtistDetailScreen.kt`, minus its inline per-album expansion — the shelf
/// covers the same "browse by album" need with less chrome, and the flat song list still lets you play the whole
/// discography from any point.
struct AlbumArtistDetailView: View {
    let albumArtistKey: String?

    var body: some View {
        let route = Route.albumArtist(albumArtistKey: albumArtistKey)
        let models = ViewModelCache.shared.viewModel(route.cacheKey) {
            AlbumArtistDetailModels(graph: AppGraph.shared, groupKey: AlbumArtistGroupKey(key: albumArtistKey))
        }
        Observing(models.artist.uiState, models.actions.uiState) { state, actions in
            AlbumArtistDetailContent(
                state: state,
                isPlaying: AppGraph.dependencies.playerBinding.isPlaying,
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

/// The Album Artist detail screen's ViewModels, cached together under its route's key.
final class AlbumArtistDetailModels: ViewModelGroup {
    let artist: AlbumArtistDetailViewModel
    let actions: MediaActionsViewModel

    init(graph: IosAppGraph, groupKey: AlbumArtistGroupKey) {
        artist = graph.albumArtistDetailViewModelFactory.create(groupKey: groupKey)
        actions = graph.mediaActionsViewModel
    }

    var members: [Lifecycle_viewmodelViewModel] { [artist, actions] }
}

/// The Album Artist detail screen from an `AlbumArtistDetailUiState`, in a `DetailScaffold` tinted from the artist's
/// picture.
struct AlbumArtistDetailContent: View {
    let state: AlbumArtistDetailUiState
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
            EmptyState("Artist Not Found", systemImage: "person.2")
        case .ready:
            if let artist = state.albumArtist {
                let name = artist.name ?? artist.friendlyArtistName ?? "Unknown Artist"
                DetailScaffold(title: name, tintSource: .albumArtist(artist)) { layout in
                    DetailHero(
                        title: name,
                        subtitle: eyebrow(pluralized(state.albums.count, "album"), pluralized(state.songs.count, "song")),
                        layout: layout,
                        onPlay: { onPlay(0) },
                        onShuffle: onShuffle
                    ) { points in
                        RemoteArtwork(.albumArtist(artist), points: points) {
                            ArtworkPlaceholder(symbol: "person.fill")
                        }
                        .artworkCircle(points)
                    }
                } rows: {
                    if !state.albums.isEmpty {
                        DetailAlbumShelf(title: "Albums", albums: state.albums, subtitle: { $0.year.map { String($0.intValue) } })
                    }
                    Section {
                        SectionHeader("Songs")
                            .listRowSeparator(.hidden)
                        ForEach(Array(state.songs.enumerated()), id: \.element.id) { index, song in
                            Button { onPlay(index) } label: {
                                DetailSongRow(song: song, playback: rowPlayback(song, current: state.currentSong, isPlaying: isPlaying))
                            }
                            .buttonStyle(.plain)
                            .contextMenu {
                                Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") { onPlayNext(song) }
                                Button("Add to Queue", systemImage: "text.append") { onAddToQueue(song) }
                            }
                        }
                    }
                }
            } else {
                EmptyState("Artist Not Found", systemImage: "person.2")
            }
        }
    }
}

/// A detail screen's shelf of album tiles under a `SectionHeader`, as one `List` section: each tile pushes its album
/// and is the zoom source for it.
struct DetailAlbumShelf: View {
    let title: String
    let albums: [Album]
    let subtitle: (Album) -> String?

    @Environment(\.layoutTier) private var layoutTier

    var body: some View {
        let inset = AdaptiveLayout.contentInset(layoutTier)
        Section {
            VStack(alignment: .leading, spacing: Spacing.smallMedium) {
                SectionHeader(title)
                    .padding(.horizontal, inset)
                Shelf(inset: inset) {
                    ForEach(albums, id: \.stableId) { album in
                        let route = Route.album(album)
                        NavigationLink(value: route) {
                            AlbumTileLabel(album: album, subtitle: subtitle(album))
                        }
                        .buttonStyle(.pressScale)
                        .zoomSource(id: route.cacheKey)
                    }
                }
            }
            .padding(.vertical, Spacing.small)
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.clear)
        }
        .listRowSeparator(.hidden)
    }
}
