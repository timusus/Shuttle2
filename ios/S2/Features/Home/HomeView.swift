import Shared
import SwiftUI

/// Home (#587): a hero to resume the queue, then the library's shelves from the shared `HomeViewModel` —
/// recently played, recently added and most played albums, and artists the library hasn't played yet.
/// A tap opens the album's route; a tile's context menu plays or queues it through the shared
/// `MediaAction`s. The empty state shows before there's a library to shelve.
struct HomeView: View {
    let navigator: Navigator

    var body: some View {
        let models = ViewModelCache.shared.viewModel(AppTab.home.cacheKey) {
            HomeModels(graph: AppGraph.shared)
        }
        Observing(models.home.uiState, models.actions.uiState) { state, actions in
            HomeContent(
                state: state,
                onShuffleAll: {
                    if let action = models.home.shuffleAll() { models.actions.dispatch(action: action) }
                },
                onTogglePlayback: models.home.onTogglePlayback,
                onShuffleQueue: {
                    if let action = models.home.shuffleQueue() { models.actions.dispatch(action: action) }
                },
                onAlbumTap: { navigator.open(.album($0)) },
                onArtistTap: { navigator.open(.albumArtist(albumArtistKey: $0.groupKey.key)) },
                onPlay: { models.actions.dispatch(action: MediaActionPlay(selection: $0, position: 0)) },
                onPlayNext: { models.actions.dispatch(action: MediaActionPlayNext(selection: $0)) },
                onAddToQueue: { models.actions.dispatch(action: MediaActionAddToQueue(selection: $0)) }
            )
            .mediaActionResults(actions.events, handled: { models.actions.onEventHandled(id: $0) })
            .consumeEvents((state as? HomeUiStateContent)?.events ?? [], handled: { models.home.onEventHandled(id: $0) }) { _ in }
        }
        .refreshable { LibraryImport.refresh() }
        .navigationTitle(AppTab.home.title)
    }
}

/// Home's shared ViewModels, cached together under its tab's key.
final class HomeModels: ViewModelGroup {
    let home: HomeViewModel
    let actions: MediaActionsViewModel

    init(graph: IosAppGraph) {
        home = graph.homeViewModel
        actions = graph.mediaActionsViewModel
    }

    var members: [Lifecycle_viewmodelViewModel] { [home, actions] }
}

/// Home from a `HomeUiState`. `showWhatsNew`/`HomeEvent.AnalyticsNowOn` have no iOS surface yet (no
/// changelog or analytics-consent screen until phase 7, #589), so their events are consumed and dropped.
struct HomeContent: View {
    let state: HomeUiState
    var onShuffleAll: () -> Void = {}
    var onTogglePlayback: () -> Void = {}
    var onShuffleQueue: () -> Void = {}
    var onAlbumTap: (Album) -> Void = { _ in }
    var onArtistTap: (AlbumArtist) -> Void = { _ in }
    var onPlay: (MediaSelection) -> Void = { _ in }
    var onPlayNext: (MediaSelection) -> Void = { _ in }
    var onAddToQueue: (MediaSelection) -> Void = { _ in }

    var body: some View {
        switch onEnum(of: state) {
        case .loading:
            ProgressView()
        case .empty:
            // Plex isn't offered on iOS until its provider is in :shared (`MediaProviderType.signInTypes`).
            EmptyState("No Music", systemImage: "house", message: "Connect a Jellyfin or Emby server to stream your music.") {
                NavigationLink("Add a Source", value: Route.sources)
                    .accessibilityIdentifier("homeEmpty.addSource")
            }
        case .content(let content):
            List {
                if let resume = content.resume {
                    Section {
                        ResumeHero(resume: resume, onTogglePlayback: onTogglePlayback, onShuffleQueue: onShuffleQueue)
                    }
                    .listRowSeparator(.hidden)
                    .listRowInsets(EdgeInsets())
                }
                AlbumShelf(
                    title: "Recently Played",
                    albums: content.recentlyPlayed,
                    showPlayCount: false,
                    onTap: onAlbumTap,
                    onPlay: onPlay, onPlayNext: onPlayNext, onAddToQueue: onAddToQueue
                )
                AlbumShelf(
                    title: "Recently Added",
                    albums: content.recentlyAdded,
                    showPlayCount: false,
                    onTap: onAlbumTap,
                    onPlay: onPlay, onPlayNext: onPlayNext, onAddToQueue: onAddToQueue
                )
                AlbumShelf(
                    title: "Most Played",
                    albums: content.mostPlayed,
                    showPlayCount: true,
                    onTap: onAlbumTap,
                    onPlay: onPlay, onPlayNext: onPlayNext, onAddToQueue: onAddToQueue
                )
                ArtistShelf(
                    title: "Something Different",
                    artists: content.somethingDifferent,
                    onTap: onArtistTap,
                    onPlay: onPlay, onPlayNext: onPlayNext, onAddToQueue: onAddToQueue
                )
            }
            .listStyle(.plain)
            .toolbar {
                Button("Shuffle", systemImage: "shuffle", action: onShuffleAll)
            }
        }
    }
}

/// The queue to pick up from: its song's artwork, title and artist, the time left, and play/pause plus
/// shuffle-queue actions (Android's `ResumeHero`).
private struct ResumeHero: View {
    let resume: ResumeQueue
    let onTogglePlayback: () -> Void
    let onShuffleQueue: () -> Void

