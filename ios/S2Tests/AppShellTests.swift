import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// The shell draws the container its tier asks for.
@MainActor
struct AppShellTests {
    private func makeShell(tier: LayoutTier, container: ShellContainer? = nil) -> AppShell {
        AppShell(tier: tier, container: container, navigator: Navigator(), showNowPlaying: .constant(false))
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

    @Test func splitViewFallbackIsANavigationSplitViewListingTheTabs() throws {
        let sut = makeShell(tier: .regular, container: .splitView)
        let split = try sut.inspect().find(ViewType.NavigationSplitView.self)
        for tab in AppTab.allCases {
            #expect((try? split.find(text: tab.title)) != nil, "missing the \(tab.title) sidebar row")
        }
    }

    @Test func everyTabRootHasTheMiniPlayer() throws {
        let sut = makeShell(tier: .compact)
        let miniPlayers = try sut.inspect().findAll(MiniPlayerView.self)
        #expect(miniPlayers.count == AppTab.allCases.count)
    }
}
