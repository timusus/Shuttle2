import Shared
import SwiftUI

/// A Jellyfin, Emby, Plex or Subsonic server's sign-in (#587, #624), on the shared `ServerSignInViewModel`: Android's
/// `ServerSignInRoute`/`ServerSignInDialog` as a HIG form, a step of the source setup (`SourceSetupFlow`), which is
/// the one place it opens from: the first run, Sources' Add a Server and Sign In Again. It starts from the saved
/// login; once the server is signed in it calls `onConnected` (the setup enables the provider and imports), then
/// `onFinished` when the view model says the success state has shown for long enough. Plex signs in with a plex.tv
/// PIN approved in the browser, then a choice of the account's servers, as on Android.
struct ServerSignInView: View {
    let type: MediaProviderType
    /// The view model's `ViewModelCache` key, kept live by whoever shows the form (`Navigator.sourceSetupLive`).
    let cacheKey: String
    let onConnected: () -> Void
    let onFinished: () -> Void

    @Environment(\.openURL) private var openURL

    var body: some View {
        let signIn = ViewModelCache.shared.viewModel(cacheKey) {
            AppGraph.shared.serverSignInViewModelFactory.create(type: type)
        }
        Observing(signIn.uiState) { state in
            ServerSignInContent(state: ServerSignInState(state), actions: ServerSignInActions(signIn, openURL: open))
                .consumeEvents(state.events, handled: { signIn.onEventHandled(id: $0) }) { event in
                    ServerSignInOutcome(onConnected: onConnected, onFinished: onFinished, onOpenUrl: open).handle(event)
                }
        }
        // The view model outlives this view (cached for the whole setup): a Quick Connect code or PIN left on screen
        // when the user goes back stops polling here rather than when the setup closes.
        .onDisappear { signIn.onLeave() }
        .navigationTitle(type.title)
        .navigationBarTitleDisplayMode(.inline)
    }

    private func open(_ url: String) {
        if let url = URL(string: url) { openURL(url) }
    }
}

/// What the sign-in's one-shot events do: `Connected` starts the import, `Finished` moves on to its progress, and
/// `OpenUrl` opens Plex's sign-in page in the browser.
struct ServerSignInOutcome {
    let onConnected: () -> Void
    let onFinished: () -> Void
    var onOpenUrl: (String) -> Void = { _ in }