    var body: some View {
        HStack(spacing: Spacing.smallMedium) {
            RemoteArtwork(.song(resume.song), points: ArtworkSize.albumRow)
                .artworkTile(ArtworkSize.albumRow, cornerRadius: ArtworkCorner.row)

            VStack(alignment: .leading, spacing: Spacing.tiny) {
                Text(resume.song.name ?? "Unknown").font(.headline).lineLimit(1)
                Text(resume.song.albumArtist ?? "Unknown").font(.subheadline).foregroundStyle(.secondary).lineLimit(1)
                Text(timeLeft).font(.s2Time).foregroundStyle(.s2SecondaryText)
            }

            Spacer(minLength: 0)

            Button(action: onShuffleQueue) {
                Image(systemName: "shuffle")
                    .font(.title3)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Shuffle Queue")

            Button(action: onTogglePlayback) {
                Image(systemName: resume.playing ? "pause.circle.fill" : "play.circle.fill")
                    .font(.largeTitle)
                    .foregroundStyle(.tint)
                    .frame(minWidth: 44, minHeight: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(resume.playing ? "Pause" : "Play")
        }
        .padding(.horizontal, Spacing.medium)
        .padding(.vertical, Spacing.small)
    }

    private var timeLeft: String {
        let seconds = Int(resume.timeLeftMs / 1000)
        return String(format: "%d:%02d left", seconds / 60, seconds % 60)
    }
}

/// A horizontal shelf of albums; hidden when `albums` is empty.
private struct AlbumShelf: View {
    let title: String
    let albums: [Album]
    let showPlayCount: Bool
    let onTap: (Album) -> Void
    let onPlay: (MediaSelection) -> Void
    let onPlayNext: (MediaSelection) -> Void
    let onAddToQueue: (MediaSelection) -> Void

    var body: some View {
        if !albums.isEmpty {
            Section {
                Text(title).font(.s2SectionTitle).accessibilityAddTraits(.isHeader).listRowSeparator(.hidden)
                ScrollView(.horizontal, showsIndicators: false) {
                    LazyHStack(alignment: .top, spacing: Spacing.smallMedium) {
                        ForEach(albums, id: \.stableId) { album in
                            AlbumTile(album: album, showPlayCount: showPlayCount, onTap: { onTap(album) })
                                .contextMenu {
                                    Button("Play", systemImage: "play") { onPlay(MediaSelectionAlbums(album: album)) }
                                    Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") { onPlayNext(MediaSelectionAlbums(album: album)) }
                                    Button("Add to Queue", systemImage: "text.append") { onAddToQueue(MediaSelectionAlbums(album: album)) }
                                }
                        }
                    }
                }
                .contentMargins(.horizontal, Spacing.medium, for: .scrollContent)
                .listRowInsets(EdgeInsets())
                .listRowSeparator(.hidden)
            }
        }
    }
}

/// A horizontal shelf of artists; hidden when `artists` is empty.
private struct ArtistShelf: View {
    let title: String
    let artists: [AlbumArtist]
    let onTap: (AlbumArtist) -> Void
    let onPlay: (MediaSelection) -> Void
    let onPlayNext: (MediaSelection) -> Void
    let onAddToQueue: (MediaSelection) -> Void

    var body: some View {
        if !artists.isEmpty {
            Section {
                Text(title).font(.s2SectionTitle).accessibilityAddTraits(.isHeader).listRowSeparator(.hidden)
                ScrollView(.horizontal, showsIndicators: false) {
                    LazyHStack(alignment: .top, spacing: Spacing.smallMedium) {
                        ForEach(artists, id: \.stableId) { artist in
                            ArtistTile(artist: artist, onTap: { onTap(artist) })
                                .contextMenu {
                                    Button("Play", systemImage: "play") { onPlay(MediaSelectionAlbumArtists(albumArtist: artist)) }
                                    Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") { onPlayNext(MediaSelectionAlbumArtists(albumArtist: artist)) }
                                    Button("Add to Queue", systemImage: "text.append") { onAddToQueue(MediaSelectionAlbumArtists(albumArtist: artist)) }
                                }
                        }
                    }
                }
                .contentMargins(.horizontal, Spacing.medium, for: .scrollContent)
                .listRowInsets(EdgeInsets())
                .listRowSeparator(.hidden)
            }
        }
    }
}


private struct AlbumTile: View {
    let album: Album
    var showPlayCount: Bool = false
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: Spacing.xsmall) {
                RemoteArtwork(id: album.stableId, points: ArtworkSize.shelf) {
                    try await AppGraph.shared.artworkUrls.url(album: album)
                }
                .frame(width: ArtworkSize.shelf, height: ArtworkSize.shelf)
                .clipShape(RoundedRectangle(cornerRadius: ArtworkCorner.tile, style: .continuous))
                Text(album.name ?? "Unknown").font(.subheadline).lineLimit(1)
                Text(subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
            }
            .frame(width: ArtworkSize.shelf)
        }
        .buttonStyle(.plain)
    }

    private var subtitle: String {
        let artist = album.albumArtist ?? "Unknown"
        return showPlayCount ? "\(album.playCount == 1 ? "1 play" : "\(album.playCount) plays") · \(artist)" : artist
    }
}

private struct ArtistTile: View {
    let artist: AlbumArtist
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: Spacing.xsmall) {
                RemoteArtwork(id: artist.stableId, points: ArtworkSize.shelf) {
                    try await AppGraph.shared.artworkUrls.url(albumArtist: artist)
                }
                .frame(width: ArtworkSize.shelf, height: ArtworkSize.shelf)
                .clipShape(Circle())
                Text(artist.name ?? "Unknown").font(.subheadline).lineLimit(1)
                Text(artist.albumCount == 1 ? "1 album" : "\(artist.albumCount) albums")
                    .font(.caption).foregroundStyle(.secondary).lineLimit(1)
            }
            .frame(width: ArtworkSize.shelf)
        }
        .buttonStyle(.plain)
    }
}
