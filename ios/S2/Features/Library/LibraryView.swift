import Shared
import SwiftUI

/// The Library tab's root on compact: a pinned rail of category chips over a two-column grid of category cards.
/// Choosing a card or a chip shows that category's own screen in place, under the rail, with the chip tinted, as
/// Android's library tabs switch in place; choosing the selected chip again, or the rail's leading close button,
/// goes back to the cards. Regular and wide show the same categories directly in the sidebar instead (`AppShell`),
/// `docs/architecture/ios-port/phase-5-ios-app.md` section 2.
///
/// `LibraryViewModel`'s enabled tabs, in the user's order, pick the categories; `LibraryEmptyViewModel` swaps them
/// for the empty state while there are no songs; the import's progress shows above the cards. Pull to refresh
/// imports. The chosen category is kept per scene. A category's view model stays cached while shown here: every
/// `Route.libraryCategory` key is always live (`Navigator.retainViewModels`).
struct LibraryView: View {
    let navigator: Navigator

    @SceneStorage("library.category") private var storedCategory: String = ""

    var body: some View {
        let models = ViewModelCache.shared.viewModel(AppTab.library.cacheKey) { LibraryRootModels(graph: AppGraph.shared) }
        Observing(models.library.uiState, models.empty.uiState, models.importState) { library, availability, importStatus in
            LibraryRootContent(
                categories: library.tabs.compactMap(LibraryCategory.init),
                availability: LibraryRootAvailability(availability),
                importStatus: ImportStatus(importStatus),
                selection: selection
            )
        }
        .task {
            // iOS reads its servers without a permission, so the "music permission" the shared empty state waits on
            // is always held; this also moves it past Loading.
            models.empty.onAccessChecked(granted: true, showRationale: false)
        }
        .refreshable { LibraryImport.refresh() }
        .navigationTitle(AppTab.library.title)
    }

    private var selection: Binding<LibraryCategory?> {
        Binding(
            get: { LibraryCategory(rawValue: storedCategory) },
            set: { storedCategory = $0?.rawValue ?? "" }
        )
    }
}

/// The Library root's ViewModels, cached together under the Library tab's key.
final class LibraryRootModels: ViewModelGroup {
    let library: LibraryViewModel
    let empty: LibraryEmptyViewModel
    let importState: SkieSwiftStateFlow<SongImportState>

    init(graph: IosAppGraph) {
        library = graph.libraryViewModel
        empty = graph.libraryEmptyViewModel
        importState = graph.songImportStateProvider.songImportState
    }

    var members: [Lifecycle_viewmodelViewModel] { [library, empty] }
}

/// `LibraryAvailability` as the root shows it: the empty state's music-access detail doesn't apply on iOS.
enum LibraryRootAvailability: Equatable {
    case loading
    case hasMusic
    case empty

    init(_ availability: LibraryAvailability) {
        switch onEnum(of: availability) {
        case .loading: self = .loading
        case .hasMusic: self = .hasMusic
        case .empty: self = .empty
        }
    }
}

/// The library import, as a card above the root's categories: running (with the provider's latest message and how
/// far through it is, if it knows), or failed. Idle, and a clean finish, show nothing.
enum ImportStatus: Equatable {
    case idle
    case importing(provider: String, message: String?, fraction: Double?)
    case failed(provider: String, error: String)

    init(_ state: SongImportState) {
        switch onEnum(of: state) {
        case .idle:
            self = .idle
        case .importProgress(let progress):
            self = .importing(
                provider: progress.providerType.name,
                message: progress.message,
                fraction: progress.progress.map { Double($0.asFloat()) }
            )
        case .importComplete(let complete):
            if let error = complete.error {
                self = .failed(provider: complete.providerType.name, error: error)
            } else {
                self = .idle
            }
        }
    }

    var isImporting: Bool {
        if case .importing = self { true } else { false }
    }
}

extension LibraryCategory {
    /// Android's `LibraryTab`; Folders has no iOS category yet.
    init?(_ tab: CoreLibraryTab) {
        switch tab {
        case .songs: self = .songs
        case .albums: self = .albums
        case .artists: self = .albumArtists
        case .genres: self = .genres
        case .playlists: self = .playlists
        case .folders: return nil
        }
    }

    /// A chip's short title: "Artists" rather than the category's "Album Artists", so the rail fits a phone.
    var chipTitle: String {
        self == .albumArtists ? "Artists" : title
    }
}

