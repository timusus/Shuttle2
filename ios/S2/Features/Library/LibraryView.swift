import Shared
import SwiftUI

/// The Library tab's root on compact: a list of categories that pushes each one's list
/// (`Route.libraryCategory`), Music-style rather than Android's pager of tabs. Regular and wide show the
/// same categories directly in the sidebar instead (`AppShell`),
/// `docs/architecture/ios-port/phase-5-ios-app.md` section 2.
///
/// `LibraryViewModel`'s enabled tabs, in the user's order, pick the categories; `LibraryEmptyViewModel` swaps them
/// for the empty state while there are no songs; the import's progress shows below either. Pull to refresh imports.
/// The toolbar and the empty state's action open Sources.
struct LibraryView: View {
    let navigator: Navigator

    var body: some View {
        let models = ViewModelCache.shared.viewModel(AppTab.library.cacheKey) { LibraryRootModels(graph: AppGraph.shared) }
        Observing(models.library.uiState, models.empty.uiState, models.importState) { library, availability, importState in
            LibraryRootContent(
                categories: library.tabs.compactMap(LibraryCategory.init),
                availability: LibraryRootAvailability(availability),
                importStatus: ImportStatus(importState),
                emptyNote: Self.emptyNote
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

    private static var emptyNote: String? {
        #if DEBUG
            DebugServerStatus.shared.message
        #else
            nil
        #endif
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

/// The library import, as a row under the root's list: running (with the provider's latest message and how far
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

/// The Library root's content, from plain values.
struct LibraryRootContent: View {
    let categories: [LibraryCategory]
    let availability: LibraryRootAvailability
    let importStatus: ImportStatus
    var emptyNote: String?

    var body: some View {
        List {
            if availability == .hasMusic {
                Section {
                    ForEach(categories, id: \.self) { category in
                        NavigationLink(value: Route.libraryCategory(category)) {
                            Label(category.title, systemImage: category.systemImage)
                        }
                    }
                }
            }
            if importStatus != .idle {
                Section {
                    ImportStatusRow(status: importStatus)
                }
            }
        }
        .overlay {
            switch availability {
            case .loading:
                ProgressView()
            case .empty where !importStatus.isImporting:
                ContentUnavailableView {
                    Label("No Music", systemImage: "music.note.house")
                } description: {
                    Text(emptyNote ?? "Connect a Jellyfin, Emby or Plex server to stream your music.")
                } actions: {
                    NavigationLink("Add a Source", value: Route.sources)
                        .buttonStyle(.borderedProminent)
                        .accessibilityIdentifier("libraryEmpty.addSource")
                }
            case .empty, .hasMusic:
                EmptyView()
            }
        }
        .toolbar {
            // Sources lives here until Settings exists (phase 7), where it moves as on Android.
            ToolbarItem(placement: .primaryAction) {
                NavigationLink(value: Route.sources) {
                    Label("Sources", systemImage: "server.rack")
                }
                .accessibilityIdentifier("library.sources")
            }
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
            VStack(alignment: .leading, spacing: 6) {
                Text("Importing from \(provider)…")
                if let fraction {
                    ProgressView(value: fraction)
                } else {
                    ProgressView().frame(maxWidth: .infinity, alignment: .leading)
                }
                if let message {
                    Text(message).font(.caption).foregroundStyle(.secondary).lineLimit(1)
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
