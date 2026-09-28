import Shared
import SwiftUI

/// The Library tab's root on compact: a pinned rail of category chips over the chosen category's own screen, shown in
/// place with its chip tinted, as Android's library tabs switch in place (#643). It opens on the category last
/// chosen, kept across launches, or the first enabled one. Regular and wide show the same categories directly in the
/// sidebar instead (`AppShell`), `docs/architecture/ios-port/phase-5-ios-app.md` section 2.
///
/// `LibraryViewModel`'s enabled tabs, in the user's order, pick the categories; `LibraryEmptyViewModel` swaps them
/// for the empty state while there are no songs; the import's progress shows under the rail. Each category's own
/// list pulls to refresh; the rail doesn't. A category's view model stays cached while shown here: every
/// `Route.libraryCategory` key is always live (`Navigator.retainViewModels`).
struct LibraryView: View {
    let navigator: Navigator

    @AppStorage("library.category") private var storedCategory: String = ""

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
        .navigationTitle(AppTab.library.title)
        .inlineTitleUnderEdgeEffect()
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

/// The library import, as a row under the root's chips: running (with the provider's latest message and how far
/// through it is, if it knows), or failed. Idle, and a clean finish, show nothing.
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
}

/// The Library root's content, from plain values. `categoryContent` draws a category's screen (by default its route's
/// destination, the same screen a push shows).
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

    /// The category shown: the one last chosen, if the user still has it enabled, else their first.
    var shownCategory: LibraryCategory? {
        if let selection, categories.contains(selection) { return selection }
        return categories.first
    }

    var body: some View {
        Group {
            switch availability {
            case .empty where !importStatus.isImporting:
                ScrollView {
                    EmptyState("No Music", systemImage: "music.note.house", message: "Connect a Jellyfin or Emby server to stream your music.") {
                        NavigationLink("Add a Source", value: Route.sources)
                            .accessibilityIdentifier("libraryEmpty.addSource")
                    }
                    .containerRelativeFrame(.vertical)
                }
                .refreshable { LibraryImport.refresh() }
            case .empty:
                // The first import: its progress, under the (empty) rail, until there's music to show.
                Color.clear
            case .loading, .hasMusic:
                // While the library is still loading, the category's own screen shows its skeleton: the grid or list
                // it's about to draw.
                if let shownCategory {
                    categoryContent(shownCategory)
                        .id(shownCategory)
                        .transition(.opacity)
                } else {
                    LibraryListSkeleton()
                }
            }
        }
        .pinnedTopBar { categoryRail }
    }

    /// The chip rail pinned over the content, and the import's progress under it. Its own property so tests can
    /// reach it: they can't see into iOS 26's `safeAreaBar`.
    var categoryRail: some View {
        VStack(alignment: .leading, spacing: 0) {
            if availability != .empty, !categories.isEmpty {
                LibraryCategoryChips(categories: categories, selection: shownCategory, onSelect: select)
            }
            if importStatus != .idle {
                ImportStatusRow(status: importStatus)
                    .padding(.horizontal, Spacing.medium)
                    .padding(.vertical, Spacing.small)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .pinnedBarBackground()
    }

    private func select(_ category: LibraryCategory) {
        guard category != shownCategory else { return }
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

/// The pinned rail of category chips, scrolling sideways only. The chosen one is filled with the tint and scrolled
/// to the middle, so its neighbours are in reach.
struct LibraryCategoryChips: View {
    let categories: [LibraryCategory]
    let selection: LibraryCategory?
    let onSelect: (LibraryCategory) -> Void

    @Environment(\.layoutTier) private var layoutTier
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                chips
            }
            // Sideways only: no bounce when the chips fit, and never a vertical drag.
            .scrollBounceBehavior(.basedOnSize, axes: .horizontal)
            .scrollBounceBehavior(.basedOnSize, axes: .vertical)
            .onAppear { reveal(selection, proxy, animated: false) }
            .onChange(of: selection) { _, chosen in reveal(chosen, proxy, animated: true) }
        }
        // Outside the scroll view, so it doesn't reach the navigation bar: a scroll view touching the top safe area
        // extends under the bar, and on iOS 26 the bar's scroll edge effect then covers its whole content.
        .padding(.vertical, Spacing.small)
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Categories")
        .accessibilityIdentifier("libraryChips")
    }

    private func reveal(_ category: LibraryCategory?, _ proxy: ScrollViewProxy, animated: Bool) {
        guard let category else { return }
        withAnimation(animated ? Motion.press.reduced(reduceMotion) : nil) { proxy.scrollTo(category, anchor: .center) }
    }

    private var chips: some View {
        HStack(spacing: Spacing.small) {
            ForEach(categories, id: \.self) { category in
                LibraryCategoryChip(category: category, isSelected: category == selection) {
                    onSelect(category)
                }
            }
        }
        .font(.subheadline.weight(.semibold))
        .padding(.horizontal, AdaptiveLayout.contentInset(layoutTier))
    }
}

