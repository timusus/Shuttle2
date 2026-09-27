import Shared
import Testing
@testable import S2

/// `Navigator`'s rules, ported from Android's `AppNavigator`: re-selecting the current root pops it to
/// root, `open(_:)` pushes onto whichever root is selected, and every path change retains only the view
/// models still reachable from some path.
@MainActor
struct NavigatorTests {
    @Test func startsOnHomeWithEmptyPaths() {
        let navigator = Navigator()
        #expect(navigator.selection == .tab(.home))
        #expect(navigator.selectedTab == .home)
        #expect(navigator.homePath.isEmpty)
        #expect(navigator.libraryPath.isEmpty)
        #expect(navigator.searchPath.isEmpty)
    }

    /// Show Home on launch off: `ShellViewModel`'s start tab is Library (#617).
    @Test func startsOnTheTabItIsGiven() {
        let navigator = Navigator(startTab: .library)
        #expect(navigator.selection == .tab(.library))
        #expect(AppTab(ShellTab.library) == .library)
        #expect(AppTab(ShellTab.home) == .home)
        #expect(AppTab(ShellTab.search) == .search)
    }

    @Test func restoringThePathsKeepsTheStartTab() {
        let navigator = Navigator()
        navigator.restore(
            home: .init(), library: .init([.libraryCategory(.albumArtists)]), search: .init(), categories: .init()
        )
        #expect(navigator.selection == .tab(.home), "a relaunch opens on the start tab, not the tab a restored path is on")
        #expect(navigator.libraryPath == [.libraryCategory(.albumArtists)])
    }

    @Test func selectingADifferentTabSwitchesWithoutClearingItsPath() {
        let navigator = Navigator(startTab: .library)
        navigator.open(.genre(name: "Rock")) // pushed onto the starting tab, .library
        navigator.selectTab(.home)
        #expect(navigator.selection == .tab(.home))
        #expect(navigator.libraryPath == [.genre(name: "Rock")], "switching tabs must not clear the other tab's path")
    }

    @Test func reselectingTheCurrentTabPopsItToRoot() {
        let navigator = Navigator()
        navigator.selectTab(.home)
        navigator.open(.genre(name: "Rock"))
        #expect(!navigator.homePath.isEmpty)
        navigator.selectTab(.home)
        #expect(navigator.homePath.isEmpty)
        #expect(navigator.selection == .tab(.home))
    }

    @Test func openAppendsToTheSelectedTabsPath() {
        let navigator = Navigator()
        navigator.selectTab(.search)
        navigator.open(.albumArtist(albumArtistKey: "a1"))
        navigator.open(.genre(name: "Rock"))
        #expect(navigator.searchPath == [.albumArtist(albumArtistKey: "a1"), .genre(name: "Rock")])
        #expect(navigator.homePath.isEmpty)
    }

    @Test func selectingALibraryCategorySwitchesTheRootSelection() {
        let navigator = Navigator()
        navigator.selectLibraryCategory(.albums)
        #expect(navigator.selection == .libraryCategory(.albums))
        #expect(navigator.selectedTab == .library, "a promoted category is still the Library tab's content")
    }

    @Test func reselectingTheCurrentLibraryCategoryPopsItToRoot() {
        let navigator = Navigator()
        navigator.selectLibraryCategory(.albums)
        navigator.open(.album(albumKey: "a1", albumArtistKey: nil))
        #expect(!navigator.path(for: .albums).isEmpty)
        navigator.selectLibraryCategory(.albums)
        #expect(navigator.path(for: .albums).isEmpty)
    }

    @Test func openWhileALibraryCategoryIsSelectedAppendsToThatCategorysPath() {
        let navigator = Navigator()
        navigator.selectLibraryCategory(.genres)
        navigator.open(.genre(name: "Rock"))
        #expect(navigator.path(for: .genres) == [.genre(name: "Rock")])
        #expect(navigator.libraryPath.isEmpty, "compact's own Library path is untouched by a category selection")
    }

    @Test func pathsForDifferentCategoriesAreIndependent() {
        let navigator = Navigator()
        navigator.selectLibraryCategory(.songs)
        navigator.open(.libraryCategory(.songs))
        navigator.selectLibraryCategory(.albums)
        navigator.open(.album(albumKey: "a1", albumArtistKey: nil))
        #expect(navigator.path(for: .songs) == [.libraryCategory(.songs)])
        #expect(navigator.path(for: .albums) == [.album(albumKey: "a1", albumArtistKey: nil)])
    }

    // MARK: - retainOnly wiring

    private final class FakeViewModel: ClearableViewModel {
        private(set) var clearCount = 0
        func clear() { clearCount += 1 }
    }

