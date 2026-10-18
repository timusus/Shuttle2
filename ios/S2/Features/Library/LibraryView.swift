import Shared
import SwiftUI

/// Categories swap in place rather than push, so each category's view model stays cached while shown.
struct LibraryView: View {
    let navigator: Navigator

    @Environment(\.appServices) private var appServices
    @AppStorage("library.category") private var storedCategory: String = ""

    private var services: AppServices { appServices.required }

    var body: some View {
        let _ = StartupTrace.mark(.library, .body)
        let models = services.viewModels.viewModel(AppTab.library.cacheKey) { LibraryRootModels(graph: services.graph) }
        Observing(models.library.uiState, models.empty.uiState, models.importState) { library, availability, importStatus in
            let _ = StartupTrace.content(.library, loaded: LibraryRootAvailability(availability) != .loading)
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
        .onAppear { StartupTrace.mark(.library, .appear) }
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
    @Environment(\.appServices) private var appServices

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
                    EmptyState("No Music", systemImage: "music.note.house", message: "Connect a Jellyfin, Emby, Plex or Navidrome server to stream your music.") {
                        NavigationLink("Add a Source", value: Route.sources)
                            .accessibilityIdentifier("libraryEmpty.addSource")
                    }
                    .containerRelativeFrame(.vertical)
                }
                .refreshable { LibraryImport.refresh(graph: appServices.required.graph) }
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
                    // No category to draw: nothing replaces this, so a skeleton would promise a layout that never comes.
                    Color.clear
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
                FilterChip(title: category.title, isSelected: category == selection, identifier: "libraryChip.\(category.rawValue)") {
                    onSelect(category)
                }
            }
        }
        .font(.subheadline.weight(.semibold))
        .padding(.horizontal, AdaptiveLayout.contentInset(layoutTier))
    }
}

extension View {
    /// A `safeAreaInset` on iOS 26 would sit in a blurred band under the scroll edge effect, so the bar goes in a `safeAreaBar`.
    @ViewBuilder
    func pinnedTopBar<Bar: View>(@ViewBuilder _ bar: () -> Bar) -> some View {
        if #available(iOS 26, *) {
            scrollEdgeEffectStyle(.hard, for: .top)
                .safeAreaBar(edge: .top, spacing: 0, content: bar)
        } else {
            safeAreaInset(edge: .top, spacing: 0, content: bar)
        }
    }

    /// A large title would be left as a ghost under the iOS 26 scroll edge effect when the scroll view is swapped in place.
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
            case .card: S2Shape.control.cornerRadius ?? 0
            case .large: (S2Shape.control.cornerRadius ?? 0) + Spacing.tiny
            }
        }

        var glyph: CGFloat {
            switch self {
            case .settings: IconSize.small
            case .card: IconSize.medium
            case .large: IconSize.large
            }
        }
    }

    /// What it draws: an SF Symbol, or a template image from the asset catalog where no symbol fits (a server's mark).
    enum Glyph: Equatable {
        case system(String)
        case asset(String)
    }

    let glyph: Glyph
    let style: Style
    var size: Size = .settings

    init(systemImage: String, style: Style, size: Size = .settings) {
        self.init(glyph: .system(systemImage), style: style, size: size)
    }

    init(glyph: Glyph, style: Style, size: Size = .settings) {
        self.glyph = glyph
        self.style = style
        self.size = size
    }

    @Environment(\.artworkTint) private var tint
    /// Grows with the text, up to about half again at the largest sizes, as iOS Settings' icons do.
    @ScaledMetric(relativeTo: .body) private var textScale: CGFloat = 1

    var body: some View {
        let scale = min(textScale, 1.5)
        let side = size.points * scale
        let shape = RoundedRectangle(cornerRadius: size.cornerRadius * scale, style: .continuous)
        glyphImage(scale: scale)
            .foregroundStyle(foreground)
            .frame(width: side, height: side)
            .background(shape.fill(background))
            .accessibilityHidden(true)
    }

    @ViewBuilder private func glyphImage(scale: CGFloat) -> some View {
        switch glyph {
        case .system(let name):
            Image(systemName: name)
                .font(.s2ScaledGlyph(size.glyph * scale, weight: .medium))
        case .asset(let name):
            Image(name)
                .resizable()
                .scaledToFit()
                .frame(width: size.glyph * scale, height: size.glyph * scale)
        }
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

/// What's playing, as the library lists mark it: the current song, and the album and album artists it belongs to.
/// `albumArtistKey` is the album's own key's artist part, as tagged ("A, B"); `albumArtistKeys` are the artists whose
/// album it is, each marked in the artist list.
struct LibraryNowPlaying: Equatable {
    var songId: Int64?
    var albumKey: String?
    var albumArtistKey: String?
    var albumArtistKeys: [String] = []
    var albumIdentity: String?
    var isPlaying = false

    static let none = LibraryNowPlaying()

    init(songId: Int64? = nil, albumKey: String? = nil, albumArtistKey: String? = nil, albumArtistKeys: [String] = [], albumIdentity: String? = nil, isPlaying: Bool = false) {
        self.songId = songId
        self.albumKey = albumKey
        self.albumArtistKey = albumArtistKey
        self.albumArtistKeys = albumArtistKeys
        self.albumIdentity = albumIdentity
        self.isPlaying = isPlaying
    }

    init(queue: QueueState, playback: PlaybackState) {
        let song = queue.currentItem?.song
        self.init(
            songId: song?.id,
            albumKey: song?.albumGroupKey.key,
            albumArtistKey: song?.albumArtistGroupKey.key,
            albumArtistKeys: song?.albumArtistKeys.compactMap(\.key) ?? [],
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
        state(albumArtist.groupKey.key.map(albumArtistKeys.contains) == true)
    }
}

/// Reads what's playing from the player (`IosPlayerController`) for a library list.
struct LibraryNowPlayingReader<Content: View>: View {
    @ViewBuilder let content: (LibraryNowPlaying) -> Content

    @Environment(\.appServices) private var appServices

    var body: some View {
        let player = appServices.required.graph.playerController
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
                    Text(message).font(.caption).foregroundStyle(.s2TextSecondary).lineLimit(1)
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
