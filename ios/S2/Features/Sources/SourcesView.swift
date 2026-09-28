import Shared
import SwiftUI

/// Sources (#587, #624, #645; phase 7 in `docs/architecture/ios-port/phase-5-ios-app.md`): the media servers the library
/// imports from, on the shared `SourcesViewModel`, as an inset-grouped list in the Settings style. Each server row
/// shows its host and its import's status and pushes its detail (`ServerDetailView`); Add Server opens the source
/// setup (`SourceSetupFlow`) in a sheet, the same cards, sign-in and import progress the first run shows. Scanning is
/// its own section. This device's music is phase 8 (local files), so there is no "On this iPhone" section until iOS
/// has a local provider to show in it.
struct SourcesView: View {
    /// Absent only outside the shell (previews); every stack in `AppShell` has it.
    @Environment(Navigator.self) private var navigator: Navigator?
    /// Where the setup sheet opens, while it's up.
    @State private var setup: SourceSetupStart?
    /// Each server's saved address and user, read when the screen shows and after the setup closes.
    @State private var logins: [MediaProviderType: ServerLogin] = [:]

    var body: some View {
        let models = SourcesModels.cached()
        Observing(models.sources.uiState, models.importState) { state, importState in
            SourcesContent(
                state: SourcesState(state, importStatus: ImportStatus(importState), logins: logins),
                onAddServer: { setup = .chooseSource },
                onRemove: { models.sources.onRemoveServer(type: $0) },
                onRescan: { models.sources.onRescan() }
            )
            // The only event is an exclude folder off this device's storage, which iOS has no folders to raise.
            .consumeEvents(state.events, handled: { models.sources.onEventHandled(id: $0) }) { _ in }
        }
        .sheet(item: $setup, onDismiss: { logins = ServerLogin.readAll() }) { start in
            SourceSetupFlow(start: start, navigator: navigator, onClose: { setup = nil })
        }
        .onAppear { logins = ServerLogin.readAll() }
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

    /// Sources' group, shared with a server's detail pushed on top of it.
    @MainActor
    static func cached() -> SourcesModels {
        ViewModelCache.shared.viewModel(Route.sources.cacheKey) { SourcesModels(graph: AppGraph.shared) }
    }
}

/// A server's saved sign-in, as Sources shows it: never the password.
struct ServerLogin: Equatable {
    var address: String?
    var username: String?

    /// The address without its scheme or path ("music.example.com:8096"), for a server's row.
    var host: String? {
        guard let address else { return nil }
        guard let url = URL(string: address), let host = url.host(percentEncoded: false), !host.isEmpty else { return address }
        return url.port.map { "\(host):\($0)" } ?? host
    }

    /// Every server type iOS signs in to, read from the Keychain (`ReadServerLogin`).
    @MainActor
    static func readAll(graph: IosAppGraph = AppGraph.shared) -> [MediaProviderType: ServerLogin] {
        Dictionary(uniqueKeysWithValues: MediaProviderType.signInTypes.map { type in
            let saved = graph.readServerLogin.invoke(type: type)
            return (type, ServerLogin(address: saved.address, username: saved.username))
        })
    }
}

/// `SourcesUiState` as iOS shows it: the connected servers with their saved sign-ins, each one's import, the scan,
/// and when the library last finished importing.
struct SourcesState: Equatable {
    var servers: [MediaProviderType]
    var scan: Scan
    var importStatus: ImportStatus
    var lastImport: Date?
    var logins: [MediaProviderType: ServerLogin]

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

        /// What the row says, and VoiceOver reads as its value.
        var text: String {
            switch self {
            case .connected: "Connected"
            case .importing(let fraction?): "Importing \(fraction.formatted(.percent.precision(.fractionLength(0))))"
            case .importing: "Importing"
            case .failed: "Import Failed"
            }
        }
    }

    init(
        servers: [MediaProviderType],
        scan: Scan = .idle,
        importStatus: ImportStatus = .idle,
        lastImport: Date? = nil,
        logins: [MediaProviderType: ServerLogin] = [:]
    ) {
        self.servers = servers
        self.scan = scan
        self.importStatus = importStatus
        self.lastImport = lastImport
        self.logins = logins
    }

