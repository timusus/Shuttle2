import Shared
import SwiftUI

/// A Jellyfin or Emby server's sign-in (#587, phase 7), on the shared `ServerSignInViewModel`: Android's
/// `ServerSignInRoute`/`ServerSignInDialog` as a pushed HIG form. It starts from the saved login; once the server is
/// signed in it tells `ServerTypePickerViewModel` (`onServerConnected`, which enables the provider and imports), then
/// pops back to Sources when the view model says the success message has shown for long enough. Plex, and its 2FA code
/// field, join with the Plex provider in `:shared`; until then the picker doesn't offer it (`MediaProviderType.signInTypes`).
struct ServerSignInView: View {
    let type: MediaProviderType
    /// Absent only outside the shell (previews), where finishing dismisses instead.
    @Environment(Navigator.self) private var navigator: Navigator?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let models = ViewModelCache.shared.viewModel(Route.serverSignIn(type).cacheKey) {
            ServerSignInModels(graph: AppGraph.shared, type: type)
        }
        Observing(models.signIn.uiState) { state in
            ServerSignInContent(state: ServerSignInState(state), actions: ServerSignInActions(models.signIn))
                .consumeEvents(state.events, handled: { models.signIn.onEventHandled(id: $0) }) { event in
                    ServerSignInOutcome(
                        onConnected: { models.picker.onServerConnected(type: type) },
                        onFinished: { if let navigator { navigator.pop(.serverSignIn(type)) } else { dismiss() } }
                    ).handle(event)
                }
        }
        .navigationTitle(type.title)
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// The sign-in's ViewModels, cached together under its route's key: the form's, and the picker's, which connects the
/// server once it's signed in (Android's `ServerTypePickerRoute` passes `onServerConnected` the same way).
final class ServerSignInModels: ViewModelGroup {
    let signIn: ServerSignInViewModel
    let picker: ServerTypePickerViewModel

    init(graph: IosAppGraph, type: MediaProviderType) {
        signIn = graph.serverSignInViewModelFactory.create(type: type)
        picker = graph.serverTypePickerViewModel
    }

    var members: [Lifecycle_viewmodelViewModel] { [signIn, picker] }
}

/// What the sign-in's one-shot events do: `Connected` starts the import, `Finished` goes back to Sources.
struct ServerSignInOutcome {
    let onConnected: () -> Void
    let onFinished: () -> Void

    func handle(_ event: ServerSignInEvent) {
        switch onEnum(of: event) {
        case .connected: onConnected()
        case .finished: onFinished()
        }
    }
}

/// `ServerSignInUiState` as iOS shows it.
struct ServerSignInState: Equatable {
    enum Field: Hashable {
        case address, username, password
    }

    enum Step: Equatable {
        case form
        case authenticating
        /// Jellyfin Quick Connect's code, waiting for approval in another Jellyfin app.
        case awaitingCode(String)
        case connected
        case failed(String)
    }

    var type: MediaProviderType
    var address = ""
    var username = ""
    var password = ""
    var rememberPassword = true
    var missing: Set<Field> = []
    var step: Step = .form
    var showProDisclosure = false
    var quickConnectEnabled = false

    init(type: MediaProviderType) {
        self.type = type
    }

    init(_ state: ServerSignInUiState) {
        type = state.type
        address = state.form.address
        username = state.form.username
        password = state.form.password
        rememberPassword = state.form.rememberPassword
        missing = Set(state.form.missing.map { field in
            switch field {
            case .address: Field.address
            case .username: Field.username
            case .password: Field.password
            }
        })
        step = switch onEnum(of: state.step) {
        case .form: .form
        case .authenticating: .authenticating
        case .awaitingCode(let awaiting): .awaitingCode(awaiting.code)
        case .connected: .connected
        case .failed(let failed): .failed(failed.message)
        }
        showProDisclosure = state.showProDisclosure
        quickConnectEnabled = state.quickConnectEnabled
    }
}

/// The form's calls into `ServerSignInViewModel`.
struct ServerSignInActions {
    var onAddressChange: (String) -> Void = { _ in }
    var onUsernameChange: (String) -> Void = { _ in }
    var onPasswordChange: (String) -> Void = { _ in }
    var onRememberPasswordChange: (Bool) -> Void = { _ in }
    var onAuthenticate: () -> Void = {}
    var onRetry: () -> Void = {}
    var onUseQuickConnect: () -> Void = {}
    var onCancelQuickConnect: () -> Void = {}
}

extension ServerSignInActions {
    init(_ viewModel: ServerSignInViewModel) {
        self.init(
            onAddressChange: { viewModel.onAddressChange(address: $0) },
            onUsernameChange: { viewModel.onUsernameChange(username: $0) },
            onPasswordChange: { viewModel.onPasswordChange(password: $0) },
            onRememberPasswordChange: { viewModel.onRememberPasswordChange(remember: $0) },
            onAuthenticate: { viewModel.onAuthenticate() },
            onRetry: { viewModel.onRetry() },
            onUseQuickConnect: { viewModel.onUseQuickConnect() },
            onCancelQuickConnect: { viewModel.onCancelQuickConnect() }
        )
    }
}

/// The sign-in form from plain values.
///
/// The text fields and the toggle edit local copies, seeded from the first state and sent to the view model on every
/// change: the shared state arrives a turn later through its flow, and binding a field straight to it would move the
/// caret or flicker the toggle while it catches up.
struct ServerSignInContent: View {
    let state: ServerSignInState
    var actions = ServerSignInActions()

    /// The most a form grows to on regular width, so its rows stay a readable length.
    static let readableWidth: CGFloat = 640

