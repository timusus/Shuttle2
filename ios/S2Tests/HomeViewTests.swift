import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// Home from its `HomeUiState`: the resume hero, the shelves, a tap's route, and the empty/loading states.
@MainActor
struct HomeViewTests {
    private func album(_ name: String, artist: String = "Radiohead", playCount: Int32 = 0) -> Album {
        Album(
            name: name, albumArtist: artist, artists: [artist], songCount: 10, duration: 0,
            year: nil, playCount: playCount, lastSongPlayed: nil, lastSongCompleted: nil,
            groupKey: AlbumGroupKey(key: name.lowercased(), albumArtistGroupKey: AlbumArtistGroupKey(key: artist.lowercased())),
            mediaProviders: [.jellyfin], artworkVersion: nil
        )
    }

    private func artist(_ name: String, albumCount: Int32 = 3) -> AlbumArtist {
        AlbumArtist(
            name: name, artists: [name], albumCount: albumCount, songCount: 30, playCount: 0,
            groupKey: AlbumArtistGroupKey(key: name.lowercased()), mediaProviders: [.jellyfin], artworkVersion: nil
        )
    }

    private func genre(_ name: String) -> Genre { Genre(name: name, songCount: 40, duration: 0, mediaProviders: [.jellyfin]) }

    private func section(_ id: HomeSectionId, _ title: HomeSectionTitle, _ items: [HomeItem]) -> HomeSection {
        HomeSection(id: id, title: title, items: items)
    }

    private func content(_ sections: [HomeSection] = [], resume: ResumeQueue? = nil) -> HomeUiState {
        HomeUiStateContent(showWhatsNew: false, sections: sections, resume: resume, events: [])
    }

    @Test func loadingShowsAProgressView() throws {
        #expect((try? HomeContent(state: HomeUiStateLoading.shared).inspect().find(ViewType.ProgressView.self)) != nil)
    }

    @Test func emptyShowsTheEmptyState() throws {
        #expect((try? HomeContent(state: HomeUiStateEmpty.shared).inspect().find(text: "No Music")) != nil)
        #expect((try? HomeContent(state: HomeUiStateEmpty.shared).inspect().find(ViewType.List.self)) == nil)
    }

    @Test func sectionsListTheirItemsUnderTheirTitles() throws {
        let sut = HomeContent(state: content([
            section(.jumpBackIn, .jumpBackIn, [HomeItemAlbumItem(album: album("OK Computer")), HomeItemArtistItem(albumArtist: artist("Massive Attack"))]),
            section(.aroundThisTime, .tonight, [HomeItemAlbumItem(album: album("Amnesiac"))]),
            section(.genrePicks, .genrePicks, [HomeItemGenreItem(genre: genre("Trip Hop"))]),
        ]))
        #expect((try? sut.inspect().find(text: "Jump Back In")) != nil)
        #expect((try? sut.inspect().find(text: "OK Computer")) != nil)
        #expect((try? sut.inspect().find(text: "Massive Attack")) != nil)
        #expect((try? sut.inspect().find(text: "3 albums")) != nil)
        #expect((try? sut.inspect().find(text: "Tonight")) != nil)
        #expect((try? sut.inspect().find(text: "Amnesiac")) != nil)
        #expect((try? sut.inspect().find(text: "Genre Picks")) != nil)
        #expect((try? sut.inspect().find(text: "Trip Hop")) != nil)
        #expect((try? sut.inspect().find(text: "On Repeat")) == nil)
    }

    @Test func coldStartOffersShuffleAll() throws {
        var shuffled = false
        let sut = HomeContent(state: content([section(.shuffleAll, .shuffleAll, [])]), onShuffleAll: { shuffled = true })
        try sut.inspect().find(button: "Shuffle All").tap()
        #expect(shuffled)
    }

    @Test func tappingATileHandsItsItemOver() throws {
        var tapped: [HomeItem] = []
        let sut = HomeContent(
            state: content([section(.jumpBackIn, .jumpBackIn, [
                HomeItemAlbumItem(album: album("OK Computer")),
                HomeItemArtistItem(albumArtist: artist("Massive Attack")),
                HomeItemGenreItem(genre: genre("Trip Hop")),
            ])]),
            onItemTap: { tapped.append($0) }
        )
        try sut.inspect().find(button: "OK Computer").tap()
        try sut.inspect().find(button: "Massive Attack").tap()
        try sut.inspect().find(button: "Trip Hop").tap()
        #expect(tapped.count == 3)
        #expect((tapped[0] as? HomeItemAlbumItem)?.album.name == "OK Computer")
        #expect((tapped[1] as? HomeItemArtistItem)?.albumArtist.name == "Massive Attack")
        #expect((tapped[2] as? HomeItemGenreItem)?.genre.name == "Trip Hop")
    }

    @Test func noSectionsOrResumeStillRendersTheScrollView() throws {
        let sut = HomeContent(state: content())
        #expect((try? sut.inspect().find(ViewType.ScrollView.self)) != nil)
        #expect((try? sut.inspect().find(text: "Jump Back In")) == nil)
    }

    @Test func resumeHeroShowsTheSongAndTogglesPlayback() throws {
        var toggled = false
        let resume = ResumeQueue(song: TestSongs.demo[0], songs: TestSongs.demo, timeLeftMs: 90_000, playing: false)
        let sut = HomeContent(state: content(resume: resume), onTogglePlayback: { toggled = true })
        #expect((try? sut.inspect().find(text: "Paranoid Android")) != nil)
        #expect((try? sut.inspect().find(text: "1:30 left")) != nil)
        // An icon button now: found by its VoiceOver label.
        try sut.inspect().find(viewWithAccessibilityLabel: "Play").button().tap()
        #expect(toggled)
    }

    @Test func shuffleAllTriggersTheCallback() throws {
        var shuffled = false
        let sut = HomeContent(state: content([section(.jumpBackIn, .jumpBackIn, [HomeItemAlbumItem(album: album("OK Computer"))])]), onShuffleAll: { shuffled = true })
        try sut.inspect().find(button: "Shuffle").tap()
        #expect(shuffled)
    }

    @Test func theRootOnItsViewModelsRendersThroughObserving() throws {
        // Renders through `Observing` from the shared ViewModels' current values: the host app's library.
        let sut = HomeView(navigator: Navigator())
        #expect(try sut.inspect().find(HomeContent.self) != nil)
    }
}