    func handle(_ event: ServerSignInEvent) {
        switch onEnum(of: event) {
        case .connected: onConnected()
        case .finished: onFinished()
        case .openUrl(let open): onOpenUrl(open.url)
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
        /// Plex's sign-in PIN, waiting for approval at `authUrl` in the browser, or with the code at `linkUrl`.
        case awaitingPin(code: String, authUrl: String, linkUrl: String)
        /// The Plex account's servers, to pick the one to sign in to.
        case choosingServer([ServerOption])
        case connected
        case failed(String)
        /// The server's certificate isn't one the system trusts (a self-signed one, say): its fingerprint, to trust it.
        case untrustedCertificate(host: String, fingerprint: String)
    }

    /// A request header the user added for the server, sent with every request to it.
    struct Header: Equatable {
        var name: String
        var value: String
    }

    /// One of the Plex account's servers: the user's own, or shared with them.
    struct ServerOption: Equatable, Identifiable {
        let id: String
        let name: String
        let owned: Bool
    }

    /// A server that answered on the local network, offered as an address.
    struct Suggestion: Equatable {
        let name: String
        let address: String
    }

    var type: MediaProviderType
    var address = ""
    var username = ""
    var password = ""
    var rememberPassword = true
    var headers: [Header] = []
    var showAdvanced = false
    var missing: Set<Field> = []
    var step: Step = .form
    var showProDisclosure = false
    var quickConnectEnabled = false
    /// Plex signs in with a plex.tv PIN and a choice of servers: no address or password.
    var signsInWithPin = false
    /// Discovered servers, all but the one already typed. iOS finds none yet (no multicast entitlement).
    var addressSuggestions: [Suggestion] = []
    /// A Subsonic server takes an OpenSubsonic API key as the password, with no username.
    var acceptsApiKey = false

    init(type: MediaProviderType) {
        self.type = type
        acceptsApiKey = type == .subsonic
        signsInWithPin = type == .plex
    }

    init(_ state: ServerSignInUiState) {
        type = state.type
        address = state.form.address
        username = state.form.username
        password = state.form.password
        rememberPassword = state.form.rememberPassword
        headers = state.form.headers.map { Header(name: $0.name, value: $0.value) }
        showAdvanced = state.form.showAdvanced
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
        case .awaitingPin(let pin): .awaitingPin(code: pin.code, authUrl: pin.authUrl, linkUrl: pin.linkUrl)
        case .choosingServer(let choosing):
            .choosingServer(choosing.servers.map { ServerOption(id: $0.id, name: $0.name, owned: $0.owned) })
        case .connected: .connected
        case .failed(let failed): .failed(failed.message)
        case .untrustedCertificate(let untrusted):
            .untrustedCertificate(host: untrusted.host, fingerprint: untrusted.displayedFingerprint)
        }
        showProDisclosure = state.showProDisclosure
        quickConnectEnabled = state.quickConnectEnabled
        signsInWithPin = state.signsInWithPin
        addressSuggestions = state.addressSuggestions.map { Suggestion(name: $0.name, address: $0.address) }
        acceptsApiKey = state.acceptsApiKey
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
    var onOpenUrl: (String) -> Void = { _ in }
    var onChooseServer: (String) -> Void = { _ in }
    var onCancelPin: () -> Void = {}
    var onShowAdvancedChange: (Bool) -> Void = { _ in }
    var onAddHeader: () -> Void = {}
    var onHeaderChange: (Int, String, String) -> Void = { _, _, _ in }
    var onRemoveHeader: (Int) -> Void = { _ in }
    var onTrustCertificate: () -> Void = {}
}

extension ServerSignInActions {
    init(_ viewModel: ServerSignInViewModel, openURL: @escaping (String) -> Void) {
        self.init(
            onAddressChange: { viewModel.onAddressChange(address: $0) },
            onUsernameChange: { viewModel.onUsernameChange(username: $0) },
            onPasswordChange: { viewModel.onPasswordChange(password: $0) },
            onRememberPasswordChange: { viewModel.onRememberPasswordChange(remember: $0) },
            onAuthenticate: { viewModel.onAuthenticate() },
            onRetry: { viewModel.onRetry() },
            onUseQuickConnect: { viewModel.onUseQuickConnect() },
            onCancelQuickConnect: { viewModel.onCancelQuickConnect() },
            onOpenUrl: openURL,
            onChooseServer: { viewModel.onChooseServer(id: $0) },
            onCancelPin: { viewModel.onCancelPin() },
            onShowAdvancedChange: { viewModel.onShowAdvancedChange(show: $0) },
            onAddHeader: { viewModel.onAddHeader() },
            onHeaderChange: { viewModel.onHeaderChange(index: Int32($0), name: $1, value: $2) },
            onRemoveHeader: { viewModel.onRemoveHeader(index: Int32($0)) },
            onTrustCertificate: { viewModel.onTrustCertificate() }
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
    @State private var headers: [ServerSignInState.Header]
    @State private var showAdvanced: Bool

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
        _headers = State(initialValue: state.headers)
        _showAdvanced = State(initialValue: state.showAdvanced)
    }

    var body: some View {
        Form {
            SignInHeader(type: state.type)
            switch state.step {
            case .awaitingCode(let code):
                QuickConnectSection(code: code, onCancel: actions.onCancelQuickConnect)
            case let .awaitingPin(code, authUrl, linkUrl):
                SignInPinSection(code: code, linkUrl: linkUrl, onOpenBrowser: { actions.onOpenUrl(authUrl) }, onCancel: actions.onCancelPin)
            case .choosingServer(let servers):
                ServerChoiceSection(servers: servers, onChoose: actions.onChooseServer, onCancel: actions.onCancelPin)
            case .connected:
                SignedInSection(type: state.type)
                submission
            case let .untrustedCertificate(host, fingerprint):
                UntrustedCertificateSection(host: host, fingerprint: fingerprint, onTrust: actions.onTrustCertificate, onCancel: actions.onRetry)
            case .form, .authenticating, .failed:
                if state.signsInWithPin {
                    pinSignIn
                } else {
                    passwordSignIn
                }
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
        .onChange(of: showAdvanced) { _, value in actions.onShowAdvancedChange(value) }
    }

    private var editable: Bool {
        switch state.step {
        case .form, .failed: true
        case .authenticating, .awaitingCode, .awaitingPin, .choosingServer, .connected, .untrustedCertificate: false
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
            TextField("Address", text: $address, prompt: Text(state.type.exampleAddress))
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
                Label("That isn't a server address. Try one like \(state.type.exampleAddress).", systemImage: "exclamationmark.circle")
                    .foregroundStyle(.s2Error)
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

    /// Plex: no address or password, just the button that opens plex.tv's sign-in.
    @ViewBuilder private var pinSignIn: some View {
        if case .failed(let message) = state.step {
            SignInErrorSection(message: message)
        }
        submission
    }

    @ViewBuilder private var passwordSignIn: some View {
        addressSection
        if !state.addressSuggestions.isEmpty, editable {
            suggestionsSection
        }
        if state.quickConnectEnabled, editable {
            quickConnectOffer
        }
        accountSection
        advancedSection
        if case .failed(let message) = state.step {
            SignInErrorSection(message: message)
        }
        submission
    }

    /// Custom headers, for a server behind a reverse proxy that wants its own (Cloudflare Access, Authelia).
    private var advancedSection: some View {
        Section {
            Toggle("Advanced", isOn: $showAdvanced)
                .s2Switch()
                .accessibilityIdentifier("serverSignIn.advanced")
            if showAdvanced {
                ForEach(headers.indices, id: \.self) { index in
                    HStack {
                        VStack {
                            TextField("Header", text: headerBinding(index, \.name))
                                .accessibilityIdentifier("serverSignIn.headerName.\(index)")
                            TextField("Value", text: headerBinding(index, \.value))
                                .accessibilityIdentifier("serverSignIn.headerValue.\(index)")
                        }
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        Button("Remove Header", systemImage: "minus.circle.fill", role: .destructive) {
                            headers.remove(at: index)
                            actions.onRemoveHeader(index)
                        }
                        .labelStyle(.iconOnly)
                        .buttonStyle(.borderless)
                        .accessibilityIdentifier("serverSignIn.removeHeader.\(index)")
                    }
                }
                Button("Add Header", systemImage: "plus.circle") {
                    headers.append(ServerSignInState.Header(name: "", value: ""))
                    actions.onAddHeader()
                }
                .accessibilityIdentifier("serverSignIn.addHeader")
            }
        } footer: {
            if showAdvanced {
                Text("Custom headers are sent with every request to this server, for a reverse proxy such as Cloudflare Access or Authelia. Headers the app sets itself, such as Host, Authorization or the server's own token, are ignored.")
            }
        }
        .disabled(!editable)
    }

    /// One header's name or value, edited here and sent to the view model with its row.
    private func headerBinding(_ index: Int, _ part: WritableKeyPath<ServerSignInState.Header, String>) -> Binding<String> {
        Binding(
            get: { headers.indices.contains(index) ? headers[index][keyPath: part] : "" },
            set: { text in
                guard headers.indices.contains(index) else { return }
                headers[index][keyPath: part] = text
                actions.onHeaderChange(index, headers[index].name, headers[index].value)
            }
        )
    }

    /// Servers that answered on the local network; tapping one fills in its address.
    private var suggestionsSection: some View {
        Section {
            ForEach(state.addressSuggestions, id: \.address) { suggestion in
                Button {
                    address = suggestion.address
                } label: {
                    LabeledContent(suggestion.name, value: suggestion.address)
                }
                .accessibilityIdentifier("serverSignIn.suggestion")
            }
        } header: {
            Text("On Your Network")
        }
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
                .s2Switch()
                .accessibilityIdentifier("serverSignIn.rememberPassword")
        } header: {
            Text(state.quickConnectEnabled ? "Or With a Password" : "Account")
        } footer: {
            if state.missing.contains(.username) {
                RequiredNote(field: "username")
            } else if state.missing.contains(.password) {
                RequiredNote(field: "password")
            } else if state.acceptsApiKey {
                Text("Leave the username empty to sign in with an API key as the password.")
                    .accessibilityIdentifier("serverSignIn.apiKeyNote")
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
                    case .form, .awaitingCode, .awaitingPin, .choosingServer, .untrustedCertificate:
                        Text(state.signsInWithPin ? "Sign In with Plex" : "Sign In")
                    }
                }
                .font(.s2Headline)
                .frame(maxWidth: .infinity)
            }
            .disabled(!editable)
            .accessibilityIdentifier("serverSignIn.signIn")
        } footer: {
            if state.showProDisclosure {
                Text(ProFeatures.signInDisclosure)
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
        case .authenticating, .awaitingCode, .awaitingPin, .choosingServer, .connected, .untrustedCertificate:
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
                IconSquare(glyph: type.glyph, style: .filled(type.color), size: .large)
                    .scaleEffect(1.25)
                    .padding(Spacing.small)
                Text("Connect to \(type.title)")
                    .font(.s2Title3)
                    .multilineTextAlignment(.center)
                    .accessibilityAddTraits(.isHeader)
                Text(type == .plex ? "Sign in with your plex.tv account in the browser, then pick your server." : "Your server's address, then your \(type.title) account.")
                    .font(.subheadline)
                    .foregroundStyle(.s2TextSecondary)
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
            .foregroundStyle(.s2Error)
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
                    .foregroundStyle(.s2Error)
                Text(message)
                Text("Check the address and your account, and that the server is running, then try again.")
                    .font(.footnote)
                    .foregroundStyle(.s2TextSecondary)
            }
            .padding(.vertical, Spacing.xsmall)
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("serverSignIn.error")
        }
    }
}

/// The server presented a certificate the system doesn't trust: its SHA-256 fingerprint, to trust for this server alone
/// once the user has checked it against the server's. Cancel goes back to the form.
private struct UntrustedCertificateSection: View {
    let host: String
    let fingerprint: String
    let onTrust: () -> Void
    let onCancel: () -> Void

    var body: some View {
        Section {
            VStack(alignment: .leading, spacing: Spacing.small) {
                Label("Untrusted Certificate", systemImage: "lock.trianglebadge.exclamationmark")
                    .font(.s2Headline)
                    .foregroundStyle(.s2Error)
                Text("\(host) presented a certificate this device doesn't trust, such as a self-signed one. Trust it only if this SHA-256 fingerprint matches your server's certificate.")
                Text(fingerprint)
                    .font(.system(.footnote, design: .monospaced))
                    .textSelection(.enabled)
                    .accessibilityIdentifier("serverSignIn.certificateFingerprint")
            }
            .padding(.vertical, Spacing.xsmall)
            Button("Trust Certificate", action: onTrust)
                .font(.s2Headline)
                .frame(maxWidth: .infinity)
                .accessibilityIdentifier("serverSignIn.trustCertificate")
            Button("Cancel", role: .cancel, action: onCancel)
                .frame(maxWidth: .infinity)
                .accessibilityIdentifier("serverSignIn.cancelTrust")
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
                .foregroundStyle(.s2Success)
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
                    Text("Waiting for approval…").foregroundStyle(.s2TextSecondary)
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

/// Plex's sign-in PIN: the browser opens on its own, and the code works at plex.tv/link on another device.
private struct SignInPinSection: View {
    let code: String
    let linkUrl: String
    let onOpenBrowser: () -> Void
    let onCancel: () -> Void

    var body: some View {
        Section {
            VStack(spacing: Spacing.medium) {
                Text("Approve the sign-in in your browser.")
                    .font(.s2Headline)
                    .multilineTextAlignment(.center)
                Text(code)
                    .font(.system(.largeTitle, design: .monospaced).weight(.bold))
                    .tracking(Spacing.xsmall)
                    .textSelection(.enabled)
                    .accessibilityIdentifier("serverSignIn.pinCode")
                Button("Open Browser", systemImage: "safari", action: onOpenBrowser)
                    .buttonStyle(.bordered)
                    .buttonBorderShape(.capsule)
                    .accessibilityIdentifier("serverSignIn.openBrowser")
                HStack(spacing: Spacing.small) {
                    ProgressView()
                    Text("Waiting for approval…").foregroundStyle(.s2TextSecondary)
                }
                .font(.subheadline)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.medium)
            Button("Cancel", role: .cancel, action: onCancel)
                .frame(maxWidth: .infinity)
                .accessibilityIdentifier("serverSignIn.cancelPin")
        } header: {
            Text("Plex")
        } footer: {
            Text("On another device, go to \(linkUrl.replacingOccurrences(of: "https://", with: "")) and enter the code.")
        }
    }
}

/// The Plex account's servers, the user's own first; tapping one signs in to it.
private struct ServerChoiceSection: View {
    let servers: [ServerSignInState.ServerOption]
    let onChoose: (String) -> Void
    let onCancel: () -> Void

    var body: some View {
        Section {
            ForEach(servers) { server in
                Button {
                    onChoose(server.id)
                } label: {
                    VStack(alignment: .leading, spacing: Spacing.xsmall) {
                        Text(server.name).foregroundStyle(.primary)
                        if !server.owned {
                            Text("Shared with you")
                                .font(.footnote)
                                .foregroundStyle(.s2TextSecondary)
                        }
                    }
                }
                .accessibilityIdentifier("serverSignIn.server.\(server.id)")
            }
        } header: {
            Text("Choose a Server")
        }
        Section {
            Button("Cancel", role: .cancel, action: onCancel)
                .frame(maxWidth: .infinity)
                .accessibilityIdentifier("serverSignIn.cancelPin")
        }
    }
}

extension ServerSignInState.Step {
    var isFailure: Bool {
        if case .failed = self { true } else { false }
    }
}

extension MediaProviderType {
    /// An address like this type's server listens on, for the address field's prompt.
    var exampleAddress: String {
        switch self {
        case .plex: "192.168.1.20:32400"
        // Navidrome is usually reached through a reverse proxy, by name
        case .subsonic: "https://music.example.com"
        default: "192.168.1.20:8096"
        }
    }
}
