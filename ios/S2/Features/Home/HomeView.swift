import Shared
import SwiftUI

/// Home (#587, polished in #624): a card to resume the queue, then the library's shelves from the shared
/// `HomeViewModel` — recently played, recently added and most played albums, and artists the library hasn't played
/// yet. A tap opens the album or artist (zooming from the tile on iOS 18+); a tile's context menu plays or queues it
/// through the shared `MediaAction`s. Before there's a library to shelve, the empty state, or the import's progress
/// while one runs. Pull to refresh re-imports, in every state.
struct HomeView: View {
    let navigator: Navigator

    var body: some View {
        let models = ViewModelCache.shared.viewModel(AppTab.home.cacheKey) {
            HomeModels(graph: AppGraph.shared)
        }
        Observing(models.home.uiState, models.actions.uiState, models.importState) { state, actions, importState in
            HomeContent(
                state: state,
                importStatus: ImportStatus(importState),
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

/// Home's shared ViewModels, cached together under its tab's key, plus the import's state for the empty state.
final class HomeModels: ViewModelGroup {
    let home: HomeViewModel
    let actions: MediaActionsViewModel
    let importState: SkieSwiftStateFlow<SongImportState>

    init(graph: IosAppGraph) {
        home = graph.homeViewModel
        actions = graph.mediaActionsViewModel
        importState = graph.songImportStateProvider.songImportState
    }

    var members: [Lifecycle_viewmodelViewModel] { [home, actions] }
}

/// Home from a `HomeUiState`. `showWhatsNew`/`HomeEvent.AnalyticsNowOn` have no iOS surface yet (no
/// changelog or analytics-consent screen until phase 7, #589), so their events are consumed and dropped.
///
/// A `ScrollView` of a `LazyVStack`, not a `List`: the shelves and the resume card are full-bleed cards, and the
/// content is capped at `AdaptiveLayout.contentMaxWidth` and centred on an iPad.
struct HomeContent: View {
    let state: HomeUiState
    var importStatus: ImportStatus = .idle
    var onShuffleAll: () -> Void = {}
    var onTogglePlayback: () -> Void = {}
    var onShuffleQueue: () -> Void = {}
    var onAlbumTap: (Album) -> Void = { _ in }
    var onArtistTap: (AlbumArtist) -> Void = { _ in }
    var onPlay: (MediaSelection) -> Void = { _ in }
    var onPlayNext: (MediaSelection) -> Void = { _ in }
    var onAddToQueue: (MediaSelection) -> Void = { _ in }

    @Environment(\.layoutTier) private var layoutTier
    /// The tile the last tap came from, the one zoom source for its route. An album can be on more than one shelf
    /// (recently and most played), and a zoom from a shelf the user didn't touch would be wrong.
    @State private var zoomSourceKey: String?

    var body: some View {
        switch onEnum(of: state) {
        case .loading:
            ProgressView()
        case .empty:
            // A scroll view even here, so pull to refresh re-imports from the empty state too.
            ScrollView {
                emptyState
                    .containerRelativeFrame(.vertical)
            }
        case .content(let content):
            ScrollView {
                LazyVStack(alignment: .leading, spacing: Spacing.large) {
                    if let resume = content.resume {
                        ResumeCard(resume: resume, onTogglePlayback: onTogglePlayback, onShuffleQueue: onShuffleQueue)
                            .padding(.horizontal, inset)
                    }
                    albumShelf("Recently Played", key: "recentlyPlayed", albums: content.recentlyPlayed)
                    albumShelf("Recently Added", key: "recentlyAdded", albums: content.recentlyAdded)
                    albumShelf("Most Played", key: "mostPlayed", albums: content.mostPlayed, showPlayCount: true)
                    artistShelf(content.somethingDifferent)
                }
                .padding(.top, Spacing.small)
                .padding(.bottom, Spacing.large)
                .frame(maxWidth: AdaptiveLayout.contentMaxWidth)
                .frame(maxWidth: .infinity)
            }
            .toolbar {
                Button("Shuffle", systemImage: "shuffle", action: onShuffleAll)
            }
        }
    }

    private var inset: CGFloat { AdaptiveLayout.contentInset(layoutTier) }

    /// "No Music" is only true once nothing is importing: during the first import the library is empty because it
    /// hasn't arrived yet, so Home says that instead.
    @ViewBuilder
    private var emptyState: some View {
        switch importStatus {
        case .importing(let provider, let message, _):
            EmptyState("Importing Your Music", systemImage: "arrow.down.circle", message: message ?? "Reading your library from \(provider).") {
                ProgressView()
            }
        case .idle, .failed:
            // Plex isn't offered on iOS until its provider is in :shared (`MediaProviderType.signInTypes`).
            EmptyState("No Music", systemImage: "house", message: "Connect a Jellyfin or Emby server to stream your music.") {
                NavigationLink("Add a Source", value: Route.sources)
                    .accessibilityIdentifier("homeEmpty.addSource")
            }
        }
    }

    /// A shelf of albums under its header (See All opens Albums); nothing when `albums` is empty.
    @ViewBuilder
    private func albumShelf(_ title: String, key: String, albums: [Album], showPlayCount: Bool = false) -> some View {
        if !albums.isEmpty {
            VStack(alignment: .leading, spacing: Spacing.smallMedium) {
                SectionHeader(title, seeAll: .libraryCategory(.albums))
                    .padding(.horizontal, inset)
                Shelf(inset: inset) {
                    ForEach(albums, id: \.stableId) { album in
                        let tileKey = "\(key)|\(album.stableId)"
                        Button {
                            zoomSourceKey = tileKey
                            onAlbumTap(album)
                        } label: {
                            AlbumTileLabel(album: album, subtitle: albumSubtitle(album, showPlayCount: showPlayCount))
                        }
                        .buttonStyle(.pressScale)
                        .zoomSource(id: Route.album(album).cacheKey, tileKey: tileKey, active: zoomSourceKey == tileKey)
                        .accessibilityIdentifier("homeTile.album")
                        .contextMenu {
                            mediaActions(MediaSelectionAlbums(album: album))
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func artistShelf(_ artists: [AlbumArtist]) -> some View {
        if !artists.isEmpty {
            VStack(alignment: .leading, spacing: Spacing.smallMedium) {
                SectionHeader("Something Different", seeAll: .libraryCategory(.albumArtists))
                    .padding(.horizontal, inset)
                Shelf(inset: inset) {
                    ForEach(artists, id: \.stableId) { artist in
                        let tileKey = "artists|\(artist.stableId)"
                        Button {
                            zoomSourceKey = tileKey
                            onArtistTap(artist)
                        } label: {
                            ArtistTileLabel(artist: artist)
                        }
                        .buttonStyle(.pressScale)
                        .zoomSource(id: Route.albumArtist(albumArtistKey: artist.groupKey.key).cacheKey, tileKey: tileKey, active: zoomSourceKey == tileKey)
                        .accessibilityIdentifier("homeTile.artist")
                        .contextMenu {
                            mediaActions(MediaSelectionAlbumArtists(albumArtist: artist))
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func mediaActions(_ selection: MediaSelection) -> some View {
        Button("Play", systemImage: "play") { onPlay(selection) }
        Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") { onPlayNext(selection) }
        Button("Add to Queue", systemImage: "text.append") { onAddToQueue(selection) }
    }

    private func albumSubtitle(_ album: Album, showPlayCount: Bool) -> String {
        let artist = album.albumArtist ?? "Unknown"
        return showPlayCount ? "\(album.playCount == 1 ? "1 play" : "\(album.playCount) plays") · \(artist)" : artist
    }
}

/// The queue to pick up from, as a full-width card (Android's `ResumeHero`): its song's cover over a blurred wash
/// of the same cover and its tint, a "Continue" eyebrow, the title, artist and time left, then Play/Pause and
/// Shuffle Queue as capsules in the cover's tint.
private struct ResumeCard: View {
    let resume: ResumeQueue
    let onTogglePlayback: () -> Void
    let onShuffleQueue: () -> Void

    var body: some View {
        ResumeCardBody(resume: resume, onTogglePlayback: onTogglePlayback, onShuffleQueue: onShuffleQueue)
            .artworkTint(from: .song(resume.song))
    }
}

private struct ResumeCardBody: View {
    let resume: ResumeQueue
    let onTogglePlayback: () -> Void
    let onShuffleQueue: () -> Void

    /// The card's cover, larger than a row's and smaller than a hero's.
    private static let coverSize: CGFloat = 96

    @Environment(\.artworkTint) private var tint
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: ArtworkCorner.hero, style: .continuous)
        VStack(alignment: .leading, spacing: Spacing.medium) {
            HStack(alignment: .center, spacing: Spacing.medium) {
                RemoteArtwork(.song(resume.song), points: Self.coverSize)
                    .artworkTile(Self.coverSize, cornerRadius: ArtworkCorner.tile)
                VStack(alignment: .leading, spacing: Spacing.xsmall) {
                    Text("Continue")
                        .font(.s2Eyebrow)
                        .textCase(.uppercase)
                        .foregroundStyle(tint)
                    Text(resume.song.name ?? "Unknown")
                        .font(.s2Headline)
                        .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 2)
                    Text(resume.song.friendlyArtistName ?? resume.song.albumArtist ?? "Unknown")
                        .font(.subheadline)
                        .foregroundStyle(.s2SecondaryText)
                        .lineLimit(1)
                    Text(timeLeft)
                        .font(.s2Time)
                        .foregroundStyle(.s2SecondaryText)
                }
                Spacer(minLength: 0)
            }
            HeroActions(
                playTitle: resume.playing ? "Pause" : "Play",
                playSymbol: resume.playing ? "pause.fill" : "play.fill",
                onPlay: onTogglePlayback,
                onShuffle: onShuffleQueue
            )
            .tint(tint)
        }
        .padding(Spacing.medium)
        .background {
            ZStack {
                Color(.secondarySystemBackground)
                RemoteArtwork(.song(resume.song), points: Self.coverSize)
                    .blur(radius: Spacing.xlarge)
                    .scaleEffect(1.4)
                    .opacity(0.55)
                    .accessibilityHidden(true)
                LinearGradient(
                    colors: [tint.opacity(0.18), Color(.systemBackground).opacity(0.55)],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            }
        }
        // The card, not the backdrop: the blurred cover lays out wider than the card and would spill past its edges.
        .clipShape(shape)
        .overlay { shape.strokeBorder(ArtworkHairline.color, lineWidth: ArtworkHairline.width) }
        .accessibilityElement(children: .contain)
    }

    private var timeLeft: String {
        let seconds = Int(resume.timeLeftMs / 1000)
        if seconds >= 3600 {
            return String(format: "%d:%02d:%02d left", seconds / 3600, seconds / 60 % 60, seconds % 60)
        }
        return String(format: "%d:%02d left", seconds / 60, seconds % 60)
    }
}

/// A horizontal shelf: tiles in a lazy row that snaps to them, inset to the screen's margin.
struct Shelf<Content: View>: View {
    let inset: CGFloat
    @ViewBuilder let content: () -> Content

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            LazyHStack(alignment: .top, spacing: Spacing.smallMedium) {
                content()
            }
            .scrollTargetLayout()
        }
        .scrollTargetBehavior(.viewAligned)
        .contentMargins(.horizontal, inset, for: .scrollContent)
    }
}

/// An album tile's face: the cover at `ArtworkSize.shelf(tier)` with the tile corner and hairline, its title and a
/// subtitle. The label of whatever says what a tap does (a `Button` on Home, a `NavigationLink` on a detail screen).
struct AlbumTileLabel: View {
    let album: Album
    let subtitle: String?

    @Environment(\.layoutTier) private var layoutTier

    var body: some View {
        let size = ArtworkSize.shelf(layoutTier)
        VStack(alignment: .leading, spacing: Spacing.xsmall) {
            RemoteArtwork(.album(album), points: size) {
                ArtworkPlaceholder(symbol: "square.stack")
            }
            .artworkTile(size, cornerRadius: ArtworkCorner.tile)
            .padding(.bottom, Spacing.xsmall)
            Text(album.name ?? "Unknown")
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.primary)
                .lineLimit(1)
            if let subtitle {
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.s2SecondaryText)
                    .lineLimit(1)
            }
        }
        .frame(width: size, alignment: .leading)
        .contentShape(Rectangle())
    }
}

/// An artist tile's face: a circle at `ArtworkSize.artistShelf` with the name and album count centred beneath.
struct ArtistTileLabel: View {
    let artist: AlbumArtist

    var body: some View {
        VStack(spacing: Spacing.xsmall) {
            RemoteArtwork(.albumArtist(artist), points: ArtworkSize.artistShelf) {
                ArtworkPlaceholder(symbol: "person.fill")
            }
            .artworkCircle(ArtworkSize.artistShelf)
            .padding(.bottom, Spacing.xsmall)
            Text(artist.name ?? artist.friendlyArtistName ?? "Unknown")
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.primary)
                .lineLimit(1)
            Text(artist.albumCount == 1 ? "1 album" : "\(artist.albumCount) albums")
                .font(.caption)
                .foregroundStyle(.s2SecondaryText)
                .lineLimit(1)
        }
        .multilineTextAlignment(.center)
        .frame(width: ArtworkSize.artistShelf)
        .contentShape(Rectangle())
    }
}

extension View {
    /// The zoom source for `id` while `active`, otherwise a source under the tile's own `tileKey`, which no screen
    /// zooms to: of several tiles showing one item, only the tapped one is the source. The id changes rather than
    /// the modifier coming and going, so the tile keeps its identity (and its loaded cover) when it's tapped.
    func zoomSource(id: String, tileKey: String, active: Bool) -> some View {
        zoomSource(id: active ? id : "tile|\(tileKey)")
    }
}
