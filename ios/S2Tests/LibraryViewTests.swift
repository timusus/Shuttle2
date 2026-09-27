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

    @Test func emptyShowsTheEmptyStateWithItsNote() throws {
        let sut = LibraryRootContent(
            categories: LibraryCategory.allCases, availability: .empty, importStatus: .idle,
            emptyNote: "Jellyfin sign-in failed: nope"
        )
        #expect((try? sut.inspect().find(text: "No Music")) != nil)
        #expect((try? sut.inspect().find(text: "Jellyfin sign-in failed: nope")) != nil)
        #expect((try? sut.inspect().find(text: LibraryCategory.songs.title)) == nil)
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
        #expect(try sut.inspect().find(LibraryRootContent.self) != nil)
    }
}