extension View {
    /// Pins `bar` along the top of this screen, under the navigation bar, with the content scrolling beneath it.
    /// iOS 26 needs `safeAreaBar`: the content's scroll edge effect covers the scroll view's whole top inset, so a
    /// `safeAreaInset` bar sits in a blurred band (#624), where a `safeAreaBar` extends the effect under itself and
    /// draws on top of it. The effect is the hard style, as for any pinned header of controls: the soft one lets
    /// artwork show through the bar, washing out its chips. Earlier releases have no edge effect.
    @ViewBuilder
    func pinnedTopBar<Bar: View>(@ViewBuilder _ bar: () -> Bar) -> some View {
        if #available(iOS 26, *) {
            scrollEdgeEffectStyle(.hard, for: .top)
                .safeAreaBar(edge: .top, spacing: 0, content: bar)
        } else {
            safeAreaInset(edge: .top, spacing: 0, content: bar)
        }
    }

    /// An inline navigation title on iOS 26. There, a large title over a screen whose scroll view is swapped in place
    /// (the Library's categories, and their grid and list) is left behind the content's scroll edge effect, a ghost
    /// of itself above the pinned bar; an inline title sits in the navigation bar, clear of the effect, at any scroll
    /// position. `inlineLarge` keeps it leading on every screen, however many toolbar items it has (a plain inline
    /// title centres itself when there's room, so Genres' sat apart from Songs', #643). Earlier releases keep the
    /// large title.
    @ViewBuilder
    func inlineTitleUnderEdgeEffect() -> some View {
        if #available(iOS 26, *) {
            toolbarTitleDisplayMode(.inlineLarge)
        } else {
            self
        }
    }

    /// A `pinnedTopBar`'s background: the bar material before iOS 26; none on iOS 26, where the content's scroll edge
    /// effect, extended under the bar, separates the two.
    @ViewBuilder
    func pinnedBarBackground() -> some View {
        if #available(iOS 26, *) {
            self
        } else {
            background(.bar)
        }
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
            Text(category.title)
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

/// An SF Symbol in a rounded square: `.filled` is iOS Settings' white glyph on a colour, `.tinted` a glyph in the
/// colour on a wash of it. Both scale with Dynamic Type.
struct IconSquare: View {
    enum Style {
        case filled(Color)
        /// The tint in scope (`\.artworkTint`, the accent outside a tinted screen).
        case tinted
    }

    enum Size {
        /// iOS Settings' 29 pt square, radius 7.
        case settings
        /// A source's, in onboarding.
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
    var albumIdentity: String?
    var isPlaying = false

    static let none = LibraryNowPlaying()

    init(songId: Int64? = nil, albumKey: String? = nil, albumArtistKey: String? = nil, albumIdentity: String? = nil, isPlaying: Bool = false) {
        self.songId = songId
        self.albumKey = albumKey
        self.albumArtistKey = albumArtistKey
        self.albumIdentity = albumIdentity
        self.isPlaying = isPlaying
    }

    init(queue: QueueState, playback: PlaybackState) {
        let song = queue.currentItem?.song
        self.init(
            songId: song?.id,
            albumKey: song?.albumGroupKey.key,
            albumArtistKey: song?.albumArtistGroupKey.key,
            albumIdentity: song?.albumGroupKey.identity,
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
        state(albumKey != nil && album.groupKey?.key == albumKey && album.groupKey?.albumArtistGroupKey?.key == albumArtistKey && album.groupKey?.identity == albumIdentity)
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
