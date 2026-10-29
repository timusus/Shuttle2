import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// The composer page from its UiState: the hero, one row per work with its catalogue and recording count, the album shelf,
/// and what tapping a work opens.
@MainActor
struct ComposerDetailTests {
    private func groupKey(_ name: String) -> AlbumGroupKey {
        AlbumGroupKey(key: name.lowercased(), albumArtistGroupKey: AlbumArtistGroupKey(key: "karajan"), identity: nil)
    }

    private func recording(_ album: String) -> OtherRecording {
        OtherRecording(groupKey: groupKey(album), album: album, albumArtist: "Karajan", songs: TestSongs.demo)
    }

    private func album(_ name: String) -> Album {
        Album(
            name: name, albumArtist: "Karajan", artists: ["Karajan"], songCount: 1, duration: 0,
            year: nil, playCount: 0, lastSongPlayed: nil, lastSongCompleted: nil,
            groupKey: groupKey(name), mediaProviders: [.jellyfin], artworkVersion: nil, dateAdded: nil, albumArtistKeys: []
        )
    }

    private func state(works: [ComposerWork], albums: [Album] = []) -> ComposerUiState {
        ComposerUiState(
            loading: false,
            composer: ComposerWorks(name: "Ludwig van Beethoven", works: works, albums: albums.compactMap(\.groupKey)),
            albums: albums
        )
    }

    private let fifth = "Symphony No. 5"

    @Test func readyShowsHeroWorksAndAlbums() throws {
        let works = [ComposerWork(title: fifth, catalogue: "Op. 67", recordings: [recording("Fifth & Seventh"), recording("Fifth (1963)")], key: "fifth")]
        let sut = ComposerDetailContent(state: state(works: works, albums: [album("Fifth & Seventh")]))
        #expect((try? sut.inspect().find(text: "Ludwig van Beethoven")) != nil)
        #expect((try? sut.inspect().find(text: "COMPOSER")) != nil)
        #expect((try? sut.inspect().find(text: fifth)) != nil)
        #expect((try? sut.inspect().find(text: "Op. 67 · 2 recordings")) != nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "detailTile.album")) != nil)
    }

    @Test func aWorkWithOneRecordingOpensItsAlbum() throws {
        var opened: AlbumGroupKey?
        let works = [ComposerWork(title: fifth, catalogue: nil, recordings: [recording("Fifth & Seventh")], key: "fifth")]
        let sut = ComposerDetailContent(state: state(works: works), onOpenAlbum: { opened = $0 })
        try sut.inspect().find(button: fifth).tap()
        #expect(opened?.key == "fifth & seventh")
    }

    @Test func aWorkWithSeveralRecordingsDoesNotOpenAnAlbumDirectly() throws {
        var opened: AlbumGroupKey?
        let works = [ComposerWork(title: fifth, catalogue: nil, recordings: [recording("A"), recording("B")], key: "fifth")]
        let sut = ComposerDetailContent(state: state(works: works), onOpenAlbum: { opened = $0 })
        try sut.inspect().find(button: fifth).tap()
        #expect(opened == nil)
    }

    @Test func theRecordingsSheetOpensTheChosenAlbum() throws {
        var opened: AlbumGroupKey?
        let work = ComposerWork(title: fifth, catalogue: nil, recordings: [recording("A"), recording("B")], key: "fifth")
        let sut = ComposerRecordingsSheet(work: work, onOpen: { opened = $0 })
        try sut.inspect().find(button: "B").tap()
        #expect(opened?.key == "b")
    }

    @Test func noComposerShowsNotFound() throws {
        let sut = ComposerDetailContent(state: ComposerUiState(loading: false, composer: nil, albums: []))
        #expect((try? sut.inspect().find(text: "Composer Not Found")) != nil)
    }
}
