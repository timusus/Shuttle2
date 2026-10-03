import Shared
import SwiftUI
import UniformTypeIdentifiers

/// Sources (#587, #624, #645, #590; phases 7 and 8 in `docs/architecture/ios-port/phase-5-ios-app.md`): this device's
/// music and the media servers the library imports from, on the shared `SourcesViewModel`, as an inset-grouped list in
/// the Settings style. On This iPhone turns the device's music on, lists where it's read from (the app's folder in
/// Files, and each folder picked there) and adds a folder through the Files picker. Each server row shows its host and
/// its import's status and pushes its detail (`ServerDetailView`); Add Server opens the source setup
/// (`SourceSetupFlow`) in a sheet, the same cards, sign-in and import progress the first run shows. Scanning is its own
/// section.
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
                onThisDeviceChange: { models.sources.onThisDeviceChange(enabled: $0) },
                onAddFolder: { url in
                    let picked = AppGraph.dependencies.localLibrary.stage(url)
                    models.sources.onFolderPicked(kind: .extra, treeUri: picked)
                },
                onRemoveFolder: { folder in
                    models.sources.onRemoveFolder(
                        kind: .extra,
                        folder: SourceFolder(uri: folder.id, path: folder.path, name: folder.name, hasAccess: folder.hasAccess)
                    )
                },
                onAddServer: { setup = .chooseSource },
                onRemove: { models.sources.onRemoveServer(type: $0) },
                onRescan: { models.sources.onRescan() }
            )
            // The only event is an exclude folder off this device's storage; iOS only adds folders to read in full.
            .consumeEvents(state.events, handled: { models.sources.onEventHandled(id: $0) }) { _ in }
        }
        .sheet(item: $setup, onDismiss: { logins = ServerLogin.readAll() }) { start in
            SourceSetupFlow(start: start, navigator: navigator, onClose: { setup = nil })
        }
        .onAppear {
            logins = ServerLogin.readAll()
            SourcesModels.cached().sources.onResume()
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

/// `SourcesUiState` as iOS shows it: this device's music and its folders, the connected servers with their saved
/// sign-ins, each one's import, the scan, and when the library last finished importing.
struct SourcesState: Equatable {
    var thisDevice: Bool
    var folders: [DeviceFolder]
    var deviceSongs: Int?
    var servers: [MediaProviderType]
    var scan: Scan
    var importStatus: ImportStatus
    var lastImport: Date?
    var logins: [MediaProviderType: ServerLogin]

    /// A folder picked in Files: [hasAccess] is false while its bookmark won't resolve, until it's picked again.
    struct DeviceFolder: Equatable, Identifiable {
        var id: String
        var name: String
        var path: String?
        var hasAccess = true
    }

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
        thisDevice: Bool = false,
        folders: [DeviceFolder] = [],
        deviceSongs: Int? = nil,
        servers: [MediaProviderType],
        scan: Scan = .idle,
        importStatus: ImportStatus = .idle,
        lastImport: Date? = nil,
        logins: [MediaProviderType: ServerLogin] = [:]
    ) {
        self.thisDevice = thisDevice
        self.folders = folders
        self.deviceSongs = deviceSongs
        self.servers = servers
        self.scan = scan
        self.importStatus = importStatus
        self.lastImport = lastImport
        self.logins = logins
    }

    init(_ state: SourcesUiState, importStatus: ImportStatus = .idle, logins: [MediaProviderType: ServerLogin] = [:]) {
        thisDevice = state.thisDevice
        folders = state.folders.extras.compactMap { folder in
            folder.uri.map { DeviceFolder(id: $0, name: folder.name, path: folder.path, hasAccess: folder.hasAccess) }
        }
        deviceSongs = state.deviceSongs.map(\.intValue)
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
    /// The server types the setup offers on iOS: the shared `ServerTypes`, as on Android.
    static var signInTypes: [MediaProviderType] {
        SourcesViewModelKt.ServerTypes
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

/// Sources from plain values: On This iPhone, Media Servers (each server, then Add Server) and, once there is a
/// source, Scan.
struct SourcesContent: View {
    let state: SourcesState
    var onThisDeviceChange: (Bool) -> Void = { _ in }
    var onAddFolder: (URL) -> Void = { _ in }
    var onRemoveFolder: (SourcesState.DeviceFolder) -> Void = { _ in }
    var onAddServer: () -> Void = {}
    var onRemove: (MediaProviderType) -> Void = { _ in }
    var onRescan: () -> Void = {}

    /// The server a swipe's Remove is asking about.
    @State private var removing: MediaProviderType?

    var body: some View {
        List {
            DeviceSection(state: state, onThisDeviceChange: onThisDeviceChange, onAddFolder: onAddFolder, onRemoveFolder: onRemoveFolder)
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
                    Text("Connect a Jellyfin, Emby or Plex server to stream your music library from it.")
                }
            }
            if state.thisDevice || !state.servers.isEmpty {
                ScanSection(scan: state.scan, lastImport: state.lastImport, onRescan: onRescan, footer: state.scanFooter)
            }
        }
        .listStyle(.insetGrouped)
        .confirmingServerRemoval($removing, onRemove: onRemove)
    }
}

/// On This iPhone: the switch for this device's music and, while it's on, where it's read from: the app's own
/// folder in Files (On My iPhone > Shuttle Music, where Finder's file sharing copies to too) and each folder picked in
/// Files, which a swipe removes. A folder out of reach says so, and tapping it picks it again. Add Folder opens the
/// Files picker; its folder is read in full, subfolders and all.
struct DeviceSection: View {
    let state: SourcesState
    let onThisDeviceChange: (Bool) -> Void
    let onAddFolder: (URL) -> Void
    let onRemoveFolder: (SourcesState.DeviceFolder) -> Void

    @State private var picking = false

    var body: some View {
        Section {
            Toggle(isOn: Binding(get: { state.thisDevice }, set: onThisDeviceChange)) {
                Label {
                    Text("Music on This iPhone")
                } icon: {
                    IconSquare(systemImage: MediaProviderType.shuttle.symbol, style: .filled(.blue))
                }
            }
            .accessibilityIdentifier("sources.thisDevice")
            if state.thisDevice {
                Label {
                    VStack(alignment: .leading, spacing: Spacing.tiny) {
                        Text("Shuttle Music Folder")
                        Text("On My iPhone in Files").font(.subheadline).foregroundStyle(.s2TextSecondary)
                    }
                } icon: {
                    IconSquare(systemImage: "folder.fill", style: .filled(.gray))
                }
                .accessibilityElement(children: .combine)
                .accessibilityIdentifier("sources.documents")
                ForEach(state.folders) { folder in
                    Button { if !folder.hasAccess { picking = true } } label: {
                        DeviceFolderRow(folder: folder)
                    }
                    .tint(.primary)
                    .accessibilityIdentifier("sources.folder.\(folder.name)")
                    .swipeActions {
                        Button("Remove", role: .destructive) { onRemoveFolder(folder) }
                    }
                }
                Button { picking = true } label: {
                    Label("Add Folder", systemImage: "plus")
                }
                .accessibilityIdentifier("sources.addFolder")
            }
        } header: {
            Text("On This iPhone")
        } footer: {
            Text(state.deviceFooter)
        }
        .fileImporter(isPresented: $picking, allowedContentTypes: [.folder]) { result in
            if case .success(let url) = result { onAddFolder(url) }
        }
    }
}

/// A folder picked in Files: its name over where it is, or a warning to pick it again when it's out of reach.
struct DeviceFolderRow: View {
    let folder: SourcesState.DeviceFolder

    var body: some View {
        Label {
            VStack(alignment: .leading, spacing: Spacing.tiny) {
                Text(folder.name)
                if folder.hasAccess {
                    if let path = folder.path {
                        Text(path).font(.subheadline).foregroundStyle(.s2TextSecondary).lineLimit(1).truncationMode(.head)
                    }
                } else {
                    Text("Can't Be Read. Tap to Choose It Again.").font(.subheadline).foregroundStyle(.s2Error)
                }
            }
        } icon: {
            IconSquare(systemImage: folder.hasAccess ? "folder.fill" : "exclamationmark.triangle.fill", style: .filled(folder.hasAccess ? .blue : .orange))
        }
        .accessibilityElement(children: .combine)
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
                        Text(host).font(.subheadline).foregroundStyle(.s2TextSecondary).lineLimit(1).truncationMode(.middle)
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
                Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.s2Error).accessibilityHidden(true)
            }
            Text(status.text).foregroundStyle(.s2TextSecondary)
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
                let text = Text(message ?? "Scanning your music").font(.subheadline).foregroundStyle(.s2TextSecondary).lineLimit(2)
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
                        Text(error).font(.subheadline).foregroundStyle(.s2TextSecondary)
                    }
                } icon: {
                    Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.s2Error)
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

extension SourcesState {
    /// On This iPhone's footer: how many songs came from this device, or how to add some.
    var deviceFooter: String {
        guard thisDevice else { return "Play music stored on this iPhone, copied in through the Files app or Finder." }
        switch deviceSongs {
        case let count? where count > 0: return "\(count.formatted()) \(count == 1 ? "song" : "songs") on this iPhone."
        default: return "Copy music into Shuttle Music in the Files app or Finder, or add a folder from Files."
        }
    }

    /// What Scan Now looks through.
    var scanFooter: String {
        switch (thisDevice, servers.isEmpty) {
        case (true, true): "Looks for new and changed music on this iPhone."
        case (true, false): "Looks for new and changed music on this iPhone and your servers."
        default: "Looks for new and changed music on your servers."
        }
    }
}

extension SourcesState.Scan {
    var isScanning: Bool {
        if case .scanning = self { true } else { false }
    }
}