/// The Library root's content, from plain values. `categoryContent` draws a chosen category's screen (by default its
/// route's destination, the same screen a push shows).
struct LibraryRootContent<CategoryContent: View>: View {
    let categories: [LibraryCategory]
    let availability: LibraryRootAvailability
    let importStatus: ImportStatus
    @Binding var selection: LibraryCategory?
    @ViewBuilder let categoryContent: (LibraryCategory) -> CategoryContent

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(
        categories: [LibraryCategory],
        availability: LibraryRootAvailability,
        importStatus: ImportStatus,
        selection: Binding<LibraryCategory?>,
        @ViewBuilder categoryContent: @escaping (LibraryCategory) -> CategoryContent
    ) {
        self.categories = categories
        self.availability = availability
        self.importStatus = importStatus
        _selection = selection
        self.categoryContent = categoryContent
    }

    /// The chosen category, if the user still has it enabled.
    private var shownCategory: LibraryCategory? {
        guard availability == .hasMusic, let selection, categories.contains(selection) else { return nil }
        return selection
    }

    var body: some View {
        Group {
            switch availability {
            case .loading:
                LibraryCategorySkeleton()
            case .empty where !importStatus.isImporting:
                EmptyState("No Music", systemImage: "music.note.house", message: "Connect a Jellyfin or Emby server to stream your music.") {
                    NavigationLink("Add a Source", value: Route.sources)
                        .accessibilityIdentifier("libraryEmpty.addSource")
                }
            case .empty:
                LibraryCategoryGrid(categories: [], importStatus: importStatus, onSelect: select)
            case .hasMusic:
                if let shownCategory {
                    categoryContent(shownCategory)
                        .id(shownCategory)
                        .transition(.opacity)
                } else {
                    LibraryCategoryGrid(categories: categories, importStatus: importStatus, onSelect: select)
                        .transition(.opacity)
                }
            }
        }
        .safeAreaInset(edge: .top, spacing: 0) {
            if availability == .hasMusic, !categories.isEmpty {
                LibraryCategoryChips(categories: categories, selection: shownCategory, onSelect: select)
            }
        }
    }

    /// Shows `category`, or the cards for nil.
    private func select(_ category: LibraryCategory?) {
        withAnimation(Motion.press.reduced(reduceMotion)) { selection = category }
    }
}

extension LibraryRootContent where CategoryContent == RouteDestinationView {
    init(
        categories: [LibraryCategory],
        availability: LibraryRootAvailability,
        importStatus: ImportStatus,
        selection: Binding<LibraryCategory?> = .constant(nil)
    ) {
        self.init(categories: categories, availability: availability, importStatus: importStatus, selection: selection) { category in
            RouteDestinationView(route: .libraryCategory(category))
        }
    }
}

/// The pinned rail of category chips. The chosen one is filled with the tint; choosing it again, or the leading close
/// button, clears the choice.
struct LibraryCategoryChips: View {
    let categories: [LibraryCategory]
    let selection: LibraryCategory?
    let onSelect: (LibraryCategory?) -> Void

    @Environment(\.layoutTier) private var layoutTier

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.small) {
                if selection != nil {
                    Button { onSelect(nil) } label: {
                        Image(systemName: "xmark")
                            .fontWeight(.semibold)
                            .padding(Spacing.small)
                            .background(Circle().fill(Color(.tertiarySystemFill)))
                    }
                    .buttonStyle(.pressScale)
                    .foregroundStyle(.primary)
                    .accessibilityLabel("All Categories")
                    .accessibilityIdentifier("libraryChip.all")
                    .transition(.scale.combined(with: .opacity))
                }
                ForEach(categories, id: \.self) { category in
                    LibraryCategoryChip(category: category, isSelected: category == selection) {
                        onSelect(category == selection ? nil : category)
                    }
                }
            }
            .font(.subheadline.weight(.semibold))
            .padding(.horizontal, AdaptiveLayout.contentInset(layoutTier))
            .padding(.vertical, Spacing.small)
        }
        .background(.bar)
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Categories")
    }
}

private struct LibraryCategoryChip: View {
    let category: LibraryCategory
    let isSelected: Bool
    let action: () -> Void

    @Environment(\.artworkTint) private var tint
    @Environment(\.artworkTintInk) private var ink

