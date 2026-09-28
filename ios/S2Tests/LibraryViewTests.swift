import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// The Library tab's root: the enabled categories while there's music, the empty state otherwise, the import's
/// progress under either.
@MainActor
struct LibraryViewTests {
    @Test func listsTheGivenCategoriesWhenThereIsMusic() throws {
        let sut = LibraryRootContent(categories: [.albums, .songs], availability: .hasMusic, importStatus: .idle)
        #expect((try? sut.inspect().find(text: LibraryCategory.albums.title)) != nil)
        #expect((try? sut.inspect().find(text: LibraryCategory.songs.title)) != nil)
        #expect((try? sut.inspect().find(text: LibraryCategory.genres.title)) == nil)
        #expect((try? sut.inspect().find(text: "No Music")) == nil)
    }

    @Test func emptyShowsTheEmptyState() throws {
        let sut = LibraryRootContent(categories: LibraryCategory.allCases, availability: .empty, importStatus: .idle)
        #expect((try? sut.inspect().find(text: "No Music")) != nil)
        #expect((try? sut.inspect().find(text: "Connect a Jellyfin or Emby server to stream your music.")) != nil)
        #expect((try? sut.inspect().find(text: LibraryCategory.songs.title)) == nil)
    }

    @Test func theEmptyStatesOneActionOpensSources() throws {
        let sut = LibraryRootContent(categories: [], availability: .empty, importStatus: .idle)
        let link = try sut.inspect().find(viewWithAccessibilityIdentifier: "libraryEmpty.addSource")
        #expect((try? link.find(text: "Add a Source")) != nil)
        // The toolbar's Sources link aside, the empty state's is the only other link.
        #expect(try sut.inspect().findAll(ViewType.NavigationLink.self).count >= 1)
    }

    @Test func anImportInProgressShowsItsRowInsteadOfTheEmptyState() throws {
        let sut = LibraryRootContent(
            categories: [], availability: .empty,
            importStatus: .importing(provider: "Jellyfin", message: "Reading songs", fraction: 0.5)
        )
        #expect((try? sut.inspect().find(text: "Importing from Jellyfin…")) != nil)
        #expect((try? sut.inspect().find(text: "Reading songs")) != nil)
        #expect((try? sut.inspect().find(text: "No Music")) == nil)
    }

    @Test func aFailedImportShowsItsError() throws {
        let sut = LibraryRootContent(
            categories: [.songs], availability: .hasMusic, importStatus: .failed(provider: "Emby", error: "timed out")
        )
        #expect((try? sut.inspect().find(text: "Emby import failed: timed out")) != nil)
    }

    @Test func importStatusFollowsTheSongImportState() {
        #expect(ImportStatus(SongImportState.Idle.shared) == .idle)
        #expect(
            ImportStatus(SongImportState.ImportProgress(providerType: .jellyfin, message: "Songs", progress: Shared.Progress(progress: 1, total: 4)))
                == .importing(provider: MediaProviderType.jellyfin.name, message: "Songs", fraction: 0.25)
        )
        #expect(
            ImportStatus(SongImportState.ImportProgress(providerType: .emby, message: nil, progress: nil))
                == .importing(provider: MediaProviderType.emby.name, message: nil, fraction: nil)
        )
        #expect(ImportStatus(SongImportState.ImportComplete(providerType: .emby, error: nil)) == .idle)
        #expect(
            ImportStatus(SongImportState.ImportComplete(providerType: .emby, error: "401"))
                == .failed(provider: MediaProviderType.emby.name, error: "401")
        )
    }

    @Test func availabilityMapsLoadingAndHasMusic() {
        #expect(LibraryRootAvailability(LibraryAvailabilityLoading.shared) == .loading)
        #expect(LibraryRootAvailability(LibraryAvailabilityHasMusic.shared) == .hasMusic)
    }

    @Test func libraryTabsMapToCategoriesWithoutFolders() {
        let tabs: [CoreLibraryTab] = [.genres, .playlists, .artists, .albums, .songs, .folders]
        #expect(tabs.compactMap(LibraryCategory.init) == [.genres, .playlists, .albumArtists, .albums, .songs])
    }

    @Test func theRootOnItsViewModelsListsTheCategoriesOrTheEmptyState() throws {
        // Renders through `Observing` from the shared ViewModels' current values: the host app's library.
        let sut = LibraryView(navigator: Navigator())
        #expect(try sut.inspect().find(LibraryRootContent<RouteDestinationView>.self) != nil)
    }

    @Test func eachCategoryHasACardAndAChip() throws {
        let sut = LibraryRootContent(categories: [.albums, .albumArtists], availability: .hasMusic, importStatus: .idle)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "libraryCategory.albums")) != nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "libraryCategory.albumArtists")) != nil)
        #expect((try? sut.categoryRail.inspect().find(viewWithAccessibilityIdentifier: "libraryChip.albums")) != nil)
        // The chip's short title fits the rail.
        #expect((try? sut.categoryRail.inspect().find(text: "Artists")) != nil)
        #expect((try? sut.categoryRail.inspect().find(viewWithAccessibilityIdentifier: "libraryChip.all")) == nil)
    }

    @Test func aCardOrChipShowsItsCategoryInPlace() throws {
        var selection: LibraryCategory?
        let binding = Binding(get: { selection }, set: { selection = $0 })
        let sut = LibraryRootContent(categories: [.albums, .songs], availability: .hasMusic, importStatus: .idle, selection: binding) { category in
            Text("Showing \(category.title)")
        }
        try sut.inspect().find(viewWithAccessibilityIdentifier: "libraryCategory.songs").button().tap()
        #expect(selection == .songs)
        try sut.categoryRail.inspect().find(viewWithAccessibilityIdentifier: "libraryChip.albums").button().tap()
        #expect(selection == .albums)
    }

    @Test func theChosenCategoryReplacesTheCardsAndItsChipClearsIt() throws {
        var selection: LibraryCategory? = .songs
        let binding = Binding(get: { selection }, set: { selection = $0 })
        let sut = LibraryRootContent(categories: [.albums, .songs], availability: .hasMusic, importStatus: .idle, selection: binding) { category in
            Text("Showing \(category.title)")
        }
        #expect((try? sut.inspect().find(text: "Showing Songs")) != nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "libraryCategory.albums")) == nil)
        try sut.categoryRail.inspect().find(viewWithAccessibilityIdentifier: "libraryChip.songs").button().tap()
        #expect(selection == nil)
        selection = .albums
        try sut.categoryRail.inspect().find(viewWithAccessibilityIdentifier: "libraryChip.all").button().tap()
        #expect(selection == nil)
    }

    @Test func aChosenCategoryTheUserNoLongerHasShowsTheCards() throws {
        let sut = LibraryRootContent(categories: [.albums], availability: .hasMusic, importStatus: .idle, selection: .constant(.genres)) { category in
            Text("Showing \(category.title)")
        }
        #expect((try? sut.inspect().find(text: "Showing Genres")) == nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "libraryCategory.albums")) != nil)
    }
}
