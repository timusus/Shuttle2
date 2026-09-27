import Testing
@testable import S2

/// `Navigator`'s rules, ported from Android's `AppNavigator`: re-selecting the current root pops it to
/// root, `open(_:)` pushes onto whichever root is selected, and every path change retains only the view
/// models still reachable from some path.
@MainActor
struct NavigatorTests {
    @Test func startsOnTheLibraryTabWithEmptyPaths() {
        let navigator = Navigator()
        #expect(navigator.selection == .tab(.library))
        #expect(navigator.selectedTab == .library)
        #expect(navigator.homePath.isEmpty)
        #expect(navigator.libraryPath.isEmpty)
        #expect(navigator.searchPath.isEmpty)
    }

    @Test func selectingADifferentTabSwitchesWithoutClearingItsPath() {
        let navigator = Navigator()
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
        let navigator = Navigator(viewModelCache: cache)
        navigator.selectTab(.library)
        navigator.open(.genre(name: "Rock"))
        let vm = cache.viewModel(Route.genre(name: "Rock").cacheKey) { FakeViewModel() }
        #expect(vm.clearCount == 0)

        navigator.selectTab(.library) // reselect: pops to root
        #expect(vm.clearCount == 1)
    }

    @Test func switchingTabsNeverClearsAnotherTabsViewModels() {
        let cache = ViewModelCache()
        let navigator = Navigator(viewModelCache: cache)
        navigator.open(.genre(name: "Rock")) // starting tab is .library
        let vm = cache.viewModel(Route.genre(name: "Rock").cacheKey) { FakeViewModel() }

        navigator.selectTab(.home)
        navigator.open(.playlist(id: 1))
        _ = cache.viewModel(Route.playlist(id: 1).cacheKey) { FakeViewModel() }

        #expect(vm.clearCount == 0)
    }

    @Test func changingALibraryCategorysPathRetainsTheOthers() {
        let cache = ViewModelCache()
        let navigator = Navigator(viewModelCache: cache)
        navigator.selectLibraryCategory(.songs)
        navigator.open(.libraryCategory(.songs))
        let songsVM = cache.viewModel(Route.libraryCategory(.songs).cacheKey) { FakeViewModel() }

        navigator.selectLibraryCategory(.albums)
        navigator.open(.album(albumKey: "a1", albumArtistKey: nil))
        _ = cache.viewModel(Route.album(albumKey: "a1", albumArtistKey: nil).cacheKey) { FakeViewModel() }

        #expect(songsVM.clearCount == 0, "the songs category's own path is untouched by selecting albums")
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
}
