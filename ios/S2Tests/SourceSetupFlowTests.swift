import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// The source setup (#624): each step from plain values, and `SourceSetupImport` mapped to the import page. The first
/// run's gating itself is `SourceSetupViewModel`'s, tested in `:android:presentation`.
@MainActor
struct SourceSetupFlowTests {
    // MARK: Import state mapping

    @Test func mapsEachImportState() {
        #expect(SourceSetupImportState(SourceSetupImportNotStarted.shared) == .starting(nil))
        #expect(SourceSetupImportState(SourceSetupImportStarting(type: .jellyfin)) == .starting(.jellyfin))
        #expect(
            SourceSetupImportState(SourceSetupImportRunning(type: .emby, message: "Artist • Song", fraction: KotlinFloat(float: 0.5)))
                == .running(.emby, message: "Artist • Song", fraction: 0.5)
        )
        #expect(SourceSetupImportState(SourceSetupImportRunning(type: .emby, message: nil, fraction: nil)) == .running(.emby, message: nil, fraction: nil))
        #expect(SourceSetupImportState(SourceSetupImportFinished(type: .jellyfin, error: nil)) == .ready(.jellyfin))
        #expect(SourceSetupImportState(SourceSetupImportFinished(type: .jellyfin, error: "HTTP 500")) == .failed(.jellyfin, error: "HTTP 500"))
    }

    @Test func aFinishedImportIsAFullRing() {
        #expect(SourceSetupImportState.ready(.jellyfin).fraction == 1)
        #expect(SourceSetupImportState.starting(.jellyfin).fraction == nil)
        #expect(SourceSetupImportState.running(.jellyfin, message: nil, fraction: 0.25).fraction == 0.25)
    }

    // MARK: Welcome

    @Test func theWelcomeStartsOrSkips() throws {
        var calls: [String] = []
        let sut = SourceSetupWelcome(onStart: { calls.append("start") }, onSkip: { calls.append("skip") })
        #expect((try? sut.inspect().find(text: "Welcome to Shuttle Music")) != nil)
        try sut.inspect().find(viewWithAccessibilityIdentifier: "onboarding.getStarted").button().tap()
        try sut.inspect().find(viewWithAccessibilityIdentifier: "onboarding.skip").button().tap()
        #expect(calls == ["start", "skip"])
    }

    // MARK: Server cards

    @Test func theCardsOfferTheTypesIOSCanSignInTo() throws {
        #expect(MediaProviderType.signInTypes == [.jellyfin, .emby])
        var chosen: MediaProviderType?
        let sut = SourceTypeCards(types: MediaProviderType.signInTypes, onSelect: { chosen = $0 })
        #expect((try? sut.inspect().find(text: "Jellyfin")) != nil)
        #expect((try? sut.inspect().find(text: "Emby")) != nil)
        #expect((try? sut.inspect().find(text: "Plex")) == nil)
        try sut.inspect().find(viewWithAccessibilityIdentifier: "serverTypePicker.Emby").button().tap()
        #expect(chosen == .emby)
    }

    // MARK: Import page

    @Test func aRunningImportShowsItsProgressAndCanBeLeft() throws {
        var continued = false
        let sut = SourceSetupImportPage(
            state: .running(.jellyfin, message: "Artist • Song", fraction: 0.42),
            onRetry: {},
            onContinue: { continued = true }
        )
        #expect((try? sut.inspect().find(text: "Importing Your Music")) != nil)
        #expect((try? sut.inspect().find(text: "Artist • Song")) != nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "onboarding.importPercent")) != nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "onboarding.retry")) == nil)
        try sut.inspect().find(viewWithAccessibilityIdentifier: "onboarding.continue").button().tap()
        #expect(continued)
    }

    @Test func aFinishedImportIsReady() throws {
        let sut = SourceSetupImportPage(state: .ready(.emby), onRetry: {}, onContinue: {})
        #expect((try? sut.inspect().find(text: "Your Library Is Ready")) != nil)
        #expect((try? sut.inspect().find(text: "Start Listening")) != nil)
    }

    @Test func aFailedImportSaysWhyAndRetries() throws {
        var retried = false
        let sut = SourceSetupImportPage(state: .failed(.jellyfin, error: "HTTP 500"), onRetry: { retried = true }, onContinue: {})
        #expect((try? sut.inspect().find(text: "HTTP 500")) != nil)
        try sut.inspect().find(viewWithAccessibilityIdentifier: "onboarding.retry").button().tap()
        #expect(retried)
    }

    // MARK: Import activity

    @Test func theImportActivityIsATappableButton() throws {
        let sut = ImportActivityButton(status: .failed(provider: "Jellyfin", error: "HTTP 500"), onOpenSources: {})
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "importActivity").button()) != nil)
    }
}