    @Test func poppingAPathClearsTheViewModelsOfTheRoutesThatLeftIt() {
        let cache = ViewModelCache()
        let navigator = Navigator(viewModelCache: cache, startTab: .library)
        navigator.selectTab(.library)
        navigator.open(.genre(name: "Rock"))
        let vm = cache.viewModel(Route.genre(name: "Rock").cacheKey) { FakeViewModel() }
        #expect(vm.clearCount == 0)

        navigator.selectTab(.library) // reselect: pops to root
        #expect(vm.clearCount == 1)
    }

    @Test func switchingTabsNeverClearsAnotherTabsViewModels() {
        let cache = ViewModelCache()
        let navigator = Navigator(viewModelCache: cache, startTab: .library)
        navigator.open(.genre(name: "Rock")) // starting tab is .library
        let vm = cache.viewModel(Route.genre(name: "Rock").cacheKey) { FakeViewModel() }

        navigator.selectTab(.home)
        navigator.open(.playlist(id: 1))
        _ = cache.viewModel(Route.playlist(id: 1).cacheKey) { FakeViewModel() }

        #expect(vm.clearCount == 0)
    }

    @Test func changingALibraryCategorysPathRetainsTheOthers() {
        let cache = ViewModelCache()
        let navigator = Navigator(viewModelCache: cache, startTab: .library)
        navigator.selectLibraryCategory(.songs)
        navigator.open(.libraryCategory(.songs))
        let songsVM = cache.viewModel(Route.libraryCategory(.songs).cacheKey) { FakeViewModel() }

        navigator.selectLibraryCategory(.albums)
        navigator.open(.album(albumKey: "a1", albumArtistKey: nil))
        _ = cache.viewModel(Route.album(albumKey: "a1", albumArtistKey: nil).cacheKey) { FakeViewModel() }

        #expect(songsVM.clearCount == 0, "the songs category's own path is untouched by selecting albums")
    }

    @Test func rootKeysSurviveAPathChangeOnAnotherTab() {
        let cache = ViewModelCache()
        let navigator = Navigator(viewModelCache: cache, startTab: .library)
        let homeVM = cache.viewModel(AppTab.home.cacheKey) { FakeViewModel() }
        let categoryVM = cache.viewModel(Route.libraryCategory(.albums).cacheKey) { FakeViewModel() }

        navigator.selectTab(.search)
        navigator.open(.playlist(id: 1))

        #expect(homeVM.clearCount == 0, "a tab root's view model must survive a path change on another tab")
        #expect(categoryVM.clearCount == 0, "a library category root's view model must survive a path change elsewhere")
    }

    // MARK: - Tier normalization

    @Test func collapsingToCompactFoldsTheCategoryPathIntoLibrary() {
        let navigator = Navigator()
        navigator.selectLibraryCategory(.albums)
        navigator.open(.album(albumKey: "a1", albumArtistKey: nil))

        navigator.normalizeSelection(for: .compact)

        #expect(navigator.selection == .tab(.library))
        #expect(navigator.libraryPath == [.libraryCategory(.albums), .album(albumKey: "a1", albumArtistKey: nil)])
        #expect(navigator.path(for: .albums).isEmpty)
    }

    @Test func expandingToRegularUnfoldsTheLibraryPathBackIntoItsCategory() {
        let navigator = Navigator()
        navigator.selectTab(.library)
        navigator.open(.libraryCategory(.albums))
        navigator.open(.album(albumKey: "a1", albumArtistKey: nil))

        navigator.normalizeSelection(for: .regular)

        #expect(navigator.selection == .libraryCategory(.albums))
        #expect(navigator.path(for: .albums) == [.album(albumKey: "a1", albumArtistKey: nil)])
        #expect(navigator.libraryPath.isEmpty)
    }

    @Test func normalizingDoesNothingWhenNotOnALibraryCategory() {
        let navigator = Navigator()
        navigator.selectTab(.home)
        navigator.open(.genre(name: "Rock"))

        navigator.normalizeSelection(for: .compact)

        #expect(navigator.selection == .tab(.home))
        #expect(navigator.homePath == [.genre(name: "Rock")])
    }

    // MARK: - @SceneStorage restoration

    @Test func storedPathRoundTripsThroughItsRawValue() {
        let stored = Navigator.StoredPath([.genre(name: "Rock"), .playlist(id: 3)])
        let decoded = Navigator.StoredPath(rawValue: stored.rawValue)
        #expect(decoded?.routes == stored.routes)
    }

    @Test func storedCategoryPathsRoundTripThroughTheirRawValue() {
        let stored = Navigator.StoredCategoryPaths([.songs: [.libraryCategory(.songs)], .albums: []])
        let decoded = Navigator.StoredCategoryPaths(rawValue: stored.rawValue)
        #expect(decoded?.paths == stored.paths)
    }

    @Test func storedPathDecodesMalformedJSONToEmpty() {
        let decoded = Navigator.StoredPath(rawValue: "{not valid json")
        #expect(decoded?.routes == [])
    }

