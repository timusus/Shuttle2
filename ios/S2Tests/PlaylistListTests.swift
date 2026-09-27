import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// Library > Playlists from its UiState: the loading and importing placeholders (no `Empty` case — smart
/// playlists always show), the smart and user playlist rows, what a tap's route resolves to, and create/rename/
/// delete dispatching to the shared ViewModel.
@MainActor
struct PlaylistListTests {
    private func state(
        _ playlists: [Playlist],
        smartPlaylists: [SmartPlaylist] = SmartPlaylistId.allCases.map { $0.smartPlaylist },
        _ loading: PlaylistListUiState.LoadingState
    ) -> PlaylistListUiState {
        PlaylistListUiState(
            playlists: playlists, smartPlaylists: smartPlaylists, covers: [:], sortOrder: .`default`, loadingState: loading, scanProgress: nil
        )
    }

    private func playlist(_ id: Int64, _ name: String, songs: Int32) -> Playlist {
        Playlist(id: id, name: name, songCount: songs, duration: 0, sortOrder: .position, sortDescending: false, mediaProvider: .shuttle, externalId: nil)
    }

    @Test func anImportKeepsShowingThePlaylistsAlreadyImported() throws {
        let sut = PlaylistListContent(state: state([playlist(1, "Road Trip", songs: 12)], .scanning))
        #expect((try? sut.inspect().find(text: "Road Trip")) != nil)
        #expect((try? sut.inspect().find(text: "Importing your library…")) == nil)
    }

    @Test func readyListsSmartAndUserPlaylistsAsLinksToTheirRoutes() throws {
        let playlists = [playlist(1, "Road Trip", songs: 12), playlist(2, "Chill", songs: 1)]
        let sut = PlaylistListContent(state: state(playlists, .ready))
        #expect((try? sut.inspect().find(text: "Favourites")) != nil)
        #expect((try? sut.inspect().find(text: "Recently Added")) != nil)
        #expect((try? sut.inspect().find(text: "Road Trip")) != nil)
        #expect((try? sut.inspect().find(text: "12 songs")) != nil)
        #expect((try? sut.inspect().find(text: "1 song")) != nil)
        #expect(try sut.inspect().findAll(ViewType.NavigationLink.self).count == playlists.count + SmartPlaylistId.allCases.count)
        #expect(Route.playlist(playlists[0]) == .playlist(id: 1))
        #expect(Route.smartPlaylist(SmartPlaylistId.favourites.smartPlaylist) == .smartPlaylist(id: "favourites"))
    }

    @Test func noUserPlaylistsShowsAPromptButSmartPlaylistsStillShow() throws {
        let sut = PlaylistListContent(state: state([], .ready))
        #expect((try? sut.inspect().find(text: "No playlists yet. Tap + to create one.")) != nil)
        #expect((try? sut.inspect().find(text: "Favourites")) != nil)
    }

    @Test func placeholders() throws {
        #expect((try? PlaylistListContent(state: state([], .scanning)).inspect().find(text: "Importing your library…")) != nil)
        #expect((try? PlaylistListContent(state: state([], .loading)).inspect().find(LibraryListSkeleton.self)) != nil)
    }

    /// Create/Rename/Delete are each wired from a closure opened by a toolbar button, a row's contextMenu
    /// or its swipeActions — but ViewInspector 0.10.3 (pinned per `.claude/rules/ios.md`; a later release
    /// breaks package resolution) can't drive any of them end-to-end here. `.contextMenu`/`.swipeActions`
    /// aren't supported at all (no `ContextMenu.swift`/`SwipeActions.swift` in its sources, unlike
    /// `Alert.swift`/`ConfirmationDialog.swift`). The toolbar's New Playlist button and its `.alert` *are*
    /// individually inspectable, but plain (unhosted) inspection re-evaluates `body` from scratch on every
    /// `.find()` — even reused against the same `InspectableView` — so the `@State` a button's `.tap()`
    /// sets (`isCreating`) is never visible to a later lookup. Making that visible needs `ViewHosting` plus
    /// a production-only `Inspection` publisher hook on `PlaylistListReadyView`, a testability seam not
    /// worth adding for one dialog. So none of Create/Rename/Delete can be driven through a real multi-step
    /// UI test; the empty-name disable they share is covered instead by `trimmedPlaylistNameStripsWhitespace`
    /// below, a pure helper test.
    @Test func trimmedPlaylistNameStripsWhitespace() {
        #expect(trimmedPlaylistName("  Weekend Mix  ") == "Weekend Mix")
        #expect(trimmedPlaylistName("   ").isEmpty)
        #expect(trimmedPlaylistName("").isEmpty)
    }
}
