import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// The server sign-in: `ServerSignInUiState` mapped to what iOS shows, the form for each step from plain values, Quick
/// Connect's code, Plex's PIN and server choice, and the events that connect the server and move the setup on.
@MainActor
struct ServerSignInViewTests {
    private func uiState(
        type: MediaProviderType = .jellyfin,
        address: String = "http://music.local:8096",
        username: String = "tim",
        missing: Set<ServerSignInField> = [],
        step: ServerSignInStep = ServerSignInStepForm.shared,
        quickConnectEnabled: Bool = false,
        discoveredServers: [DiscoveredServer] = [],
        headers: [CoreCustomHeader] = []
    ) -> ServerSignInUiState {
        ServerSignInUiState(
            type: type,
            form: ServerSignInForm(
                address: address, username: username, password: "hunter2",
                rememberPassword: false, passwordRevealable: true, missing: missing,
                headers: headers, showAdvanced: !headers.isEmpty
            ),
            step: step,
            events: [],
            showProDisclosure: false,
            quickConnectEnabled: quickConnectEnabled,
            discoveredServers: discoveredServers
        )
    }

    private func state(_ step: ServerSignInState.Step, quickConnect: Bool = false) -> ServerSignInState {
        var state = ServerSignInState(type: .jellyfin)
        state.address = "http://music.local:8096"
        state.username = "tim"
        state.step = step
        state.quickConnectEnabled = quickConnect
        return state
    }

    private func signInButton(_ sut: ServerSignInContent) throws -> InspectableView<ViewType.Button> {
        try sut.inspect().find(viewWithAccessibilityIdentifier: "serverSignIn.signIn").button()
    }

    // MARK: State mapping

    @Test func mapsTheFormAndMissingFields() {
        let state = ServerSignInState(uiState(type: .emby, missing: [.username], quickConnectEnabled: true))
        #expect(state.type == .emby)
        #expect(state.address == "http://music.local:8096")
        #expect(state.username == "tim")
        #expect(state.password == "hunter2")
        #expect(state.rememberPassword == false)
        #expect(state.missing == [.username])
        #expect(state.step == .form)
        #expect(state.quickConnectEnabled)
    }

    @Test func plexSignsInWithAPin() {
        #expect(ServerSignInState(uiState(type: .plex)).signsInWithPin)
        #expect(!ServerSignInState(uiState(type: .jellyfin)).signsInWithPin)
    }

    @Test func mapsTheDiscoveredServersButTheOneTyped() {
        let state = ServerSignInState(uiState(discoveredServers: [
            DiscoveredServer(name: "Den", address: "http://music.local:8096"),
            DiscoveredServer(name: "Attic", address: "http://192.168.1.30:8096"),
        ]))
        #expect(state.addressSuggestions == [ServerSignInState.Suggestion(name: "Attic", address: "http://192.168.1.30:8096")])
    }