    var body: some View {
        Button(action: action) {
            Text(category.chipTitle)
                .lineLimit(1)
                .padding(.horizontal, Spacing.smallMedium + Spacing.xsmall)
                .padding(.vertical, Spacing.small)
                .foregroundStyle(isSelected ? ink : .primary)
                .background(Capsule().fill(isSelected ? AnyShapeStyle(tint) : AnyShapeStyle(Color(.tertiarySystemFill))))
                .contentShape(Capsule())
        }
        .buttonStyle(.pressScale)
        .accessibilityLabel(category.title)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
        .accessibilityIdentifier("libraryChip.\(category.rawValue)")
    }
}

/// The two-column grid of category cards (one column at the accessibility text sizes), with the import's progress
/// above it.
private struct LibraryCategoryGrid: View {
    let categories: [LibraryCategory]
    let importStatus: ImportStatus
    let onSelect: (LibraryCategory) -> Void

    @Environment(\.layoutTier) private var layoutTier
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    private var columns: [GridItem] {
        if dynamicTypeSize.isAccessibilitySize {
            return [GridItem(.flexible())]
        }
        if layoutTier != .compact {
            return [GridItem(.adaptive(minimum: ArtworkSize.gridMinimum), spacing: AdaptiveLayout.gridSpacing)]
        }
        return Array(repeating: GridItem(.flexible(), spacing: AdaptiveLayout.gridSpacing), count: 2)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.medium) {
                if importStatus != .idle {
                    ImportStatusRow(status: importStatus)
                        .padding(Spacing.medium)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(
                            RoundedRectangle(cornerRadius: ArtworkCorner.tile, style: .continuous)
                                .fill(Color(.secondarySystemGroupedBackground))
                        )
                }
                LazyVGrid(columns: columns, spacing: AdaptiveLayout.gridSpacing) {
                    ForEach(categories, id: \.self) { category in
                        LibraryCategoryCard(category: category) { onSelect(category) }
                    }
                }
            }
            .padding(.horizontal, AdaptiveLayout.contentInset(layoutTier))
            .padding(.vertical, Spacing.medium)
            .frame(maxWidth: AdaptiveLayout.contentMaxWidth)
            .frame(maxWidth: .infinity)
        }
        .background(Color(.systemGroupedBackground))
    }
}

/// A category card: its symbol in a tinted rounded square over its title.
private struct LibraryCategoryCard: View {
    let category: LibraryCategory
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: Spacing.smallMedium) {
                IconSquare(systemImage: category.systemImage, style: .tinted, size: .card)
                Text(category.title)
                    .font(.s2Headline)
                    .foregroundStyle(.primary)
                    .multilineTextAlignment(.leading)
                    .lineLimit(2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(Spacing.medium)
            .background(
                RoundedRectangle(cornerRadius: ArtworkCorner.tile, style: .continuous)
                    .fill(Color(.secondarySystemGroupedBackground))
            )
            .contentShape(RoundedRectangle(cornerRadius: ArtworkCorner.tile, style: .continuous))
        }
        .buttonStyle(.pressScale)
        .accessibilityLabel(category.title)
        .accessibilityIdentifier("libraryCategory.\(category.rawValue)")
    }
}

/// The cards' skeleton while the library's availability is still loading.
private struct LibraryCategorySkeleton: View {
    @Environment(\.layoutTier) private var layoutTier
    @ScaledMetric(relativeTo: .headline) private var cardHeight: CGFloat = 96

    var body: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: AdaptiveLayout.gridSpacing), count: 2), spacing: AdaptiveLayout.gridSpacing) {
            ForEach(LibraryCategory.allCases, id: \.self) { _ in
                RoundedRectangle(cornerRadius: ArtworkCorner.tile, style: .continuous)
                    .fill(Color(.secondarySystemGroupedBackground))
                    .frame(height: cardHeight)
            }
        }
        .shimmer()
        .padding(.horizontal, AdaptiveLayout.contentInset(layoutTier))
        .padding(.vertical, Spacing.medium)
        .frame(maxHeight: .infinity, alignment: .top)
        .background(Color(.systemGroupedBackground))
        .accessibilityElement()
        .accessibilityLabel("Loading")
    }
}

/// An SF Symbol in a rounded square: `.filled` is iOS Settings' white glyph on a colour, `.tinted` a glyph in the
/// colour on a wash of it (the Library's category cards). Both scale with Dynamic Type.
struct IconSquare: View {
    enum Style {
        case filled(Color)
        /// The tint in scope (`\.artworkTint`, the accent outside a tinted screen).
        case tinted
    }

