import Foundation
import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

@MainActor
struct ScrobblingViewTests {
    private func state(_ account: ScrobblingState.Account, busy: Bool = false) -> ScrobblingState {
        ScrobblingState(account: account, scrobbleServerStreams: false, approvalUrl: nil, message: nil, busy: busy)
    }

    // MARK: State mapping

    @Test func mapsTheSharedStateToPlainValues() {
        let shared = ScrobblingUiState(
            account: LastFmAccountStateSignedIn(username: "rj"),
            scrobbleServerStreams: true,
            approvalUrl: "https://www.last.fm/api/auth?token=t",
            message: .expired,
            busy: true
        )

        #expect(ScrobblingState(shared) == ScrobblingState(
            account: .signedIn(username: "rj"),
            scrobbleServerStreams: true,
            approvalUrl: URL(string: "https://www.last.fm/api/auth?token=t"),
            message: .expired,
            busy: true
        ))
    }

    @Test func theAccountSummaryFollowsTheAccount() {
        #expect(state(.signedIn(username: "rj")).accountSummary == "Signed in as rj")
        #expect(state(.awaitingApproval).accountSummary == "Approve Shuttle Music on last.fm, then come back here")
        #expect(state(.signedOut).accountSummary == "Not signed in")
    }

    // MARK: Coming back from the browser

    /// Whether each phase change, in order, finishes sign-in.
    private func finishes(_ changes: [(ScenePhase, awaiting: Bool)]) -> [Bool] {
        var browserReturn = BrowserReturn()
        return changes.map { browserReturn.phaseChanged(to: $0.0, awaiting: $0.awaiting) }
    }

    @Test func comingBackFromTheBrowserWhileAwaitingFinishesSignInOnce() {
        // Then an ordinary activation, which finishes nothing
        let changes: [(ScenePhase, awaiting: Bool)] = [(.inactive, true), (.background, true), (.active, true), (.inactive, true), (.active, true)]
        #expect(finishes(changes) == [false, false, true, false, false])
    }

    @Test func anActivationWithoutLeavingTheAppNeverFinishesSignIn() {
        // Notification Centre or Control Centre: inactive, never background
        #expect(finishes([(.inactive, true), (.active, true)]) == [false, false])
    }

    @Test func leavingWhenNotAwaitingOrComingBackSignedInFinishesNothing() {
        #expect(finishes([(.background, false), (.active, false)]) == [false, false])
        #expect(finishes([(.background, true), (.active, false)]) == [false, false])
    }

    // MARK: Content

    @Test func signedOutOffersSignIn() throws {
        var signedIn = false
        let sut = ScrobblingContent(state: state(.signedOut), onSignIn: { signedIn = true })
        try sut.inspect().find(viewWithAccessibilityIdentifier: "scrobbling.signIn").button().tap()

        #expect(signedIn)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "scrobbling.signOut")) == nil)
        #expect((try? sut.inspect().find(text: "Scrobbling powered by AudioScrobbler")) != nil)
    }

    @Test func awaitingApprovalOffersToFinishOrStartAgain() throws {
        var finished = false
        let sut = ScrobblingContent(state: state(.awaitingApproval), onFinishSignIn: { finished = true })
        try sut.inspect().find(viewWithAccessibilityIdentifier: "scrobbling.finishSignIn").button().tap()

        #expect(finished)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "scrobbling.signIn")) != nil)
    }

    @Test func signedInShowsTheAccountAndOffersSignOut() throws {
        var signedOut = false
        let sut = ScrobblingContent(state: state(.signedIn(username: "rj")), onSignOut: { signedOut = true })
        try sut.inspect().find(viewWithAccessibilityIdentifier: "scrobbling.signOut").button().tap()

        #expect(signedOut)
        #expect((try? sut.inspect().find(text: "Signed in as rj")) != nil)
    }

    @Test func theButtonsAreDisabledWhileBusy() throws {
        let sut = ScrobblingContent(state: state(.signedOut, busy: true))
        #expect(try sut.inspect().find(viewWithAccessibilityIdentifier: "scrobbling.signIn").button().isDisabled())
    }

    @Test func aBuildWithoutLastFmShowsOnlyTheServerStreamsSwitch() throws {
        var serverStreams: Bool?
        let sut = ScrobblingContent(state: state(.unavailable), onServerStreamsChange: { serverStreams = $0 })
        try sut.inspect().find(viewWithAccessibilityIdentifier: "scrobbling.serverStreams").toggle().tap()

        #expect(serverStreams == true)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "scrobbling.signIn")) == nil)
        #expect((try? sut.inspect().find(text: "Last.fm")) == nil)
    }
}
