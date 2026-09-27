import Shared
import SwiftUI

/// Album artist detail (P5-7): hero (circular artwork, name), Play/Shuffle, a horizontal shelf of the artist's
/// albums (each pushing `Route.album`), then every one of the artist's songs across those albums. A tap plays
/// from that song; its context menu has the shared media actions. Modeled on Android's
/// `AlbumArtistDetailScreen.kt`, minus its inline per-album expansion — the shelf covers the same "browse by
/// album" need with less chrome, and the flat song list still lets you play the whole discography from any point.
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

/// The Album Artist detail screen from an `AlbumArtistDetailUiState`.
struct AlbumArtistDetailContent: View {
    let state: AlbumArtistDetailUiState
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
                List {
                    heroSection(artist)
                    if !state.albums.isEmpty {
                        Section("Albums") {
                            albumShelf
                                .listRowInsets(EdgeInsets())
                        }
                    }
                    Section("Songs") {
                        ForEach(Array(state.songs.enumerated()), id: \.element.id) { index, song in
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
                .listStyle(.plain)
                .navigationTitle(artist.name ?? artist.friendlyArtistName ?? "Artist")
                .navigationBarTitleDisplayMode(.inline)
            } else {
                EmptyState("Artist Not Found", systemImage: "person.2")
            }
        }
    }

    private var albumShelf: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(alignment: .top, spacing: Spacing.medium) {
                ForEach(state.albums, id: \.stableId) { album in
                    NavigationLink(value: Route.album(album)) {
                        VStack(alignment: .leading, spacing: Spacing.xsmall) {
                            RemoteArtwork(id: album.stableId, points: ArtworkSize.shelf) {
                                try await AppGraph.shared.artworkUrls.url(album: album)
                            }
                            .frame(width: ArtworkSize.shelf, height: ArtworkSize.shelf)
                            .clipShape(RoundedRectangle(cornerRadius: Radius.medium, style: .continuous))
                            Text(album.name ?? "Unknown")
                                .font(.footnote)
                                .foregroundStyle(.primary)
                                .lineLimit(1)
                                .frame(width: ArtworkSize.shelf, alignment: .leading)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal)
        }
    }

    @ViewBuilder
    private func heroSection(_ artist: AlbumArtist) -> some View {
        Section {
            DetailHero(
                title: artist.name ?? artist.friendlyArtistName ?? "Unknown Artist",
                subtitle: subtitle,
                onPlay: { onPlay(0) },
                onShuffle: onShuffle
            ) {
                RemoteArtwork(id: artist.stableId, points: ArtworkSize.hero) {
                    try await AppGraph.shared.artworkUrls.url(albumArtist: artist)
                }
                .frame(width: ArtworkSize.hero, height: ArtworkSize.hero)
                .clipShape(Circle())
            }
            .listRowInsets(EdgeInsets())
        }
        .listRowSeparator(.hidden)
    }

    private var subtitle: String {
        "\(pluralized(state.albums.count, "album")) · \(pluralized(state.songs.count, "song"))"
    }
}
