import Shared
import SwiftUI

/// Sources (#587, phase 7 in `docs/architecture/ios-port/phase-5-ios-app.md`): the media servers the library imports
/// from, on the shared `SourcesViewModel`, and a rescan. "Connect a Server" presents the type picker on
/// `ServerTypePickerViewModel`; choosing a type pushes `Route.serverSignIn`. A connected server's row offers what
/// Android's server dialog does: sign in again, or remove it. This device's folders are phase 8 (local files), so
/// Android's "This device" and folder groups have no iOS rows yet.
struct SourcesView: View {
    /// Absent only outside the shell (previews); every stack in `AppShell` has it.
    @Environment(Navigator.self) private var navigator: Navigator?
    @State private var showsPicker = false
    /// The type chosen in the picker, pushed once the sheet has gone so the push animates on the visible stack.
    @State private var chosenType: MediaProviderType?

    var body: some View {
        let models = ViewModelCache.shared.viewModel(Route.sources.cacheKey) { SourcesModels(graph: AppGraph.shared) }
        let choice = ServerTypeChoice(tryAddServer: { models.picker.onAddServer() }, choose: { chosenType = $0 })
        Observing(models.sources.uiState) { state in
            SourcesContent(
                state: SourcesState(state),
                onAddServer: { showsPicker = true },
                onSignIn: { navigator?.open(.serverSignIn($0)) },
                onRemove: { models.sources.onRemoveServer(type: $0) },
                onRescan: { models.sources.onRescan() }
            )
            // The only event is an exclude folder off this device's storage, which iOS has no folders to raise.
            .consumeEvents(state.events, handled: { models.sources.onEventHandled(id: $0) }) { _ in }
        }
        .sheet(isPresented: $showsPicker, onDismiss: {
            if let type = chosenType {
                chosenType = nil
                navigator?.open(.serverSignIn(type))
            }
        }) {
            ServerTypePicker(types: MediaProviderType.signInTypes, onSelect: { type in
                choice.select(type)
                showsPicker = false
            })
        }
        .navigationTitle("Sources")
    }
}

/// Sources' ViewModels, cached together under its route's key.
final class SourcesModels: ViewModelGroup {
    let sources: SourcesViewModel
    let picker: ServerTypePickerViewModel

    init(graph: IosAppGraph) {
        sources = graph.sourcesViewModel
        picker = graph.serverTypePickerViewModel
    }

    var members: [Lifecycle_viewmodelViewModel] { [sources, picker] }
}

/// What choosing a type in the picker does, as Android's `ServerTypePickerRoute`: the entitlement gate
/// (`TryAddServer`; always open on iOS until billing lands), then the chosen type's sign-in.
struct ServerTypeChoice {
    let tryAddServer: () -> Bool
    let choose: (MediaProviderType) -> Void

    func select(_ type: MediaProviderType) {
        if tryAddServer() { choose(type) }
    }
}

/// `SourcesUiState` as iOS shows it: the connected servers, and the scan.
struct SourcesState: Equatable {
    var servers: [MediaProviderType]
    var scan: Scan

    enum Scan: Equatable {
        case idle
        case scanning(message: String?, fraction: Double?)
        case failed(String)
    }

    init(servers: [MediaProviderType], scan: Scan = .idle) {
        self.servers = servers
        self.scan = scan
    }

    init(_ state: SourcesUiState) {
        servers = state.servers.filter(\.connected).map(\.type)
        if let progress = state.scan {
            scan = .scanning(message: progress.message, fraction: progress.fraction.map { Double($0.floatValue) })
        } else if let error = state.scanError {
            scan = .failed(error)
        } else {
            scan = .idle
        }
    }
}

extension MediaProviderType {
    /// The server types the picker offers on iOS: the shared `ServerTypes` less Plex, whose provider isn't in
    /// `:shared` yet, so its sign-in has no authentication to run.
    static var signInTypes: [MediaProviderType] {
        SourcesViewModelKt.ServerTypes.filter { $0 != .plex }
    }

    /// The name Sources and the picker show (Android's `titleRes`).
    var title: String {
        switch self {
        case .shuttle: "S2 scanner"
        case .mediaStore: "Android media store"
        case .emby: "Emby"
        case .jellyfin: "Jellyfin"
        case .plex: "Plex"
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
        List {
            Section {
                ForEach(state.servers, id: \.self) { type in
                    Button { confirming = type } label: {
                        LabeledContent {
                            Text("Connected")
                        } label: {
                            Label(type.title, systemImage: "server.rack")
                        }
                    }
                    .tint(.primary)
                    .accessibilityIdentifier("sources.server.\(type.name)")
                    .swipeActions {
                        Button("Remove", role: .destructive) { onRemove(type) }
                    }
                }
                Button("Connect a Server", systemImage: "plus", action: onAddServer)
                    .accessibilityIdentifier("sources.addServer")
            } header: {
                Text("Servers")
            } footer: {
                if state.servers.isEmpty {
                    Text("Stream your library from a Jellyfin or Emby server.")
                }
            }
            if !state.servers.isEmpty {
                Section {
                    ScanRow(scan: state.scan, onRescan: onRescan)
                }
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

/// "Scan Now", with the running scan's progress or the last one's failure (Android's rescan row).
private struct ScanRow: View {
    let scan: SourcesState.Scan
    let onRescan: () -> Void

    var body: some View {
        Button(action: onRescan) {
            VStack(alignment: .leading, spacing: 6) {
                Label("Scan Now", systemImage: "arrow.clockwise")
                switch scan {
                case .idle:
                    Text("Look for new and changed music").font(.caption).foregroundStyle(.secondary)
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

/// "Connect a Server": a row per type iOS can sign in to (`MediaProviderType.signInTypes`), as Android's picker sheet.
struct ServerTypePicker: View {
    let types: [MediaProviderType]
    let onSelect: (MediaProviderType) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List(types, id: \.self) { type in
                Button { onSelect(type) } label: {
                    Label(type.title, systemImage: "server.rack")
                }
                .tint(.primary)
                .accessibilityIdentifier("serverTypePicker.\(type.name)")
            }
            .navigationTitle("Connect a Server")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

extension Route {
    /// The sign-in route for `type`, keyed by the Kotlin enum's name so a stored path decodes it.
    static func serverSignIn(_ type: MediaProviderType) -> Route {
        .serverSignIn(type: type.name)
    }

    /// The server type a `serverSignIn` route names; nil for an unknown name (a stored path from another version).
    static func serverType(named name: String) -> MediaProviderType? {
        MediaProviderType.allCases.first { $0.name == name }
    }
}