    @Test func mapsEachStep() {
        #expect(ServerSignInState(uiState(step: ServerSignInStepAuthenticating.shared)).step == .authenticating)
        #expect(ServerSignInState(uiState(step: ServerSignInStepAwaitingCode(code: "123456"))).step == .awaitingCode("123456"))
        #expect(ServerSignInState(uiState(step: ServerSignInStepConnected.shared)).step == .connected)
        #expect(ServerSignInState(uiState(step: ServerSignInStepFailed(message: "HTTP 401"))).step == .failed("HTTP 401"))
        let pin = ServerSignInStepAwaitingPin(code: "ABCD", authUrl: "https://app.plex.tv/auth#?code=x", linkUrl: "https://plex.tv/link")
        #expect(
            ServerSignInState(uiState(type: .plex, step: pin)).step
                == .awaitingPin(code: "ABCD", authUrl: "https://app.plex.tv/auth#?code=x", linkUrl: "https://plex.tv/link")
        )
        let choosing = ServerSignInStepChoosingServer(servers: [ServerChoice(id: "a", name: "Den", owned: false)])
        #expect(ServerSignInState(uiState(type: .plex, step: choosing)).step == .choosingServer([.init(id: "a", name: "Den", owned: false)]))
        let untrusted = ServerSignInStepUntrustedCertificate(
            origin: CoreServerOrigin(host: "music.local", port: 8920), fingerprint: "abcdef", message: "TLS"
        )
        #expect(ServerSignInState(uiState(step: untrusted)).step == .untrustedCertificate(host: "music.local:8920", fingerprint: "AB:CD:EF"))
    }

    @Test func mapsTheServersCustomHeadersAndOpensAdvanced() {
        let state = ServerSignInState(uiState(headers: [CoreCustomHeader(name: "CF-Access-Client-Id", value: "id")]))
        #expect(state.headers == [ServerSignInState.Header(name: "CF-Access-Client-Id", value: "id")])
        #expect(state.showAdvanced)
        #expect(!ServerSignInState(uiState()).showAdvanced)
    }

    // MARK: Steps

    @Test func theFormShowsItsFieldsAndSignsIn() throws {
        var authenticated = false
        let sut = ServerSignInContent(state: state(.form), actions: ServerSignInActions(onAuthenticate: { authenticated = true }))
        for id in ["serverSignIn.address", "serverSignIn.username", "serverSignIn.password", "serverSignIn.rememberPassword"] {
            #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: id)) != nil, "\(id)")
        }
        #expect((try? sut.inspect().find(text: "Sign In")) != nil)
        #expect((try? sut.inspect().find(IconSquare.self)) != nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "serverSignIn.error")) == nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "serverSignIn.quickConnect")) == nil)
        try signInButton(sut).tap()
        #expect(authenticated)
    }

    @Test func aMissingFieldSaysSo() throws {
        var state = state(.form)
        state.missing = [.address]
        let sut = ServerSignInContent(state: state)
        #expect((try? sut.inspect().find(text: "Enter the address.")) != nil)
    }

    @Test func signingInShowsProgressAndDisablesTheButton() throws {
        let sut = ServerSignInContent(state: state(.authenticating))
        #expect((try? sut.inspect().find(text: "Signing In…")) != nil)
        #expect((try? sut.inspect().find(ViewType.ProgressView.self)) != nil)
        #expect(try signInButton(sut).isDisabled())
    }

    @Test func aFailureShowsItsErrorAndTryAgainRetriesThenSignsIn() throws {
        var calls: [String] = []
        let sut = ServerSignInContent(
            state: state(.failed("Invalid username or password")),
            actions: ServerSignInActions(onAuthenticate: { calls.append("authenticate") }, onRetry: { calls.append("retry") })
        )
        #expect((try? sut.inspect().find(text: "Invalid username or password")) != nil)
        #expect((try? sut.inspect().find(text: "Try Again")) != nil)
        #expect(try !signInButton(sut).isDisabled())
        try signInButton(sut).tap()
        #expect(calls == ["retry", "authenticate"])
    }

    @Test func connectedSaysSoAndCantSignInAgain() throws {
        let sut = ServerSignInContent(state: state(.connected))
        #expect((try? sut.inspect().find(text: "Signed In")) != nil)
        #expect(try signInButton(sut).isDisabled())
    }


    @Test func subsonicTakesAUsernameAndPasswordOrAnAPIKey() throws {
        let sut = ServerSignInContent(state: ServerSignInState(type: .subsonic))
        #expect((try? sut.inspect().find(text: "Connect to Navidrome / Subsonic")) != nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "serverSignIn.username")) != nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "serverSignIn.password")) != nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "serverSignIn.apiKeyNote")) != nil)

        let jellyfin = ServerSignInContent(state: state(.form))
        #expect((try? jellyfin.inspect().find(viewWithAccessibilityIdentifier: "serverSignIn.apiKeyNote")) == nil)
    }

    // MARK: Quick Connect

    @Test func quickConnectIsOfferedOnlyWhenTheServerSupportsIt() throws {
        var used = false
        let sut = ServerSignInContent(state: state(.form, quickConnect: true), actions: ServerSignInActions(onUseQuickConnect: { used = true }))
        try sut.inspect().find(viewWithAccessibilityIdentifier: "serverSignIn.quickConnect").button().tap()
        #expect(used)
    }

    @Test func quickConnectShowsTheCodeInPlaceOfTheFormAndCancels() throws {
        var cancelled = false
        let sut = ServerSignInContent(state: state(.awaitingCode("482913")), actions: ServerSignInActions(onCancelQuickConnect: { cancelled = true }))
        let code = try sut.inspect().find(viewWithAccessibilityIdentifier: "serverSignIn.quickConnectCode").text()
        #expect(try code.string() == "482913")
        #expect((try? sut.inspect().find(text: "Enter this code in another Jellyfin app to sign in.")) != nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "serverSignIn.address")) == nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "serverSignIn.signIn")) == nil)
        try sut.inspect().find(viewWithAccessibilityIdentifier: "serverSignIn.cancelQuickConnect").button().tap()
        #expect(cancelled)
    }

    // MARK: Network suggestions

    @Test func aSuggestionFillsInItsAddress() throws {
        var state = state(.form)
        state.addressSuggestions = [.init(name: "Attic", address: "http://192.168.1.30:8096")]
        let sut = ServerSignInContent(state: state)
        #expect((try? sut.inspect().find(text: "On Your Network")) != nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "serverSignIn.suggestion")) != nil)
        #expect((try? ServerSignInContent(state: self.state(.form)).inspect().find(viewWithAccessibilityIdentifier: "serverSignIn.suggestion")) == nil)
    }

    // MARK: Plex

    @Test func plexAsksForNoAddressOrPasswordAndSignsInWithPlex() throws {
        var authenticated = false
        let sut = ServerSignInContent(state: ServerSignInState(type: .plex), actions: ServerSignInActions(onAuthenticate: { authenticated = true }))
        #expect((try? sut.inspect().find(text: "Sign In with Plex")) != nil)
        #expect((try? sut.inspect().find(text: "Sign in with your plex.tv account in the browser, then pick your server.")) != nil)
        for id in ["serverSignIn.address", "serverSignIn.username", "serverSignIn.password"] {
            #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: id)) == nil, "\(id)")
        }
        try signInButton(sut).tap()
        #expect(authenticated)
    }

    @Test func plexShowsThePinOpensTheBrowserAndCancels() throws {
        var calls: [String] = []
        var plex = ServerSignInState(type: .plex)
        plex.step = .awaitingPin(code: "ABCD", authUrl: "https://app.plex.tv/auth#?code=x", linkUrl: "https://plex.tv/link")
        let sut = ServerSignInContent(
            state: plex,
            actions: ServerSignInActions(onOpenUrl: { calls.append($0) }, onCancelPin: { calls.append("cancel") })
        )
        #expect(try sut.inspect().find(viewWithAccessibilityIdentifier: "serverSignIn.pinCode").text().string() == "ABCD")
        #expect((try? sut.inspect().find(text: "On another device, go to plex.tv/link and enter the code.")) != nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "serverSignIn.signIn")) == nil)
        try sut.inspect().find(viewWithAccessibilityIdentifier: "serverSignIn.openBrowser").button().tap()
        try sut.inspect().find(viewWithAccessibilityIdentifier: "serverSignIn.cancelPin").button().tap()
        #expect(calls == ["https://app.plex.tv/auth#?code=x", "cancel"])
    }

    @Test func plexListsTheAccountsServersAndSignsInToTheOneTapped() throws {
        var chosen: [String] = []
        var plex = ServerSignInState(type: .plex)
        plex.step = .choosingServer([.init(id: "own", name: "Den", owned: true), .init(id: "shared", name: "Mum's", owned: false)])
        let sut = ServerSignInContent(state: plex, actions: ServerSignInActions(onChooseServer: { chosen.append($0) }))
        #expect((try? sut.inspect().find(text: "Den")) != nil)
        #expect((try? sut.inspect().find(text: "Shared with you")) != nil)
        try sut.inspect().find(viewWithAccessibilityIdentifier: "serverSignIn.server.shared").button().tap()
        #expect(chosen == ["shared"])
    }

    // MARK: Success

    @Test func connectedStartsTheImportFinishedGoesBackAndOpenUrlOpensIt() {
        var calls: [String] = []
        let outcome = ServerSignInOutcome(
            onConnected: { calls.append("connected") },
            onFinished: { calls.append("finished") },
            onOpenUrl: { calls.append($0) }
        )
        outcome.handle(ServerSignInEventConnected.shared)
        #expect(calls == ["connected"])
        outcome.handle(ServerSignInEventFinished.shared)
        #expect(calls == ["connected", "finished"])
        outcome.handle(ServerSignInEventOpenUrl(url: "https://app.plex.tv/auth"))
        #expect(calls == ["connected", "finished", "https://app.plex.tv/auth"])
    }

    // MARK: Address guidance

    @Test func theFooterShowsTheAddressTheSignInWillUse() throws {
        var state = state(.form)
        state.address = "192.168.1.20:8096/"
        let sut = ServerSignInContent(state: state)
        #expect((try? sut.inspect().find(text: "Connects to http://192.168.1.20:8096")) != nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "serverSignIn.addressInvalid")) == nil)
    }

    @Test func anAddressWithASpaceIsFlaggedButThePrefilledSchemeIsNot() throws {
        var invalid = state(.form)
        invalid.address = "http://my server"
        #expect((try? ServerSignInContent(state: invalid).inspect().find(viewWithAccessibilityIdentifier: "serverSignIn.addressInvalid")) != nil)
        var prefilled = state(.form)
        prefilled.address = "http://"
        #expect((try? ServerSignInContent(state: prefilled).inspect().find(viewWithAccessibilityIdentifier: "serverSignIn.addressInvalid")) == nil)
    }

    // MARK: Outcome

    @Test func aFailureExplainsItself() throws {
        let sut = ServerSignInContent(state: state(.failed("HTTP 401")))
        #expect((try? sut.inspect().find(text: "Couldn't Sign In")) != nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "serverSignIn.error")) != nil)
    }

    @Test func connectedShowsTheSuccessInPlaceOfTheForm() throws {
        let sut = ServerSignInContent(state: state(.connected))
        #expect((try? sut.inspect().find(text: "Signed in to Jellyfin")) != nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "serverSignIn.address")) == nil)
    }
}
