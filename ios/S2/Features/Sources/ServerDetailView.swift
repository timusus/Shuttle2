import Shared
import SwiftUI

/// A connected server's detail (#645), pushed from its row in Sources (`Route.server`): where it is and who is signed
/// in, its import's status, Scan Now, Sign In Again (the source setup at its sign-in) and Remove Server. Servers
/// have no per-library choice in the shared model, so there is no Libraries section. On Sources' own view models,
/// which stay cached while Sources is under it.
struct ServerDetailView: View {
    let typeName: String

    @Environment(Navigator.self) private var navigator: Navigator?
    @Environment(\.dismiss) private var dismiss
    /// The setup sheet, open at this server's sign-in while it's up.
    @State private var setup: SourceSetupStart?
    @State private var login = ServerLogin()

    var body: some View {
        if let type = MediaProviderType.server(named: typeName) {
            let models = SourcesModels.cached()
            Observing(models.sources.uiState, models.importState) { state, importState in
                let sources = SourcesState(state, importStatus: ImportStatus(importState))
                ServerDetailContent(
                    type: type,
                    login: login,
                    status: sources.status(of: type),
                    scan: sources.scan,
                    updated: sources.serverUpdated[type],
                    shortfall: sources.serverShortfall[type] ?? 0,
                    onRescan: { models.sources.onRescan() },
                    onSignIn: { setup = .signIn(type) },
                    onRemove: {
                        models.sources.onRemoveServer(type: type)
                        dismiss()
                    }
                )
            }
            .sheet(item: $setup, onDismiss: { login = ServerLogin.readAll()[type] ?? ServerLogin() }) { start in
                SourceSetupFlow(start: start, navigator: navigator, onClose: { setup = nil })
            }
            .onAppear { login = ServerLogin.readAll()[type] ?? ServerLogin() }
            .navigationTitle(type.title)
            .navigationBarTitleDisplayMode(.inline)
        } else {
            ContentUnavailableView("No Such Server", systemImage: "server.rack")
        }
    }
}

/// A server's detail from plain values.
struct ServerDetailContent: View {
    let type: MediaProviderType
    let login: ServerLogin
    let status: SourcesState.ServerStatus
    let scan: SourcesState.Scan
    let updated: Date?
    var shortfall = 0
    var now = Date()
    var onRescan: () -> Void = {}
    var onSignIn: () -> Void = {}
    var onRemove: () -> Void = {}

    @State private var removing: MediaProviderType?

    var body: some View {
        List {
            Section {
                Label {
                    VStack(alignment: .leading, spacing: Spacing.tiny) {
                        Text(type.title).font(.s2Headline)
                        if let host = login.host {
                            Text(host).font(.subheadline).foregroundStyle(.s2TextSecondary)
                        }
                    }
                } icon: {
                    IconSquare(glyph: type.glyph, style: .filled(type.color), size: .large)
                }
                .accessibilityElement(children: .combine)
                LabeledContent("Status") {
                    ServerStatusLabel(status: status)
                }
                .accessibilityIdentifier("serverDetail.status")
                if let updated {
                    Text(updatedText(updated, now: now)).font(.subheadline).foregroundStyle(.s2TextSecondary)
                        .accessibilityIdentifier("serverDetail.updated")
                }
                if shortfall > 0 {
                    Text(listingShortfallText(shortfall)).font(.subheadline).foregroundStyle(.s2TextSecondary)
                        .accessibilityIdentifier("serverDetail.shortfall")
                }
                if case .failed(let error) = status {
                    Text(error).font(.subheadline).foregroundStyle(.s2TextSecondary)
                        .accessibilityIdentifier("serverDetail.error")
                }
                if let address = login.address {
                    LabeledContent("Address", value: address)
                        .textSelection(.enabled)
                        .accessibilityIdentifier("serverDetail.address")
                }
                if let username = login.username {
                    LabeledContent("User", value: username)
                        .accessibilityIdentifier("serverDetail.user")
                }
            }
            ScanSection(
                scan: scan,
                onRescan: onRescan,
                footer: "Looks for new and changed music on every server you've connected."
            )
            Section {
                Button("Sign In Again", action: onSignIn)
                    .accessibilityIdentifier("serverDetail.signIn")
            } footer: {
                Text("Sign in again if \(type.title) says your session has expired, or to use another account.")
            }
            Section {
                Button("Remove Server", role: .destructive) { removing = type }
                    .accessibilityIdentifier("serverDetail.remove")
            }
        }
        .listStyle(.insetGrouped)
        .confirmingServerRemoval($removing) { _ in onRemove() }
    }
}

extension View {
    /// Asks before removing the server in `removing`, wherever a Remove starts (a server's detail, a swipe in
    /// Sources): its songs and playlists leave the library and its sign-in is forgotten.
    func confirmingServerRemoval(_ removing: Binding<MediaProviderType?>, onRemove: @escaping (MediaProviderType) -> Void) -> some View {
        confirmationDialog(
            "Remove \(removing.wrappedValue?.title ?? "Server")?",
            isPresented: Binding(get: { removing.wrappedValue != nil }, set: { if !$0 { removing.wrappedValue = nil } }),
            titleVisibility: .visible,
            presenting: removing.wrappedValue
        ) { type in
            Button("Remove", role: .destructive) { onRemove(type) }
            Button("Cancel", role: .cancel) {}
        } message: { _ in
            Text("Its songs and playlists leave your library, and you'll need to sign in to connect it again.")
        }
    }
}
