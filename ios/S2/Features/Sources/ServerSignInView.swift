import Shared
import SwiftUI

/// A Jellyfin or Emby server's sign-in (#587, #624), on the shared `ServerSignInViewModel`: Android's
/// `ServerSignInRoute`/`ServerSignInDialog` as a HIG form, a step of the source setup (`SourceSetupFlow`), which is
/// the one place it opens from: the first run, Sources' Add a Server and Sign In Again. It starts from the saved
/// login; once the server is signed in it calls `onConnected` (the setup enables the provider and imports), then
/// `onFinished` when the view model says the success state has shown for long enough. Plex, and its 2FA code field,
/// join with the Plex provider in `:shared`; until then the setup doesn't offer it (`MediaProviderType.signInTypes`).
struct ServerSignInView: View {
    let type: MediaProviderType
    /// The view model's `ViewModelCache` key, kept live by whoever shows the form (`Navigator.sourceSetupLive`).
    let cacheKey: String
    let onConnected: () -> Void
    let onFinished: () -> Void

    var body: some View {
        let signIn = ViewModelCache.shared.viewModel(cacheKey) {
            AppGraph.shared.serverSignInViewModelFactory.create(type: type)
        }
        Observing(signIn.uiState) { state in
            ServerSignInContent(state: ServerSignInState(state), actions: ServerSignInActions(signIn))
                .consumeEvents(state.events, handled: { signIn.onEventHandled(id: $0) }) { event in
                    ServerSignInOutcome(onConnected: onConnected, onFinished: onFinished).handle(event)
                }
        }
        .navigationTitle(type.title)
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// What the sign-in's one-shot events do: `Connected` starts the import, `Finished` moves on to its progress.
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
            SignInHeader(type: state.type)
            switch state.step {
            case .awaitingCode(let code):
                QuickConnectSection(code: code, onCancel: actions.onCancelQuickConnect)
            case .connected:
                SignedInSection(type: state.type)
                submission
            case .form, .authenticating, .failed:
                addressSection
                if state.quickConnectEnabled, editable {
                    quickConnectOffer
                }
                accountSection
                if case .failed(let message) = state.step {
                    SignInErrorSection(message: message)
                }
                submission
            }
        }
        .sensoryFeedback(.success, trigger: state.step == .connected) { _, connected in connected }
        .sensoryFeedback(.error, trigger: state.step.isFailure) { _, failed in failed }
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

    /// The address as the sign-in will use it (`serverAddress`: scheme added, trailing slashes dropped), or nil.
    private var resolvedAddress: String? {
        ServerSignInViewModelKt.serverAddress(typed: address)
    }

    /// The address box holds more than the prefilled scheme, yet no host the sign-in can use.
    private var addressLooksInvalid: Bool {
        let typed = address.trimmingCharacters(in: .whitespaces)
        return resolvedAddress == nil && !typed.isEmpty && !typed.hasSuffix("://")
    }

