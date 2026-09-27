import Foundation
import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// Sources: `SourcesUiState` mapped to what iOS shows, the rows from plain values, the type picker, and choosing a
/// type pushing the sign-in route.
@MainActor
struct SourcesViewTests {
    private func uiState(
        connected: [MediaProviderType] = [],
        scan: ScanProgress? = nil,
        scanError: String? = nil
    ) -> SourcesUiState {
        SourcesUiState(
            thisDevice: false,
            usesAndroidProvider: false,
            folders: FolderLists(includes: [], excludes: [], extras: []),
            scan: scan,
            scanError: scanError,
            servers: SourcesViewModelKt.ServerTypes.map { ServerSource(type: $0, connected: connected.contains($0)) },
            events: []
        )
    }

    // MARK: State mapping

    @Test func mapsOnlyTheConnectedServersInTheViewModelsOrder() {
        let state = SourcesState(uiState(connected: [.plex, .jellyfin]))
        #expect(state.servers == [.jellyfin, .plex])
        #expect(state.scan == .idle)
    }

    @Test func mapsARunningScanAheadOfAnEarlierError() {
        let state = SourcesState(uiState(scan: ScanProgress(message: "Reading songs", fraction: KotlinFloat(float: 0.25)), scanError: "old"))
        #expect(state.scan == .scanning(message: "Reading songs", fraction: 0.25))
    }

    @Test func mapsAFailedScan() {
        #expect(SourcesState(uiState(scanError: "HTTP 401")).scan == .failed("HTTP 401"))
    }

    // MARK: Content

    @Test func listsConnectedServersWithTheirStatus() throws {
        let sut = SourcesContent(state: SourcesState(servers: [.jellyfin]))
        #expect((try? sut.inspect().find(text: "Jellyfin")) != nil)
        #expect((try? sut.inspect().find(text: "Connected")) != nil)
        #expect((try? sut.inspect().find(text: "Emby")) == nil)
        #expect((try? sut.inspect().find(text: "Scan Now")) != nil)
    }

    @Test func noServersExplainsWhatToConnectAndHidesTheScan() throws {
        let sut = SourcesContent(state: SourcesState(servers: []))
        #expect((try? sut.inspect().find(text: "Stream your library from a Jellyfin or Emby server.")) != nil)
        #expect((try? sut.inspect().find(text: "Scan Now")) == nil)
    }

    @Test func connectAServerAsksForThePicker() throws {
        var asked = false
        let sut = SourcesContent(state: SourcesState(servers: []), onAddServer: { asked = true })
        try sut.inspect().find(viewWithAccessibilityIdentifier: "sources.addServer").button().tap()
        #expect(asked)
    }

    @Test func showsAFailedScanAndRescans() throws {
        var rescanned = false
        let sut = SourcesContent(state: SourcesState(servers: [.emby], scan: .failed("HTTP 500")), onRescan: { rescanned = true })
        #expect((try? sut.inspect().find(text: "Scan failed: HTTP 500. Tap to try again")) != nil)
        try sut.inspect().find(viewWithAccessibilityIdentifier: "sources.rescan").button().tap()
        #expect(rescanned)
    }

    // MARK: Picker -> sign-in route

    @Test func pickerListsTheTypesIOSCanSignInTo() throws {
        #expect(MediaProviderType.signInTypes == [.jellyfin, .emby])
        let sut = ServerTypePicker(types: MediaProviderType.signInTypes, onSelect: { _ in })
        for title in ["Jellyfin", "Emby"] {
            #expect((try? sut.inspect().find(text: title)) != nil)
        }
        #expect((try? sut.inspect().find(text: "Plex")) == nil)
    }

    @Test func choosingATypeInThePickerReportsIt() throws {
        var chosen: MediaProviderType?
        let sut = ServerTypePicker(types: MediaProviderType.signInTypes, onSelect: { chosen = $0 })
        try sut.inspect().find(viewWithAccessibilityIdentifier: "serverTypePicker.Emby").button().tap()
        #expect(chosen == .emby)
    }

    @Test func aChosenTypePushesItsSignInRouteOntoTheCurrentStack() {
        let navigator = Navigator(viewModelCache: ViewModelCache())
        navigator.selectTab(.library)
        navigator.open(.sources)
        let choice = ServerTypeChoice(tryAddServer: { true }, choose: { navigator.open(.serverSignIn($0)) })
        choice.select(.jellyfin)
        #expect(navigator.libraryPath == [.sources, .serverSignIn(type: "Jellyfin")])
    }

    @Test func theEntitlementGateStopsTheSignIn() {
        var chosen: MediaProviderType?
        ServerTypeChoice(tryAddServer: { false }, choose: { chosen = $0 }).select(.plex)
        #expect(chosen == nil)
    }

    @Test func sourcesRoutesHaveKeysAndSurviveAStoredPath() throws {
        #expect(Route.sources.cacheKey == "sources")
        #expect(Route.serverSignIn(.plex).cacheKey == "serverSignIn:Plex")
        let routes: [Route] = [.sources, .serverSignIn(.emby)]
        let decoded = try JSONDecoder().decode([Route].self, from: JSONEncoder().encode(routes))
        #expect(decoded == routes)
        #expect(Route.serverType(named: "Emby") == .emby)
        #expect(Route.serverType(named: "Gopher") == nil)
    }

    // MARK: Reachability

    @Test func theEmptyLibraryOffersSources() throws {
        let sut = LibraryRootContent(categories: LibraryCategory.allCases, availability: .empty, importStatus: .idle)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "libraryEmpty.addSource")) != nil)
    }

    @Test func emptyHomeOffersSources() throws {
        let sut = HomeContent(state: HomeUiStateEmpty.shared)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "homeEmpty.addSource")) != nil)
    }
}