    @Environment(\.layoutTier) private var layoutTier
    @FocusState private var focus: Focus?
    @State private var address: String
    @State private var username: String
    @State private var password: String
    @State private var rememberPassword: Bool

    private enum Focus: Hashable {
        case address, username, password
    }

    init(state: ServerSignInState, actions: ServerSignInActions = ServerSignInActions()) {
        self.state = state
        self.actions = actions
        _address = State(initialValue: state.address)
        _username = State(initialValue: state.username)
        _password = State(initialValue: state.password)
        _rememberPassword = State(initialValue: state.rememberPassword)
    }

    var body: some View {
        Form {
            if case .awaitingCode(let code) = state.step {
                QuickConnectSection(code: code, onCancel: actions.onCancelQuickConnect)
            } else {
                fields
                submission
            }
        }
        // Dragging the form puts the keyboard away, so Sign In and Quick Connect are reachable without Return,
        // which on the password field signs in.
        .scrollDismissesKeyboard(.immediately)
        .frame(maxWidth: layoutTier == .compact ? .infinity : Self.readableWidth)
        .frame(maxWidth: .infinity)
        .background(Color(.systemGroupedBackground))
        .onChange(of: address) { _, value in actions.onAddressChange(value) }
        .onChange(of: username) { _, value in actions.onUsernameChange(value) }
        .onChange(of: password) { _, value in actions.onPasswordChange(value) }
        .onChange(of: rememberPassword) { _, value in actions.onRememberPasswordChange(value) }
    }

    private var editable: Bool {
        switch state.step {
        case .form, .failed: true
        case .authenticating, .awaitingCode, .connected: false
        }
    }

    @ViewBuilder private var fields: some View {
        Section {
            TextField("Address", text: $address, prompt: Text("http://my.server.com:8080"))
                .keyboardType(.URL)
                .textContentType(.URL)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .focused($focus, equals: .address)
                .submitLabel(.next)
                .onSubmit { focus = .username }
                .accessibilityIdentifier("serverSignIn.address")
        } header: {
            Text("Server")
        } footer: {
            if state.missing.contains(.address) {
                RequiredNote(field: "address")
            } else {
                Text("The server's address, with http:// or https:// and its port.")
            }
        }
        Section {
            TextField("Username", text: $username)
                .textContentType(.username)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .focused($focus, equals: .username)
                .submitLabel(.next)
                .onSubmit { focus = .password }
                .accessibilityIdentifier("serverSignIn.username")
            SecureField("Password", text: $password)
                .textContentType(.password)
                .focused($focus, equals: .password)
                .submitLabel(.go)
                .onSubmit(signIn)
                .accessibilityIdentifier("serverSignIn.password")
            Toggle("Remember Password", isOn: $rememberPassword)
                .accessibilityIdentifier("serverSignIn.rememberPassword")
        } header: {
            Text("Account")
        } footer: {
            if state.missing.contains(.username) {
                RequiredNote(field: "username")
            } else if state.missing.contains(.password) {
                RequiredNote(field: "password")
            }
        }
        .disabled(!editable)
        if case .failed(let message) = state.step {
            Section {
                Label(message, systemImage: "exclamationmark.triangle.fill")
                    .foregroundStyle(.red)
                    .accessibilityIdentifier("serverSignIn.error")
            }
        }
    }

    @ViewBuilder private var submission: some View {
        Section {
            Button(action: signIn) {
                HStack(spacing: 8) {
                    switch state.step {
                    case .authenticating:
                        ProgressView()
                        Text("Signing In…")
                    case .connected:
                        Label("Signed In", systemImage: "checkmark.circle.fill")
                    case .failed:
                        Text("Try Again")
                    case .form, .awaitingCode:
                        Text("Sign In")
                    }
                }
                .frame(maxWidth: .infinity)
            }
            .disabled(!editable)
            .accessibilityIdentifier("serverSignIn.signIn")
            if state.quickConnectEnabled, editable {
                Button("Use Quick Connect", action: actions.onUseQuickConnect)
                    .frame(maxWidth: .infinity)
                    .accessibilityIdentifier("serverSignIn.quickConnect")
            }
        } footer: {
            if state.showProDisclosure {
                Text("Streaming from Jellyfin, Emby and Plex is part of S2 Pro. Free for 14 days.")
            }
        }
    }

    /// Sign In, or Try Again after a failure: back to the form, then sign in with what's in it now.
    private func signIn() {
        switch state.step {
        case .form:
            focus = nil
            actions.onAuthenticate()
        case .failed:
            focus = nil
            actions.onRetry()
            actions.onAuthenticate()
        case .authenticating, .awaitingCode, .connected:
            break
        }
    }
}

private struct RequiredNote: View {
    let field: String

    var body: some View {
        Label("Enter the \(field).", systemImage: "exclamationmark.circle")
            .foregroundStyle(.red)
    }
}

/// Jellyfin Quick Connect's code, large and selectable, while another Jellyfin app approves it.
private struct QuickConnectSection: View {
    let code: String
    let onCancel: () -> Void

    var body: some View {
        Section {
            VStack(spacing: 12) {
                Text("Enter this code in another Jellyfin app to sign in.")
                    .multilineTextAlignment(.center)
                Text(code)
                    .font(.system(.largeTitle, design: .monospaced).weight(.semibold))
                    .textSelection(.enabled)
                    .accessibilityIdentifier("serverSignIn.quickConnectCode")
                ProgressView()
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
            Button("Cancel", role: .cancel, action: onCancel)
                .frame(maxWidth: .infinity)
                .accessibilityIdentifier("serverSignIn.cancelQuickConnect")
        } header: {
            Text("Quick Connect")
        }
    }
}