    private var addressSection: some View {
        Section {
            TextField("Address", text: $address, prompt: Text("192.168.1.20:8096"))
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
            } else if addressLooksInvalid {
                Label("That isn't a server address. Try one like 192.168.1.20:8096.", systemImage: "exclamationmark.circle")
                    .foregroundStyle(.red)
                    .accessibilityIdentifier("serverSignIn.addressInvalid")
            } else if let resolvedAddress {
                Text("Connects to \(resolvedAddress)")
                    .accessibilityIdentifier("serverSignIn.resolvedAddress")
            } else {
                Text("An IP address or host name, with its port. https:// if your server uses it; http:// is added if you leave it out.")
            }
        }
        .disabled(!editable)
    }

    /// Quick Connect, offered first when the server supports it: no password to type.
    private var quickConnectOffer: some View {
        Section {
            Button(action: actions.onUseQuickConnect) {
                Label("Sign In with Quick Connect", systemImage: "qrcode")
                    .font(.s2Headline)
                    .frame(maxWidth: .infinity)
            }
            .accessibilityIdentifier("serverSignIn.quickConnect")
        } footer: {
            Text("Approve a code from another \(state.type.title) app you're signed in to. No password needed.")
        }
    }

    private var accountSection: some View {
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
            Text(state.quickConnectEnabled ? "Or With a Password" : "Account")
        } footer: {
            if state.missing.contains(.username) {
                RequiredNote(field: "username")
            } else if state.missing.contains(.password) {
                RequiredNote(field: "password")
            }
        }
        .disabled(!editable)
    }

    private var submission: some View {
        Section {
            Button(action: signIn) {
                HStack(spacing: Spacing.small) {
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
                .font(.s2Headline)
                .frame(maxWidth: .infinity)
            }
            .disabled(!editable)
            .accessibilityIdentifier("serverSignIn.signIn")
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

/// The form's header: the server type's glyph, centred, over what the form is for.
private struct SignInHeader: View {
    let type: MediaProviderType

    var body: some View {
        Section {
            VStack(spacing: Spacing.smallMedium) {
                IconSquare(systemImage: type.symbol, style: .filled(type.color), size: .large)
                    .scaleEffect(1.25)
                    .padding(Spacing.small)
                Text("Connect to \(type.title)")
                    .font(.s2Title3)
                    .multilineTextAlignment(.center)
                    .accessibilityAddTraits(.isHeader)
                Text("Your server's address, then your \(type.title) account.")
                    .font(.subheadline)
                    .foregroundStyle(.s2SecondaryText)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets())
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

/// Why the sign-in failed, the server's own words under a plain heading, and what usually fixes it.
private struct SignInErrorSection: View {
    let message: String

    var body: some View {
        Section {
            VStack(alignment: .leading, spacing: Spacing.xsmall) {
                Label("Couldn't Sign In", systemImage: "exclamationmark.triangle.fill")
                    .font(.s2Headline)
                    .foregroundStyle(.red)
                Text(message)
                Text("Check the address and your account, and that the server is running, then try again.")
                    .font(.footnote)
                    .foregroundStyle(.s2SecondaryText)
            }
            .padding(.vertical, Spacing.xsmall)
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("serverSignIn.error")
        }
    }
}

/// The sign-in succeeded: a moment of confirmation before the setup moves on to the import.
private struct SignedInSection: View {
    let type: MediaProviderType

    var body: some View {
        Section {
            Label("Signed in to \(type.title)", systemImage: "checkmark.seal.fill")
                .font(.s2Headline)
                .foregroundStyle(.green)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.small)
                .accessibilityIdentifier("serverSignIn.connected")
        }
    }
}

/// Jellyfin Quick Connect's code, large and selectable (and copyable), while another Jellyfin app approves it.
private struct QuickConnectSection: View {
    let code: String
    let onCancel: () -> Void

    var body: some View {
        Section {
            VStack(spacing: Spacing.medium) {
                Text("Enter this code in another Jellyfin app to sign in.")
                    .font(.s2Headline)
                    .multilineTextAlignment(.center)
                Text(code)
                    .font(.system(.largeTitle, design: .monospaced).weight(.bold))
                    .tracking(Spacing.xsmall)
                    .textSelection(.enabled)
                    // No spelled-out label: Maestro's sign-in flows copy the code from this element's text.
                    .accessibilityIdentifier("serverSignIn.quickConnectCode")
                Button("Copy Code", systemImage: "doc.on.doc") { UIPasteboard.general.string = code }
                    .buttonStyle(.bordered)
                    .buttonBorderShape(.capsule)
                    .accessibilityIdentifier("serverSignIn.copyQuickConnectCode")
                HStack(spacing: Spacing.small) {
                    ProgressView()
                    Text("Waiting for approval…").foregroundStyle(.s2SecondaryText)
                }
                .font(.subheadline)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.medium)
            Button("Cancel", role: .cancel, action: onCancel)
                .frame(maxWidth: .infinity)
                .accessibilityIdentifier("serverSignIn.cancelQuickConnect")
        } header: {
            Text("Quick Connect")
        } footer: {
            Text("In Jellyfin's web app or another client, open your profile, then Quick Connect, and enter the code.")
        }
    }
}

extension ServerSignInState.Step {
    var isFailure: Bool {
        if case .failed = self { true } else { false }
    }
}
