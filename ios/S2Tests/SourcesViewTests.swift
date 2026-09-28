import Foundation
import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// Sources: `SourcesUiState` and the import mapped to what iOS shows, the rows and the empty state from plain values.
@MainActor
struct SourcesViewTests {
    private func uiState(
        connected: [MediaProviderType] = [],
        scan: ScanProgress? = nil,
        scanError: String? = nil,
        lastImport: KotlinInstant? = nil
    ) -> SourcesUiState {
        SourcesUiState(
            thisDevice: false,
            usesAndroidProvider: false,
            folders: FolderLists(includes: [], excludes: [], extras: []),
            scan: scan,
            scanError: scanError,
            servers: SourcesViewModelKt.ServerTypes.map { ServerSource(type: $0, connected: connected.contains($0)) },
            lastImport: lastImport,
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

    @Test func noServersIsAnEmptyStateInvitingOneWithNoScan() throws {
        let sut = SourcesContent(state: SourcesState(servers: []))
        #expect((try? sut.inspect().find(text: "No Servers Yet")) != nil)
        #expect((try? sut.inspect().find(text: "Scan Now")) == nil)
    }

    @Test func mapsTheLastImport() {
        let state = SourcesState(uiState(connected: [.jellyfin], lastImport: KotlinInstant.companion.fromEpochMilliseconds(epochMilliseconds: 1_700_000_000_000)))
        #expect(state.lastImport == Date(timeIntervalSince1970: 1_700_000_000))
        #expect(SourcesState(uiState()).lastImport == nil)
    }

    @Test func aServersStatusFollowsItsOwnImportOnly() {
        let importing = SourcesState(servers: [.jellyfin, .emby], importStatus: .importing(provider: "Jellyfin", message: nil, fraction: 0.4))
        #expect(importing.status(of: .jellyfin) == .importing(fraction: 0.4))
        #expect(importing.status(of: .emby) == .connected)
        let failed = SourcesState(servers: [.emby], importStatus: .failed(provider: "Emby", error: "HTTP 500"))
        #expect(failed.status(of: .emby) == .failed("HTTP 500"))
    }

    @Test func rowsShowTheirImport() throws {
        let sut = SourcesContent(state: SourcesState(servers: [.jellyfin], importStatus: .importing(provider: "Jellyfin", message: nil, fraction: 0.5)))
        #expect((try? sut.inspect().find(text: "Importing")) != nil)
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

    @Test func sourcesRouteHasAKeyAndSurvivesAStoredPath() throws {
        #expect(Route.sources.cacheKey == "sources")
        let routes: [Route] = [.sources, .equalizer]
        let decoded = try JSONDecoder().decode([Route].self, from: JSONEncoder().encode(routes))
        #expect(decoded == routes)
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
