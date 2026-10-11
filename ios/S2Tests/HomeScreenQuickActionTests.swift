import Testing
import UIKit
@testable import S2

/// The app icon's quick actions (#761): each shortcut item maps back to its action, and performing one drives the
/// shell's navigation or the shuffle entry point.
@MainActor
struct HomeScreenQuickActionTests {
    @Test func everyActionSurvivesTheRoundTripThroughItsShortcutItem() {
        for action in HomeScreenQuickAction.allCases {
            #expect(HomeScreenQuickAction(action.item) == action)
        }
        #expect(HomeScreenQuickAction.allCases.map(\.item.localizedTitle) == ["Shuffle All", "Search", "Recently Played"])
    }

    @Test func anUnknownShortcutItemIsIgnored() {
        #expect(HomeScreenQuickAction(UIApplicationShortcutItem(type: "other", localizedTitle: "Other")) == nil)
    }

    @Test func searchOpensTheSearchTab() async {
        let navigator = Navigator()
        await HomeScreenQuickAction.search.perform(on: navigator, shuffleLibrary: { Issue.record("shuffled") })
        #expect(navigator.selectedTab == .search)
    }

    @Test func recentlyPlayedOpensTheHistoryPlaylistOnHome() async {
        let navigator = Navigator(startTab: .library)
        await HomeScreenQuickAction.history.perform(on: navigator, shuffleLibrary: { Issue.record("shuffled") })
        #expect(navigator.selectedTab == .home)
        #expect(navigator.homePath == [.smartPlaylist(id: "history")])
    }

    @Test func aNavigatingActionClosesSettingsAndTheInspectorBeforeRouting() async {
        let navigator = Navigator()
        navigator.showsSettings = true
        navigator.showsPlayerInspector = true
        await HomeScreenQuickAction.history.perform(on: navigator, shuffleLibrary: { Issue.record("shuffled") })
        #expect(!navigator.showsSettings)
        #expect(!navigator.showsPlayerInspector)
        #expect(navigator.settingsPath.isEmpty)
        #expect(navigator.homePath == [.smartPlaylist(id: "history")])
    }

    @Test func shuffleAllShufflesTheLibraryWithoutNavigating() async {
        let navigator = Navigator(startTab: .library)
        var shuffled = 0
        await HomeScreenQuickAction.shuffleAll.perform(on: navigator, shuffleLibrary: { shuffled += 1 })
        #expect(shuffled == 1)
        #expect(navigator.selectedTab == .library)
        #expect(navigator.libraryPath.isEmpty)
    }
}
