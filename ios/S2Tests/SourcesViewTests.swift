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
        lastImport: KotlinInstant? = nil,
        thisDevice: Bool = false,
        extras: [SourceFolder] = [],
        deviceSongs: Int? = nil
    ) -> SourcesUiState {
        SourcesUiState(
            thisDevice: thisDevice,
            usesAndroidProvider: false,
            folders: FolderLists(includes: [], excludes: [], extras: extras),
            scan: scan,
            scanError: scanError,
            deviceStatus: SourceStatusIdle.shared,
            deviceSongs: deviceSongs.map { KotlinInt(int: Int32($0)) },
            deviceUpdated: nil,
            servers: SourcesViewModelKt.ServerTypes.map { ServerSource(type: $0, connected: connected.contains($0), status: SourceStatusIdle.shared, songs: nil, updated: nil) },
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

    @Test func listsConnectedServersUnderMediaServersWithHostAndStatus() throws {
        let login = ServerLogin(address: "https://music.example.com:8920/jellyfin", username: "tim")
        let sut = SourcesContent(state: SourcesState(servers: [.jellyfin], logins: [.jellyfin: login]))
        #expect((try? sut.inspect().find(text: "Media Servers")) != nil)
        #expect((try? sut.inspect().find(text: "Jellyfin")) != nil)
        #expect((try? sut.inspect().find(text: "music.example.com:8920")) != nil)
        #expect((try? sut.inspect().find(text: "Connected")) != nil)
        #expect((try? sut.inspect().find(text: "Emby")) == nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "sources.addServer").button()) != nil)
    }

    /// #645: Remove Server asks first (through `confirmingServerRemoval`, which a swipe's Remove in Sources shares;
    /// ViewInspector can't reach swipe actions, so this checks the detail's).
    @Test func removeServerAsksBeforeRemoving() throws {
        var removed = false
        let sut = ServerDetailContent(type: .emby, login: ServerLogin(), status: .connected, scan: .idle, lastImport: nil, onRemove: { removed = true })
        try sut.inspect().find(viewWithAccessibilityIdentifier: "serverDetail.remove").button().tap()
        #expect(!removed)
    }

    @Test func aServerRowPushesItsDetail() throws {
        let sut = SourcesContent(state: SourcesState(servers: [.emby]))
        let link = try sut.inspect().find(viewWithAccessibilityIdentifier: "sources.server.Emby").navigationLink()
        #expect((try? link.labelView().find(text: "Emby")) != nil)
        #expect(Route.server(type: MediaProviderType.emby.name).cacheKey == "server:Emby")
        #expect(MediaProviderType.server(named: "Emby") == .emby)
        #expect(MediaProviderType.server(named: "Nope") == nil)
    }

    @Test func aServerRowsStatusIsItsAccessibilityValue() throws {
        let sut = ServerRow(type: .jellyfin, host: "music.example.com", status: .failed("HTTP 500"))
        #expect(try sut.inspect().find(ViewType.LabeledContent.self).accessibilityValue().string() == "Import Failed")
        #expect(try sut.inspect().find(ViewType.LabeledContent.self).accessibilityLabel().string() == "Jellyfin, music.example.com")
    }

    @Test func noServersShowsAddServerAndNoScan() throws {
        let sut = SourcesContent(state: SourcesState(servers: []))
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "sources.addServer")) != nil)
        #expect((try? sut.inspect().find(text: "Connect a Jellyfin, Emby or Plex server to stream your music library from it.")) != nil)
        #expect((try? sut.inspect().find(text: "Scan Now")) == nil)
    }

    // MARK: On This iPhone

    @Test func thisDevicesFoldersAndSongsAreMapped() {
        let folder = SourceFolder(uri: "f1", path: "/Music", name: "Music", hasAccess: false)
        let state = SourcesState(uiState(thisDevice: true, extras: [folder], deviceSongs: 12))
        #expect(state.thisDevice)
        #expect(state.folders == [SourcesState.DeviceFolder(id: "f1", name: "Music", path: "/Music", hasAccess: false)])
        #expect(state.deviceSongs == 12)
        #expect(state.deviceFooter == "12 songs on this iPhone.")
    }

    @Test func thisDeviceOffShowsOnlyItsSwitchAndNoScan() throws {
        var enabled: Bool?
        let sut = SourcesContent(state: SourcesState(servers: []), onThisDeviceChange: { enabled = $0 })
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "sources.addFolder")) == nil)
        #expect((try? sut.inspect().find(text: "Scan Now")) == nil)
        try sut.inspect().find(viewWithAccessibilityIdentifier: "sources.thisDevice").toggle().tap()
        #expect(enabled == true)
    }

    @Test func thisDeviceOnListsItsFoldersAndScans() throws {
        let folders = [
            SourcesState.DeviceFolder(id: "a", name: "Music", path: "/Music"),
            SourcesState.DeviceFolder(id: "b", name: "Away", path: nil, hasAccess: false),
        ]
        let sut = SourcesContent(state: SourcesState(thisDevice: true, folders: folders, deviceSongs: 1, servers: []))
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "sources.documents")) != nil)
        #expect((try? sut.inspect().find(text: "Music")) != nil)
        #expect((try? sut.inspect().find(text: "Can't Be Read. Tap to Choose It Again.")) != nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "sources.addFolder")) != nil)
        #expect((try? sut.inspect().find(text: "1 song on this iPhone.")) != nil)
        #expect((try? sut.inspect().find(text: "Scan Now")) != nil)
        #expect((try? sut.inspect().find(text: "Looks for new and changed music on this iPhone.")) != nil)
    }

    @Test func thisDeviceWithoutSongsSaysHowToAddThem() {
        #expect(SourcesState(thisDevice: true, deviceSongs: 0, servers: []).deviceFooter.hasPrefix("Copy music into Shuttle Music"))
    }

    @Test func scanIsItsOwnSectionWithTheLastUpdate() throws {
        let sut = SourcesContent(state: SourcesState(servers: [.jellyfin], lastImport: Date().addingTimeInterval(-240)))
        #expect((try? sut.inspect().find(text: "Scan")) != nil)
        #expect((try? sut.inspect().find(text: "Scan Now")) != nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "sources.lastImport")) != nil)
        #expect((try? sut.inspect().find(text: "Last Updated")) != nil)
    }

    @Test func rowsShowTheirImport() throws {
        let sut = SourcesContent(state: SourcesState(servers: [.jellyfin], importStatus: .importing(provider: "Jellyfin", message: nil, fraction: 0.5)))
        #expect((try? sut.inspect().find(text: "Importing 50%")) != nil)
    }

    @Test func showsAFailedScanAndRescans() throws {
        var rescanned = false
        let sut = SourcesContent(state: SourcesState(servers: [.emby], scan: .failed("HTTP 500")), onRescan: { rescanned = true })
        #expect((try? sut.inspect().find(text: "Last Scan Failed")) != nil)
        #expect((try? sut.inspect().find(text: "HTTP 500")) != nil)
        try sut.inspect().find(viewWithAccessibilityIdentifier: "sources.rescan").button().tap()
        #expect(rescanned)
    }

    @Test func scanNowIsDisabledWhileScanning() throws {
        let sut = SourcesContent(state: SourcesState(servers: [.emby], scan: .scanning(message: "Reading songs", fraction: 0.3)))
        #expect(try sut.inspect().find(viewWithAccessibilityIdentifier: "sources.rescan").button().isDisabled())
        #expect((try? sut.inspect().find(text: "Reading songs")) != nil)
    }

    @Test func aLoginsHostDropsTheSchemeAndPath() {
        #expect(ServerLogin(address: "https://music.example.com:8920/jellyfin").host == "music.example.com:8920")
        #expect(ServerLogin(address: "http://192.168.1.5").host == "192.168.1.5")
        #expect(ServerLogin(address: nil).host == nil)
    }

    // MARK: Server detail

    @Test func theDetailShowsWhereTheServerIsAndWhoIsSignedIn() throws {
        let login = ServerLogin(address: "https://music.example.com", username: "tim")
        let sut = ServerDetailContent(type: .jellyfin, login: login, status: .connected, scan: .idle, lastImport: nil)
        #expect((try? sut.inspect().find(text: "https://music.example.com")) != nil)
        #expect((try? sut.inspect().find(text: "tim")) != nil)
        #expect((try? sut.inspect().find(text: "Connected")) != nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "serverDetail.remove").button()) != nil)
    }

    @Test func theDetailSignsInAgainAndRescans() throws {
        var signedIn = false
        var rescanned = false
        let sut = ServerDetailContent(
            type: .emby,
            login: ServerLogin(),
            status: .failed("HTTP 401"),
            scan: .idle,
            lastImport: nil,
            onRescan: { rescanned = true },
            onSignIn: { signedIn = true }
        )
        #expect((try? sut.inspect().find(text: "HTTP 401")) != nil)
        try sut.inspect().find(viewWithAccessibilityIdentifier: "serverDetail.signIn").button().tap()
        try sut.inspect().find(viewWithAccessibilityIdentifier: "sources.rescan").button().tap()
        #expect(signedIn)
        #expect(rescanned)
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

    @Test func connectAServerAsksForThePicker() throws {
        var asked = false
        let sut = SourcesContent(state: SourcesState(servers: []), onAddServer: { asked = true })
        try sut.inspect().find(viewWithAccessibilityIdentifier: "sources.addServer").button().tap()
        #expect(asked)
    }

    @Test func sourcesRouteHasAKeyAndSurvivesAStoredPath() throws {
        #expect(Route.sources.cacheKey == "sources")
        let routes: [Route] = [.sources, .server(type: "Jellyfin"), .equalizer]
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

    /// #645: the import activity's Open Sources opened the Settings sheet at Sources while its popover was still
    /// dismissing, which UIKit refuses, so nothing happened. It pushes Sources onto the stack it sits on instead.
    @Test func theImportActivitysOpenSourcesPushesSourcesOntoTheCurrentStack() {
        let navigator = Navigator(viewModelCache: ViewModelCache(), startTab: .library)
        RootToolbar.openSources(navigator)
        #expect(navigator.libraryPath == [.sources])
        #expect(!navigator.showsSettings)

        navigator.selectTab(.home)
        RootToolbar.openSources(navigator)
        #expect(navigator.homePath == [.sources])

        // Regular and wide: a library category's own stack.
        navigator.selectLibraryCategory(.albums)
        RootToolbar.openSources(navigator)
        #expect(navigator.path(for: LibraryCategory.albums) == [.sources])
    }

}
