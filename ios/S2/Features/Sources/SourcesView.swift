import Shared
import SwiftUI

/// Sources (#587, #624, phase 7 in `docs/architecture/ios-port/phase-5-ios-app.md`): the media servers the library
/// imports from, on the shared `SourcesViewModel`, each with its import's status, and a rescan. Add a Server and a
/// server's Sign In Again open the source setup (`SourceSetupFlow`) in a sheet, the same cards, sign-in and import
/// progress the first run shows. A connected server's row offers what Android's server dialog does: sign in again, or
/// remove it. This device's folders are phase 8 (local files), so Android's "This device" and folder groups have no
/// iOS rows yet.
struct SourcesView: View {
    /// Absent only outside the shell (previews); every stack in `AppShell` has it.
    @Environment(Navigator.self) private var navigator: Navigator?
    /// Where the setup sheet opens, while it's up.
    @State private var setup: SourceSetupStart?

    var body: some View {
        let models = ViewModelCache.shared.viewModel(Route.sources.cacheKey) { SourcesModels(graph: AppGraph.shared) }
        Observing(models.sources.uiState, models.importState) { state, importState in
            SourcesContent(
                state: SourcesState(state, importStatus: ImportStatus(importState)),
                onAddServer: { setup = .chooseSource },
                onSignIn: { setup = .signIn($0) },
                onRemove: { models.sources.onRemoveServer(type: $0) },
                onRescan: { models.sources.onRescan() }
            )
            // The only event is an exclude folder off this device's storage, which iOS has no folders to raise.
            .consumeEvents(state.events, handled: { models.sources.onEventHandled(id: $0) }) { _ in }
        }
        .sheet(item: $setup) { start in
            SourceSetupFlow(start: start, navigator: navigator, onClose: { setup = nil })
        }
        .navigationTitle("Sources")
    }
}

/// Sources' ViewModel and the import's state, cached together under its route's key.
final class SourcesModels: ViewModelGroup {
    let sources: SourcesViewModel
    let importState: SkieSwiftStateFlow<SongImportState>

    init(graph: IosAppGraph) {
        sources = graph.sourcesViewModel
        importState = graph.songImportStateProvider.songImportState
    }

    var members: [Lifecycle_viewmodelViewModel] { [sources] }
}

/// `SourcesUiState` as iOS shows it: the connected servers, each one's import, the scan, and when the library last
/// finished importing.
struct SourcesState: Equatable {
    var servers: [MediaProviderType]
    var scan: Scan
    var importStatus: ImportStatus
    var lastImport: Date?

    enum Scan: Equatable {
        case idle
        case scanning(message: String?, fraction: Double?)
        case failed(String)
    }

    /// A connected server's row status.
    enum ServerStatus: Equatable {
        case connected
        case importing(fraction: Double?)
        case failed(String)
    }

    init(servers: [MediaProviderType], scan: Scan = .idle, importStatus: ImportStatus = .idle, lastImport: Date? = nil) {
        self.servers = servers
        self.scan = scan
        self.importStatus = importStatus
        self.lastImport = lastImport
    }

    init(_ state: SourcesUiState, importStatus: ImportStatus = .idle) {
        servers = state.servers.filter(\.connected).map(\.type)
        if let progress = state.scan {
            scan = .scanning(message: progress.message, fraction: progress.fraction.map { Double($0.floatValue) })
        } else if let error = state.scanError {
            scan = .failed(error)
        } else {
            scan = .idle
        }
        self.importStatus = importStatus
        lastImport = state.lastImport.map { Date(timeIntervalSince1970: TimeInterval($0.toEpochMilliseconds()) / 1000) }
    }

    /// `type`'s import running or failed, else plain connected: the importer reports one provider at a time.
    func status(of type: MediaProviderType) -> ServerStatus {
        switch importStatus {
        case .importing(let provider, _, let fraction) where provider == type.name: .importing(fraction: fraction)
        case .failed(let provider, let error) where provider == type.name: .failed(error)
        default: .connected
        }
    }
}

extension MediaProviderType {
    /// The server types the setup offers on iOS: the shared `ServerTypes` less Plex, whose provider isn't in
    /// `:shared` yet, so its sign-in has no authentication to run.
    static var signInTypes: [MediaProviderType] {
        SourcesViewModelKt.ServerTypes.filter { $0 != .plex }
    }

    /// The name Sources and the setup show (Android's `titleRes`).
    var title: String {
        switch self {
        case .shuttle: "S2 scanner"
        case .mediaStore: "Android media store"
        case .emby: "Emby"
        case .jellyfin: "Jellyfin"
        case .plex: "Plex"
        }
    }

    /// The glyph Sources, the setup and sign-in draw for it.
    var symbol: String {
        switch self {
        case .shuttle, .mediaStore: "iphone"
        case .emby, .jellyfin, .plex: "server.rack"
        }
    }

    /// Its glyph's colour, near the brand's own.
    var color: Color {
        switch self {
        case .shuttle, .mediaStore: .gray
        case .jellyfin: .purple
        case .emby: .green
        case .plex: .orange
        }
    }
}

/// Sources from plain values.
struct SourcesContent: View {
    let state: SourcesState
    var onAddServer: () -> Void = {}
    var onSignIn: (MediaProviderType) -> Void = { _ in }
    var onRemove: (MediaProviderType) -> Void = { _ in }
    var onRescan: () -> Void = {}

