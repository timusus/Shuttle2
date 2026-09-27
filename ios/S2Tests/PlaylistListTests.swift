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
        #expect((try? PlaylistListContent(state: state([], .loading)).inspect().find(ViewType.ProgressView.self)) != nil)
    }

    @Test func createRenameAndDeleteClosuresAreWiredToTheirPlaylist() {
        let target = playlist(1, "Road Trip", songs: 12)
        var created: String?
        var renamed: (Playlist, String)?
        var deleted: Playlist?
        let sut = PlaylistListContent(
            state: state([target], .ready),
            onCreate: { created = $0 },
            onRename: { renamed = ($0, $1) },
            onDelete: { deleted = $0 }
        )
        sut.onCreate("Weekend Mix")
        sut.onRename(target, "New Name")
        sut.onDelete(target)
        #expect(created == "Weekend Mix")
        #expect(renamed?.0.id == target.id)
        #expect(renamed?.1 == "New Name")
        #expect(deleted?.id == target.id)
    }
}
