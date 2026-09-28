import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// Library > Genres from its UiState: the loading, importing and empty placeholders, the rows, and what a tap's
/// route resolves to.
@MainActor
struct GenreListTests {
    private func state(_ genres: [Genre], _ loading: GenreListUiState.LoadingState) -> GenreListUiState {
        GenreListUiState(
            genres: genres, loadingState: loading, scanProgress: nil, sortOrder: .`default`,
            letterIndex: LetterIndexKt.genreLetterIndex(genres: genres, sortOrder: .`default`)
        )
    }

    private func genre(_ name: String, songs: Int32) -> Genre {
        Genre(name: name, songCount: songs, duration: 0, mediaProviders: [.jellyfin])
    }

    @Test func readyListsEachGenreAsALinkToItsRoute() throws {
        let genres = [genre("Trip Hop", songs: 12), genre("Art Rock", songs: 1)]
        let sut = GenreListContent(state: state(genres, .ready))
        #expect((try? sut.inspect().find(text: "Trip Hop")) != nil)
        #expect((try? sut.inspect().find(text: "12 songs")) != nil)
        #expect((try? sut.inspect().find(text: "1 song")) != nil)
        #expect(try sut.inspect().findAll(ViewType.NavigationLink.self).count == 2)
        #expect(Route.genre(genres[0]) == .genre(name: "Trip Hop"))
    }

    @Test func anImportKeepsShowingTheGenresAlreadyImported() throws {
        let sut = GenreListContent(state: state([genre("Trip Hop", songs: 12)], .scanning))
        #expect((try? sut.inspect().find(text: "Trip Hop")) != nil)
        #expect((try? sut.inspect().find(text: "Importing your library…")) == nil)
    }

    @Test func placeholders() throws {
        #expect((try? GenreListContent(state: state([], .empty)).inspect().find(text: "No Genres")) != nil)
        #expect((try? GenreListContent(state: state([], .scanning)).inspect().find(text: "Importing your library…")) != nil)
        #expect((try? GenreListContent(state: state([], .loading)).inspect().find(LibraryListSkeleton.self)) != nil)
    }
}