    /// The connected server whose options are showing.
    @State private var confirming: MediaProviderType?

    var body: some View {
        if state.servers.isEmpty {
            EmptyState(
                "No Servers Yet",
                systemImage: "server.rack",
                message: "Connect a Jellyfin or Emby server to stream your music library from it."
            ) {
                Button("Add a Server", systemImage: "plus", action: onAddServer)
                    .accessibilityIdentifier("sources.addServer")
            }
        } else {
            serverList
        }
    }

    private var serverList: some View {
        List {
            Section {
                ForEach(state.servers, id: \.self) { type in
                    Button { confirming = type } label: {
                        ServerRow(type: type, status: state.status(of: type))
                    }
                    .tint(.primary)
                    .accessibilityIdentifier("sources.server.\(type.name)")
                    .swipeActions {
                        Button("Remove", role: .destructive) { onRemove(type) }
                    }
                }
                Button("Add a Server", systemImage: "plus", action: onAddServer)
                    .accessibilityIdentifier("sources.addServer")
            } header: {
                Text("Servers")
            }
            Section {
                ScanRow(scan: state.scan, lastImport: state.lastImport, onRescan: onRescan)
            }
        }
        .confirmationDialog(
            confirming.map { "Remove \($0.title)?" } ?? "",
            isPresented: Binding(get: { confirming != nil }, set: { if !$0 { confirming = nil } }),
            titleVisibility: .visible,
            presenting: confirming
        ) { type in
            Button("Remove", role: .destructive) { onRemove(type) }
            Button("Sign In Again") { onSignIn(type) }
            Button("Cancel", role: .cancel) {}
        } message: { _ in
            Text("Its songs and playlists leave your library. You can connect it again later.")
        }
    }
}

/// A connected server: its glyph large, its name, what its import is doing, and a status capsule.
private struct ServerRow: View {
    let type: MediaProviderType
    let status: SourcesState.ServerStatus

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        let layout = dynamicTypeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: Spacing.small))
            : AnyLayout(HStackLayout(spacing: Spacing.smallMedium))
        layout {
            IconSquare(systemImage: type.symbol, style: .filled(type.color), size: .large)
            VStack(alignment: .leading, spacing: Spacing.tiny) {
                Text(type.title).font(.s2Headline)
                detail.font(.subheadline).foregroundStyle(.s2SecondaryText).lineLimit(2)
            }
            if !dynamicTypeSize.isAccessibilitySize { Spacer(minLength: Spacing.small) }
            capsule
        }
        .padding(.vertical, Spacing.xsmall)
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder private var detail: some View {
        switch status {
        case .connected: Text("Media server")
        case .importing(let fraction):
            if let fraction { Text("Importing, \(fraction, format: .percent.precision(.fractionLength(0)))") } else { Text("Importing…") }
        case .failed(let error): Text(error)
        }
    }

    private var capsule: StatusCapsule {
        switch status {
        case .connected: StatusCapsule(text: "Connected", color: .green)
        case .importing: StatusCapsule(text: "Importing", color: .blue)
        case .failed: StatusCapsule(text: "Import Failed", color: .red)
        }
    }
}

/// A short status in a tinted capsule, with a dot of its colour.
struct StatusCapsule: View {
    let text: String
    let color: Color

    var body: some View {
        HStack(spacing: Spacing.xsmall) {
            Circle().fill(color).frame(width: Spacing.small, height: Spacing.small)
            Text(text)
        }
        .font(.caption.weight(.semibold))
        .foregroundStyle(color)
        .padding(.horizontal, Spacing.small)
        .padding(.vertical, Spacing.xsmall)
        .background(Capsule().fill(color.opacity(0.15)))
    }
}

/// "Scan Now", with the running scan's progress, the last one's failure, or when the library last finished importing
/// (Android's rescan row).
private struct ScanRow: View {
    let scan: SourcesState.Scan
    let lastImport: Date?
    let onRescan: () -> Void

    var body: some View {
        Button(action: onRescan) {
            VStack(alignment: .leading, spacing: Spacing.small) {
                Label { Text("Scan Now") } icon: { IconSquare(systemImage: "arrow.clockwise", style: .filled(.teal)) }
                switch scan {
                case .idle:
                    Text("Look for new and changed music").font(.caption).foregroundStyle(.secondary)
                    if let lastImport {
                        Text("Library updated \(lastImport, format: .relative(presentation: .named))")
                            .font(.caption).foregroundStyle(.secondary)
                            .accessibilityIdentifier("sources.lastImport")
                    }
                case .scanning(let message, let fraction):
                    if let fraction { ProgressView(value: fraction) } else { ProgressView().frame(maxWidth: .infinity, alignment: .leading) }
                    Text(message ?? "Scanning your music").font(.caption).foregroundStyle(.secondary).lineLimit(1)
                case .failed(let error):
                    Text("Scan failed: \(error). Tap to try again").font(.caption).foregroundStyle(.secondary)
                }
            }
        }
        .disabled(scan.isScanning)
        .accessibilityIdentifier("sources.rescan")
    }
}

extension SourcesState.Scan {
    var isScanning: Bool {
        if case .scanning = self { true } else { false }
    }
}