    init(_ state: SourcesUiState, importStatus: ImportStatus = .idle, logins: [MediaProviderType: ServerLogin] = [:]) {
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
        self.logins = logins
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

    /// The server type a `Route.server` names, if it is one.
    static func server(named name: String) -> MediaProviderType? {
        SourcesViewModelKt.ServerTypes.first { $0.name == name }
    }

    /// The name Sources and the setup show (Android's `titleRes`).
    var title: String {
        switch self {
        case .shuttle: "Shuttle Music scanner"
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

/// Sources from plain values: Media Servers (each server, then Add Server) and, once there is a server, Scan.
struct SourcesContent: View {
    let state: SourcesState
    var onAddServer: () -> Void = {}
    var onRemove: (MediaProviderType) -> Void = { _ in }
    var onRescan: () -> Void = {}

    /// The server a swipe's Remove is asking about.
    @State private var removing: MediaProviderType?

    var body: some View {
        List {
            Section {
                ForEach(state.servers, id: \.self) { type in
                    NavigationLink(value: Route.server(type: type.name)) {
                        ServerRow(type: type, host: state.logins[type]?.host, status: state.status(of: type))
                    }
                    .accessibilityIdentifier("sources.server.\(type.name)")
                    .swipeActions {
                        Button("Remove", role: .destructive) { removing = type }
                    }
                }
                Button(action: onAddServer) {
                    Label("Add Server", systemImage: "plus")
                }
                .accessibilityIdentifier("sources.addServer")
            } header: {
                Text("Media Servers")
            } footer: {
                if state.servers.isEmpty {
                    Text("Connect a Jellyfin or Emby server to stream your music library from it.")
                }
            }
            if !state.servers.isEmpty {
                ScanSection(scan: state.scan, lastImport: state.lastImport, onRescan: onRescan)
            }
        }
        .listStyle(.insetGrouped)
        .confirmingServerRemoval($removing, onRemove: onRemove)
    }
}

/// A connected server: its glyph, its name over its host, and its status as the row's value in the secondary label
/// colour, which VoiceOver reads as the row's value.
struct ServerRow: View {
    let type: MediaProviderType
    let host: String?
    let status: SourcesState.ServerStatus

    var body: some View {
        LabeledContent {
            ServerStatusLabel(status: status)
        } label: {
            Label {
                VStack(alignment: .leading, spacing: Spacing.tiny) {
                    Text(type.title)
                    if let host {
                        Text(host).font(.subheadline).foregroundStyle(.secondary).lineLimit(1).truncationMode(.middle)
                    }
                }
            } icon: {
                IconSquare(systemImage: type.symbol, style: .filled(type.color))
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel([type.title, host].compactMap { $0 }.joined(separator: ", "))
        .accessibilityValue(status.text)
    }
}

/// A server's status in the secondary label colour; a failure leads with a red warning glyph.
struct ServerStatusLabel: View {
    let status: SourcesState.ServerStatus

    var body: some View {
        HStack(spacing: Spacing.xsmall) {
            if case .failed = status {
                Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.red).accessibilityHidden(true)
            }
            Text(status.text).foregroundStyle(.secondary)
        }
        .font(.subheadline)
    }
}

/// Scan: "Scan Now" as a button row (disabled while a scan runs, with its progress beneath), the last scan's failure,
/// and when the library last finished importing, in the standard label colours.
struct ScanSection: View {
    let scan: SourcesState.Scan
    let lastImport: Date?
    let onRescan: () -> Void
    var footer = "Looks for new and changed music on your servers."

    var body: some View {
        Section {
            Button(action: onRescan) {
                Label { Text("Scan Now") } icon: { IconSquare(systemImage: "arrow.clockwise", style: .filled(.teal)) }
            }
            .tint(.primary)
            .disabled(scan.isScanning)
            .accessibilityIdentifier("sources.rescan")
            switch scan {
            case .idle:
                EmptyView()
            case .scanning(let message, let fraction):
                let text = Text(message ?? "Scanning your music").font(.subheadline).foregroundStyle(.secondary).lineLimit(2)
                if let fraction {
                    VStack(alignment: .leading, spacing: Spacing.small) {
                        ProgressView(value: fraction)
                        text
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityIdentifier("sources.scanProgress")
                } else {
                    // Indeterminate: the spinner leads the message on one line, as a row's icon would.
                    HStack(spacing: Spacing.medium) {
                        // A new identity each time the row shows: a List reusing the row's cell leaves an
                        // indeterminate ProgressView blank.
                        ProgressView().id(UUID())
                        text
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityIdentifier("sources.scanProgress")
                }
            case .failed(let error):
                Label {
                    VStack(alignment: .leading, spacing: Spacing.tiny) {
                        Text("Last Scan Failed")
                        Text(error).font(.subheadline).foregroundStyle(.secondary)
                    }
                } icon: {
                    Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.red)
                }
                .accessibilityElement(children: .combine)
                .accessibilityIdentifier("sources.scanFailed")
            }
            if let lastImport {
                LabeledContent("Last Updated") {
                    Text(lastImport, format: .relative(presentation: .named))
                }
                .accessibilityIdentifier("sources.lastImport")
            }
        } header: {
            Text("Scan")
        } footer: {
            Text(footer)
        }
    }
}

extension SourcesState.Scan {
    var isScanning: Bool {
        if case .scanning = self { true } else { false }
    }
}