    enum Size {
        /// iOS Settings' 29 pt square, radius 7.
        case settings
        /// A Library category card's.
        case card
        /// A server's, in Sources and at the top of its sign-in.
        case large

        var points: CGFloat {
            switch self {
            case .settings: 29
            case .card: 40
            case .large: 48
            }
        }

        var cornerRadius: CGFloat {
            switch self {
            case .settings: 7
            case .card: ArtworkCorner.row + Spacing.tiny
            case .large: ArtworkCorner.row + Spacing.xsmall
            }
        }

        var glyph: CGFloat {
            switch self {
            case .settings: 17
            case .card: 20
            case .large: 24
            }
        }
    }

    let systemImage: String
    let style: Style
    var size: Size = .settings

    @Environment(\.artworkTint) private var tint
    /// Grows with the text, up to about half again at the largest sizes, as iOS Settings' icons do.
    @ScaledMetric(relativeTo: .body) private var textScale: CGFloat = 1

    var body: some View {
        let scale = min(textScale, 1.5)
        let side = size.points * scale
        let shape = RoundedRectangle(cornerRadius: size.cornerRadius * scale, style: .continuous)
        Image(systemName: systemImage)
            .font(.system(size: size.glyph * scale, weight: .medium))
            .foregroundStyle(foreground)
            .frame(width: side, height: side)
            .background(shape.fill(background))
            .accessibilityHidden(true)
    }

    private var foreground: Color {
        switch style {
        case .filled: .white
        case .tinted: tint
        }
    }

    private var background: AnyShapeStyle {
        switch style {
        case .filled(let color): AnyShapeStyle(color.gradient)
        case .tinted: AnyShapeStyle(tint.opacity(0.15))
        }
    }
}

/// What's playing, as the library lists mark it: the current song, and the album and album artist it belongs to.
struct LibraryNowPlaying: Equatable {
    var songId: Int64?
    var albumKey: String?
    var albumArtistKey: String?
    var isPlaying = false

    static let none = LibraryNowPlaying()

    init(songId: Int64? = nil, albumKey: String? = nil, albumArtistKey: String? = nil, isPlaying: Bool = false) {
        self.songId = songId
        self.albumKey = albumKey
        self.albumArtistKey = albumArtistKey
        self.isPlaying = isPlaying
    }

    init(queue: QueueState, playback: PlaybackState) {
        let song = queue.currentItem?.song
        self.init(
            songId: song?.id,
            albumKey: song?.albumGroupKey.key,
            albumArtistKey: song?.albumArtistGroupKey.key,
            isPlaying: playback is PlaybackState.Playing
        )
    }

    private func state(_ matches: Bool) -> MediaRowPlayback {
        guard matches else { return .none }
        return isPlaying ? .playing : .paused
    }

    func playback(song: Song) -> MediaRowPlayback {
        state(songId != nil && song.id == songId)
    }

    func playback(album: Album) -> MediaRowPlayback {
        state(albumKey != nil && album.groupKey?.key == albumKey && album.groupKey?.albumArtistGroupKey?.key == albumArtistKey)
    }

    func playback(albumArtist: AlbumArtist) -> MediaRowPlayback {
        state(albumArtistKey != nil && albumArtist.groupKey.key == albumArtistKey)
    }
}

/// Reads what's playing from the player (`IosPlayerController`) for a library list.
struct LibraryNowPlayingReader<Content: View>: View {
    @ViewBuilder let content: (LibraryNowPlaying) -> Content

    var body: some View {
        let player = AppGraph.shared.playerController
        Observing(player.queueOperations.queueStateFlow, player.playbackStateFlow) { queue, playback in
            content(LibraryNowPlaying(queue: queue, playback: playback))
        }
    }
}

struct ImportStatusRow: View {
    let status: ImportStatus

    var body: some View {
        switch status {
        case .idle:
            EmptyView()
        case .importing(let provider, let message, let fraction):
            VStack(alignment: .leading, spacing: Spacing.small) {
                Text("Importing from \(provider)…")
                if let fraction {
                    ProgressView(value: fraction)
                } else {
                    ProgressView().frame(maxWidth: .infinity, alignment: .leading)
                }
                if let message {
                    Text(message).font(.caption).foregroundStyle(.s2SecondaryText).lineLimit(1)
                }
            }
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("importStatus")
        case .failed(let provider, let error):
            Label("\(provider) import failed: \(error)", systemImage: "exclamationmark.triangle")
                .foregroundStyle(.secondary)
                .accessibilityIdentifier("importStatus")
        }
    }
}
