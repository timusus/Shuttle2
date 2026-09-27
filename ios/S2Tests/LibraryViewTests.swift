import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// The Library tab lists the songs of the Kotlin `IosAppGraph.librarySongs` StateFlow.
@MainActor
struct LibraryViewTests {
    private let graph = IosAppGraph()

    @Test func theKotlinStateFlowReachesSwiftAsSongs() {
        let songs = graph.librarySongs.value
        #expect(songs.count == 5)
        #expect(songs.first?.name == "Paranoid Android")
        #expect(songs.first?.friendlyArtistName == "Radiohead")
    }

    @Test func songListShowsEachSongWithItsArtistAndAlbum() throws {
        let sut = SongList(songs: graph.librarySongs.value)
        #expect((try? sut.inspect().find(text: "Paranoid Android")) != nil)
        #expect((try? sut.inspect().find(text: "Radiohead · OK Computer")) != nil)
        #expect(try sut.inspect().findAll(ViewType.Text.self).count == 10)
    }

    @Test func libraryViewObservesTheFlow() throws {
        let sut = LibraryView(songs: graph.librarySongs)
        #expect((try? sut.inspect().find(text: "Teardrop")) != nil)
        #expect((try? sut.inspect().find(text: "Pyramid Song")) != nil)
    }
}
