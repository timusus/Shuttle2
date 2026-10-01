import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// Library > Artists from its UiState: the loading, importing and empty placeholders, the rows, and what a
/// tap's route resolves to.
@MainActor
struct AlbumArtistListTests {
    private func state(_ albumArtists: [AlbumArtist], _ loading: AlbumArtistListUiState.LoadingState) -> AlbumArtistListUiState {
        AlbumArtistListUiState(
            albumArtists: albumArtists, selectedArtists: [], viewMode: .list, sortOrder: .`default`, loadingState: loading, scanProgress: nil,
            letterIndex: LetterIndexKt.albumArtistLetterIndex(albumArtists: albumArtists, sortOrder: .`default`)
        )
    }

    private func artist(_ name: String, albums: Int32, songs: Int32) -> AlbumArtist {
        AlbumArtist(
            name: name, artists: [name], albumCount: albums, songCount: songs, playCount: 0,
            groupKey: AlbumArtistGroupKey(key: name.lowercased()), mediaProviders: [.jellyfin], artworkVersion: nil, appearsOnCount: 0
        )
    }

    @Test func readyListsEachArtistAsALinkToItsRoute() throws {
        let artists = [artist("Radiohead", albums: 3, songs: 42), artist("Björk", albums: 1, songs: 1)]
        let sut = AlbumArtistListContent(state: state(artists, .ready))
        #expect((try? sut.inspect().find(text: "Radiohead")) != nil)
        #expect((try? sut.inspect().find(text: "3 albums · 42 songs")) != nil)
        #expect((try? sut.inspect().find(text: "1 album · 1 song")) != nil)
        #expect(try sut.inspect().findAll(ViewType.NavigationLink.self).count == 2)
        #expect(Route.albumArtist(artists[0]) == .albumArtist(albumArtistKey: "radiohead"))
    }

    @Test func anImportKeepsShowingTheArtistsAlreadyImported() throws {
        let sut = AlbumArtistListContent(state: state([artist("Radiohead", albums: 3, songs: 42)], .scanning))
        #expect((try? sut.inspect().find(text: "Radiohead")) != nil)
        #expect((try? sut.inspect().find(text: "Importing your library…")) == nil)
    }

    @Test func placeholders() throws {
        #expect((try? AlbumArtistListContent(state: state([], .empty)).inspect().find(text: "No Artists")) != nil)
        #expect((try? AlbumArtistListContent(state: state([], .scanning)).inspect().find(text: "Importing your library…")) != nil)
        #expect((try? AlbumArtistListContent(state: state([], .loading)).inspect().find(LibraryListSkeleton.self)) != nil)
    }
}