    @Test func storedPathDecodesAnUnknownRouteToEmpty() {
        let decoded = Navigator.StoredPath(rawValue: #"[{"notARealRoute":{}}]"#)
        #expect(decoded?.routes == [])
    }

    @Test func storedCategoryPathsDecodesMalformedJSONToEmpty() {
        let decoded = Navigator.StoredCategoryPaths(rawValue: "not json at all")
        #expect(decoded?.paths == [:])
    }

    @Test func storedCategoryPathsDecodesAnUnknownRouteToEmpty() {
        let decoded = Navigator.StoredCategoryPaths(rawValue: #"{"albums":[{"notARealRoute":{}}]}"#)
        #expect(decoded?.paths == [:])
    }

    @Test func restoreSetsEveryPathFromStorage() {
        let navigator = Navigator()
        navigator.restore(
            home: Navigator.StoredPath([.genre(name: "Jazz")]),
            library: Navigator.StoredPath([]),
            search: Navigator.StoredPath([.playlist(id: 9)]),
            categories: Navigator.StoredCategoryPaths([.albums: [.album(albumKey: "a1", albumArtistKey: nil)]])
        )
        #expect(navigator.homePath == [.genre(name: "Jazz")])
        #expect(navigator.searchPath == [.playlist(id: 9)])
        #expect(navigator.path(for: .albums) == [.album(albumKey: "a1", albumArtistKey: nil)])
    }

    // MARK: - Settings sheet

    @Test func openPushesInsideTheSettingsSheetWhileItIsUp() {
        let navigator = Navigator()
        navigator.showsSettings = true
        navigator.open(.sources)
        navigator.open(.serverSignIn(type: "jellyfin"))
        #expect(navigator.settingsPath == [.sources, .serverSignIn(type: "jellyfin")])
        #expect(navigator.libraryPath.isEmpty, "the tab under the sheet keeps its path")
    }

    @Test func popClosesASignInPushedInsideTheSettingsSheet() {
        let navigator = Navigator()
        navigator.showsSettings = true
        navigator.open(.sources)
        navigator.open(.serverSignIn(type: "jellyfin"))
        navigator.pop(.serverSignIn(type: "jellyfin"))
        #expect(navigator.settingsPath == [.sources])
    }

    @Test func closingSettingsDropsItsPathAndItsViewModels() {
        let cache = ViewModelCache()
        let navigator = Navigator(viewModelCache: cache, startTab: .library)
        navigator.showsSettings = true
        let settingsVM = cache.viewModel(Navigator.settingsCacheKey) { FakeViewModel() }
        navigator.open(.sources)
        let sourcesVM = cache.viewModel(Route.sources.cacheKey) { FakeViewModel() }
        #expect(settingsVM.clearCount == 0)

        navigator.showsSettings = false
        #expect(navigator.settingsPath.isEmpty)
        #expect(settingsVM.clearCount == 1)
        #expect(sourcesVM.clearCount == 1)

        navigator.open(.genre(name: "Rock"))
        #expect(navigator.libraryPath == [.genre(name: "Rock")], "with the sheet closed, open pushes onto the selected tab again")
    }

    // MARK: - #615: Sources reached outside the Settings sheet (Home's own "Add a Source")

    /// Regression for #615: on iPad, choosing a server type from Sources reached through Home's empty
    /// state (a plain `NavigationLink` onto `homePath`, not through Settings) must push the sign-in onto
    /// that same `homePath` — not onto whatever `libraryPath`/library category the shell happens to be
    /// showing underneath, which the fix's suspects called "a path the regular-tier shell isn't showing".
    @Test func openAfterSourcesIsReachedFromHomeStaysOnHomesPath() {
        let navigator = Navigator()
        // Home's "Add a Source" pushes with a plain SwiftUI NavigationLink, straight onto homePath,
        // never through Navigator.open — mirrored here by setting the path directly.
        navigator.selectTab(.home)
        navigator.homePath = [.sources]

        navigator.open(.serverSignIn(type: "jellyfin"))

        #expect(navigator.homePath == [.sources, .serverSignIn(type: "jellyfin")])
        #expect(navigator.libraryPath.isEmpty)
        #expect(navigator.settingsPath.isEmpty)
    }

    /// Same as above, but with a library category promoted into the sidebar (regular/wide): Sources is
    /// never pushed onto a category's own path directly, so `open` while a category is selected must not
    /// strand the sign-in there instead of wherever Sources actually is (Settings, in practice).
    @Test func openWhileALibraryCategoryIsSelectedNeverStrandsASettingsSheetPushOnTheCategory() {
        let navigator = Navigator()
        navigator.selectLibraryCategory(.songs)
        navigator.showsSettings = true

        navigator.open(.sources)
        navigator.open(.serverSignIn(type: "jellyfin"))

        #expect(navigator.settingsPath == [.sources, .serverSignIn(type: "jellyfin")])
        #expect(navigator.path(for: .songs).isEmpty, "the visible category root must be untouched by the sheet's own push")
    }
}
