import Shared
import SwiftUI

/// Last.fm scrobbling (#503): the shared `ScrobblingViewModel`, as one grouped `Form`, pushed from Settings' Scrobbling
/// row (`Route.scrobbling`). Sign-in opens last.fm in the browser; coming back to the app finishes it, and the "I've
/// approved it" button is there for when that didn't happen on its own.
struct ScrobblingView: View {
    var body: some View {
        let viewModel = ViewModelCache.shared.viewModel(Route.scrobbling.cacheKey) { AppGraph.shared.scrobblingViewModel }
        Observing(viewModel.uiState) { state in
            ScrobblingContent(
                state: ScrobblingState(state),
                onSignIn: { viewModel.onSignIn() },
                onFinishSignIn: { viewModel.onFinishSignIn() },
                onSignOut: { viewModel.onSignOut() },
                onServerStreamsChange: { viewModel.onServerStreamsChange(enabled: $0) },
                onApprovalUrlOpened: { viewModel.onApprovalUrlOpened() },
                onMessageShown: { viewModel.onMessageShown() }
            )
        }
    }
}

/// The screen's state in plain values.
struct ScrobblingState: Equatable {
    enum Account: Equatable {
        /// A build without Last.fm keys: the account section is left out.
        case unavailable
        case signedOut
        case awaitingApproval
        case signedIn(username: String)
    }

    enum Message: Equatable {
        case notApproved, expired, failed
    }

    var account: Account
    var scrobbleServerStreams: Bool
    /// The last.fm page to open in the browser, once.
    var approvalUrl: URL?
    var message: Message?
    var busy: Bool
}

extension ScrobblingState {
    init(_ state: ScrobblingUiState) {
        let account: Account = switch onEnum(of: state.account) {
        case .unavailable: .unavailable
        case .signedOut: .signedOut
        case .awaitingApproval: .awaitingApproval
        case .signedIn(let signedIn): .signedIn(username: signedIn.username)
        }
        let message: Message? = switch state.message {
        case .notApproved: .notApproved
        case .expired: .expired
        case .failed: .failed
        case nil: nil
        }
        self.init(
            account: account,
            scrobbleServerStreams: state.scrobbleServerStreams,
            approvalUrl: state.approvalUrl.flatMap(URL.init(string:)),
            message: message,
            busy: state.busy
        )
    }

    var accountSummary: String {
        switch account {
        case .signedIn(let username):
            String(format: String(localized: "scrobbling_lastfm_signed_in", table: "Settings"), username)
        case .awaitingApproval:
            String(localized: "scrobbling_lastfm_awaiting", table: "Settings")
        case .signedOut, .unavailable:
            String(localized: "scrobbling_lastfm_signed_out", table: "Settings")
        }
    }

    var messageText: String? {
        message.map {
            switch $0 {
            case .notApproved: String(localized: "scrobbling_not_approved", table: "Settings")
            case .expired: String(localized: "scrobbling_expired", table: "Settings")
            case .failed: String(localized: "scrobbling_failed", table: "Settings")
            }
        }
    }
}

/// Whether a scene phase change is the user coming back from approving the app on last.fm: the first activation after
/// the app went to the background while sign-in waited for approval. Pulling down Notification Centre or Control
/// Centre only makes the app inactive, so it never counts.
struct BrowserReturn: Equatable {
    private(set) var leftWhileAwaiting = false

    mutating func phaseChanged(to phase: ScenePhase, awaiting: Bool) -> Bool {
        switch phase {
        case .background:
            if awaiting { leftWhileAwaiting = true }
            return false
        case .active:
            let returned = leftWhileAwaiting && awaiting
            leftWhileAwaiting = false
            return returned
        default:
            return false
        }
    }
}

/// Scrobbling from plain values.
struct ScrobblingContent: View {
    let state: ScrobblingState
    var onSignIn: () -> Void = {}
    var onFinishSignIn: () -> Void = {}
    var onSignOut: () -> Void = {}
    var onServerStreamsChange: (Bool) -> Void = { _ in }
    var onApprovalUrlOpened: () -> Void = {}
    var onMessageShown: () -> Void = {}

    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase
    @State private var browserReturn = BrowserReturn()

    var body: some View {
        Form {
            if state.account != .unavailable {
                Section {
                    LabeledContent {
                        if state.busy { ProgressView() }
                    } label: {
                        Text("scrobbling_lastfm", tableName: "Settings")
                        Text(state.accountSummary)
                    }
                    .accessibilityElement(children: .combine)
                    accountButtons
                } footer: {
                    Text("scrobbling_attribution", tableName: "Settings")
                }
            }
            Section {
                Toggle(isOn: Binding(get: { state.scrobbleServerStreams }, set: onServerStreamsChange)) {
                    Text("scrobbling_server_streams_title", tableName: "Settings")
                }
                .accessibilityIdentifier("scrobbling.serverStreams")
            } footer: {
                Text("scrobbling_server_streams_summary", tableName: "Settings")
            }
        }
        .navigationTitle(String(localized: "settings_scrobbling_title"))
        .navigationBarTitleDisplayMode(.inline)
        .onChange(of: state.approvalUrl, initial: true) { _, url in
            guard let url else { return }
            openURL(url)
            onApprovalUrlOpened()
        }
        .onChange(of: scenePhase) { _, phase in
            if browserReturn.phaseChanged(to: phase, awaiting: state.account == .awaitingApproval) { onFinishSignIn() }
        }
        .alert(
            state.messageText ?? "",
            isPresented: Binding(get: { state.messageText != nil }, set: { if !$0 { onMessageShown() } })
        ) {
            Button(String(localized: "settings_ok", table: "Settings")) {}
        }
    }

    @ViewBuilder private var accountButtons: some View {
        switch state.account {
        case .signedIn:
            Button(role: .destructive, action: onSignOut) { Text("scrobbling_sign_out", tableName: "Settings") }
                .disabled(state.busy)
                .accessibilityIdentifier("scrobbling.signOut")
        case .awaitingApproval:
            Button(action: onFinishSignIn) { Text("scrobbling_finish_sign_in", tableName: "Settings") }
                .disabled(state.busy)
                .accessibilityIdentifier("scrobbling.finishSignIn")
            Button(action: onSignIn) { Text("scrobbling_sign_in", tableName: "Settings") }
                .disabled(state.busy)
                .accessibilityIdentifier("scrobbling.signIn")
        case .signedOut, .unavailable:
            Button(action: onSignIn) { Text("scrobbling_sign_in", tableName: "Settings") }
                .disabled(state.busy)
                .accessibilityIdentifier("scrobbling.signIn")
        }
    }
}
