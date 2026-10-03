import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// The server sign-in: `ServerSignInUiState` mapped to what iOS shows, the form for each step from plain values, Quick
/// Connect's code, and the events that connect the server and move the setup on.
@MainActor
struct ServerSignInViewTests {
    private func uiState(
        type: MediaProviderType = .jellyfin,
        address: String = "http://music.local:8096",
        username: String = "tim",
        authCode: String = "",
        missing: Set<ServerSignInField> = [],
        step: ServerSignInStep = ServerSignInStepForm.shared,
        quickConnectEnabled: Bool = false
    ) -> ServerSignInUiState {
        ServerSignInUiState(
            type: type,
            form: ServerSignInForm(
                address: address, username: username, password: "hunter2", authCode: authCode,
                rememberPassword: false, passwordRevealable: true, missing: missing
            ),
            step: step,
            events: [],
            showProDisclosure: false,
            quickConnectEnabled: quickConnectEnabled
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

    @Test func mapsPlexsTwoFactorCode() {
        let plex = ServerSignInState(uiState(type: .plex, authCode: "123456"))
        #expect(plex.asksForAuthCode)
        #expect(plex.authCode == "123456")
        #expect(!ServerSignInState(uiState(type: .jellyfin)).asksForAuthCode)
    }

    @Test func mapsEachStep() {
        #expect(ServerSignInState(uiState(step: ServerSignInStepAuthenticating.shared)).step == .authenticating)
        #expect(ServerSignInState(uiState(step: ServerSignInStepAwaitingCode(code: "123456"))).step == .awaitingCode("123456"))
        #expect(ServerSignInState(uiState(step: ServerSignInStepConnected.shared)).step == .connected)
        #expect(ServerSignInState(uiState(step: ServerSignInStepFailed(message: "HTTP 401"))).step == .failed("HTTP 401"))
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

    @Test func plexAsksForAPlexTvAccountAndAnOptionalTwoFactorCode() throws {
        var plex = ServerSignInState(type: .plex)
        plex.asksForAuthCode = true
        let sut = ServerSignInContent(state: plex)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "serverSignIn.authCode")) != nil)
        #expect((try? sut.inspect().find(text: "Your server's address, then your plex.tv account.")) != nil)

        let jellyfin = ServerSignInContent(state: state(.form))
        #expect((try? jellyfin.inspect().find(viewWithAccessibilityIdentifier: "serverSignIn.authCode")) == nil)
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

    // MARK: Success

    @Test func connectedStartsTheImportAndFinishedGoesBack() {
        var calls: [String] = []
        let outcome = ServerSignInOutcome(onConnected: { calls.append("connected") }, onFinished: { calls.append("finished") })
        outcome.handle(ServerSignInEventConnected.shared)
        #expect(calls == ["connected"])
        outcome.handle(ServerSignInEventFinished.shared)
        #expect(calls == ["connected", "finished"])
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
