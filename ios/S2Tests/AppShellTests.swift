import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// The shell draws the container its tier asks for.
@MainActor
struct AppShellTests {
    /// A player of its own, over a fake engine, so a test decides whether a song is current.
    private let engine = FakeAudioEngine()
    private let graph: IosAppGraph
    private let playerBinding: PlayerBinding

    init() {
        graph = makeTestGraph(audioPlayer: EngineAudioPlayer(engine: engine))
        playerBinding = PlayerBinding(viewModel: IosAppGraphKt.createPlayerViewModel(graph), intent: PlayIntent(following: graph.playerController))
    }

    private func makeShell(tier: LayoutTier, container: ShellContainer? = nil) -> AppShell {
        AppShell(
            tier: tier, container: container, navigator: Navigator(), showNowPlaying: .constant(false),
            playerBinding: playerBinding
        )
    }

    /// Queues the demo songs and loads the first, so a song is current.
    private func queueASong() async throws {
        let controller = graph.playerController
        _ = try await controller.queueOperations.setQueue(songs: TestSongs.demo, shuffleSongs: nil, position: 0, context: PlayContextNone.shared)
        controller.load(seekPosition: nil, skipUnloadable: false, playWhenReady: false) { _ in }
        #expect(await waitUntil { playerBinding.isMiniPlayerVisible })
    }

    @Test func compactIsATabBarOfTheThreeTabs() throws {
        let sut = makeShell(tier: .compact)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "shell.tabBar")) != nil)
        #expect((try? sut.inspect().find(ViewType.NavigationSplitView.self)) == nil)
        let tabView = try sut.inspect().find(ViewType.TabView.self)
        for tab in AppTab.allCases {
            #expect((try? tabView.find(text: tab.title)) != nil, "missing the \(tab.title) tab")
        }
    }

    @Test func regularAndWideUseTheSidebar() throws {
        let expected: String
        if #available(iOS 18, *) {
            expected = "shell.sidebarTabs"
        } else {
            expected = "shell.splitView"
        }
        for tier in [LayoutTier.regular, .wide] {
            let sut = makeShell(tier: tier)
            #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: expected)) != nil, "\(tier)")
            #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "shell.tabBar")) == nil, "\(tier)")
        }
    }

    @Test func splitViewFallbackIsANavigationSplitViewListingHomeSearchAndTheLibraryCategories() throws {
        let sut = makeShell(tier: .regular, container: .splitView)
        let split = try sut.inspect().find(ViewType.NavigationSplitView.self)
        #expect((try? split.find(text: AppTab.home.title)) != nil, "missing the Home sidebar row")
        #expect((try? split.find(text: AppTab.search.title)) != nil, "missing the Search sidebar row")
        for category in LibraryCategory.allCases {
            #expect((try? split.find(text: category.title)) != nil, "missing the \(category.title) sidebar row")
        }
    }

    @Test func everyTabRootHasTheMiniPlayerWhileASongIsCurrent() async throws {
        try await queueASong()
        let sut = makeShell(tier: .compact)
        let miniPlayers = try sut.inspect().findAll(MiniPlayerView.self)
        #expect(miniPlayers.count == AppTab.allCases.count)
    }

    @Test func noTabRootHasTheMiniPlayerWithNothingQueued() throws {
        let sut = makeShell(tier: .compact)
        #expect(!playerBinding.isMiniPlayerVisible)
        #expect(try sut.inspect().findAll(MiniPlayerView.self).isEmpty)
    }
}
