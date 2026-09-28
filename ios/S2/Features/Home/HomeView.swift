import Shared
import SwiftUI

/// Home (#587, polished in #624, sections from #633): a card to resume the queue, then the suggestion sections from
/// the shared `HomeViewModel` (Jump Back In, the time of day, On Repeat, Rediscover, Recently Added, Genre Picks).
/// A tap opens the album, artist or playlist (zooming from the tile on iOS 18+) or shuffles the genre; a tile's context menu plays or queues it
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
                onItemTap: { item in
                    switch onEnum(of: item) {
                    case .albumItem(let it): navigator.open(.album(it.album))
                    case .artistItem(let it): navigator.open(.albumArtist(albumArtistKey: it.albumArtist.groupKey.key))
                    case .playlistItem(let it): navigator.open(.playlist(id: it.playlist.id))
                    case .smartPlaylistItem(let it): navigator.open(.smartPlaylist(id: it.smartPlaylistId.id))
                    case .genreItem: models.actions.dispatch(action: item.playAction())
                    }
                },
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
    var onItemTap: (HomeItem) -> Void = { _ in }
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
                    ForEach(content.sections, id: \.id) { section in
                        sectionView(section)
                    }
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

    /// One of Home's suggestion sections under its title: a shelf of its items, or for Shuffle All (cold start)
    /// a button that shuffles the library.
    @ViewBuilder
    private func sectionView(_ section: HomeSection) -> some View {
        let title = Self.title(section.title)
        if section.id == .shuffleAll {
            Button(title, systemImage: "shuffle", action: onShuffleAll)
                .buttonStyle(.borderedProminent)
                .padding(.horizontal, inset)
                .accessibilityIdentifier("home.shuffleAll")
        } else if !section.items.isEmpty {
            VStack(alignment: .leading, spacing: Spacing.smallMedium) {
                SectionHeader(title)
                    .padding(.horizontal, inset)
                Shelf(inset: inset) {
                    ForEach(section.items, id: \.key) { item in
                        tile(item, tileKey: "\(section.id)|\(item.key)")
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func tile(_ item: HomeItem, tileKey: String) -> some View {
        let button = Button {
            zoomSourceKey = tileKey
            onItemTap(item)
        } label: {
            switch onEnum(of: item) {
            case .albumItem(let it): AlbumTileLabel(album: it.album, subtitle: it.album.albumArtist ?? "Unknown")
            case .artistItem(let it): ArtistTileLabel(artist: it.albumArtist)
            case .playlistItem(let it): PlainTileLabel(title: it.playlist.name, subtitle: "Playlist", symbol: "music.note.list")
            case .smartPlaylistItem(let it): PlainTileLabel(title: it.smartPlaylistId.title, subtitle: "Playlist", symbol: it.smartPlaylistId.symbol)
            case .genreItem(let it): PlainTileLabel(title: it.genre.name, subtitle: "Genre", symbol: "guitars")
            }
        }
        .buttonStyle(.pressScale)
        switch onEnum(of: item) {
        case .albumItem(let it):
            button
                .zoomSource(id: Route.album(it.album).cacheKey, tileKey: tileKey, activeKey: zoomSourceKey)
                .accessibilityIdentifier("homeTile.album")
                .contextMenu { mediaActions(MediaSelectionAlbums(album: it.album)) }
        case .artistItem(let it):
            button
                .zoomSource(id: Route.albumArtist(albumArtistKey: it.albumArtist.groupKey.key).cacheKey, tileKey: tileKey, activeKey: zoomSourceKey)
                .accessibilityIdentifier("homeTile.artist")
                .contextMenu { mediaActions(MediaSelectionAlbumArtists(albumArtist: it.albumArtist)) }
        case .playlistItem:
            button.accessibilityIdentifier("homeTile.playlist")
        case .smartPlaylistItem:
            button.accessibilityIdentifier("homeTile.smartPlaylist")
        case .genreItem(let it):
            button
                .accessibilityIdentifier("homeTile.genre")
                .contextMenu { mediaActions(MediaSelectionGenres(genre: it.genre)) }
        }
    }

    static func title(_ title: HomeSectionTitle) -> String {
        switch title {
        case .jumpBackIn: "Jump Back In"
        case .thisMorning: "This Morning"
        case .thisAfternoon: "This Afternoon"
        case .tonight: "Tonight"
        case .onRepeat: "On Repeat"
        case .rediscover: "Rediscover"
        case .recentlyAdded: "Recently Added"
        case .genrePicks: "Genre Picks"
        case .shuffleAll: "Shuffle All"
        }
    }

    @ViewBuilder
    private func mediaActions(_ selection: MediaSelection) -> some View {
        Button("Play", systemImage: "play") { onPlay(selection) }
        Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") { onPlayNext(selection) }
        Button("Add to Queue", systemImage: "text.append") { onAddToQueue(selection) }
    }
}

/// A tile for an item without artwork of its own (a playlist, a smart playlist, a genre): a symbol placeholder at
/// the album tile's size, its title and a subtitle.
private struct PlainTileLabel: View {
    let title: String
    let subtitle: String
    let symbol: String

    @Environment(\.layoutTier) private var layoutTier

    var body: some View {
        let size = ArtworkSize.shelf(layoutTier)
        VStack(alignment: .leading, spacing: Spacing.xsmall) {
            ArtworkPlaceholder(symbol: symbol)
                .artworkTile(size, cornerRadius: ArtworkCorner.tile)
                .padding(.bottom, Spacing.xsmall)
            Text(title)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.primary)
                .lineLimit(1)
            Text(subtitle)
                .font(.caption)
                .foregroundStyle(.s2SecondaryText)
                .lineLimit(1)
        }
        .frame(width: size, alignment: .leading)
        .contentShape(Rectangle())
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

/// An artist tile's face: the artist's picture (`ArtworkCorner.tile`, as an album tile) at `ArtworkSize.artistShelf` with the name and album count centred beneath.
struct ArtistTileLabel: View {
    let artist: AlbumArtist

    var body: some View {
        VStack(spacing: Spacing.xsmall) {
            RemoteArtwork(.albumArtist(artist), points: ArtworkSize.artistShelf) {
                ArtworkPlaceholder(symbol: "person.fill")
            }
            .artworkTile(ArtworkSize.artistShelf, cornerRadius: ArtworkCorner.tile)
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

/// Which of several tiles showing one item is the zoom source for its screen. An item can be on more than one shelf
/// (Home's recently and most played, or an artist's albums pushed over Home), and a zoom from a tile the user didn't
/// touch would be wrong, as would two live sources sharing one id.
enum ZoomTile {
    /// `id` for the tile last tapped (`activeKey`); otherwise an id of the tile's own, which no screen zooms to.
    static func sourceID(_ id: String, tileKey: String, activeKey: String?) -> String {
        activeKey == tileKey ? id : "tile|\(tileKey)"
    }
}

extension View {
    /// The zoom source for `id` while this tile (`tileKey`) is the last one tapped (`activeKey`), per
    /// `ZoomTile.sourceID`. The id changes rather than the modifier coming and going, so the tile keeps its identity
    /// (and its loaded cover) when it's tapped.
    func zoomSource(id: String, tileKey: String, activeKey: String?) -> some View {
        zoomSource(id: ZoomTile.sourceID(id, tileKey: tileKey, activeKey: activeKey))
    }
}
