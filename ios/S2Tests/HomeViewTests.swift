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

    private func content(
        recentlyPlayed: [Album] = [], recentlyAdded: [Album] = [], mostPlayed: [Album] = [],
        somethingDifferent: [AlbumArtist] = [], resume: ResumeQueue? = nil
    ) -> HomeUiState {
        HomeUiStateContent(
            showWhatsNew: false, recentlyPlayed: recentlyPlayed, recentlyAdded: recentlyAdded,
            mostPlayed: mostPlayed, somethingDifferent: somethingDifferent, songs: TestSongs.demo,
            resume: resume, events: []
        )
    }

    @Test func loadingShowsAProgressView() throws {
        #expect((try? HomeContent(state: HomeUiStateLoading.shared).inspect().find(ViewType.ProgressView.self)) != nil)
    }

    @Test func emptyShowsTheEmptyState() throws {
        #expect((try? HomeContent(state: HomeUiStateEmpty.shared).inspect().find(text: "No Music")) != nil)
        #expect((try? HomeContent(state: HomeUiStateEmpty.shared).inspect().find(ViewType.List.self)) == nil)
    }

    @Test func shelvesListTheirAlbumsAndHideWhenEmpty() throws {
        let sut = HomeContent(state: content(
            recentlyPlayed: [album("OK Computer")],
            recentlyAdded: [album("Amnesiac")],
            mostPlayed: [album("Post", artist: "Björk", playCount: 5)]
        ))
        #expect((try? sut.inspect().find(text: "Recently Played")) != nil)
        #expect((try? sut.inspect().find(text: "OK Computer")) != nil)
        #expect((try? sut.inspect().find(text: "Recently Added")) != nil)
        #expect((try? sut.inspect().find(text: "Amnesiac")) != nil)
        #expect((try? sut.inspect().find(text: "Most Played")) != nil)
        #expect((try? sut.inspect().find(text: "5 plays · Björk")) != nil)
        #expect((try? sut.inspect().find(text: "Something Different")) == nil)
    }

    @Test func somethingDifferentListsArtists() throws {
        let sut = HomeContent(state: content(somethingDifferent: [artist("Massive Attack")]))
        #expect((try? sut.inspect().find(text: "Something Different")) != nil)
        #expect((try? sut.inspect().find(text: "Massive Attack")) != nil)
        #expect((try? sut.inspect().find(text: "3 albums")) != nil)
    }

    @Test func tappingAnAlbumOpensIt() throws {
        var tapped: Album?
        let sut = HomeContent(state: content(recentlyPlayed: [album("OK Computer")]), onAlbumTap: { tapped = $0 })
        try sut.inspect().find(button: "OK Computer").tap()
        #expect(tapped?.name == "OK Computer")
    }

    @Test func tappingAnArtistOpensIt() throws {
        var tapped: AlbumArtist?
        let sut = HomeContent(state: content(somethingDifferent: [artist("Massive Attack")]), onArtistTap: { tapped = $0 })
        try sut.inspect().find(button: "Massive Attack").tap()
        #expect(tapped?.name == "Massive Attack")
    }

    @Test func noShelvesOrResumeStillRendersTheList() throws {
        let sut = HomeContent(state: content())
        #expect((try? sut.inspect().find(ViewType.List.self)) != nil)
        #expect((try? sut.inspect().find(text: "Recently Played")) == nil)
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
        let sut = HomeContent(state: content(recentlyPlayed: [album("OK Computer")]), onShuffleAll: { shuffled = true })
        try sut.inspect().find(button: "Shuffle").tap()
        #expect(shuffled)
    }

    @Test func theRootOnItsViewModelsRendersThroughObserving() throws {
        // Renders through `Observing` from the shared ViewModels' current values: the host app's library.
        let sut = HomeView(navigator: Navigator())
        #expect(try sut.inspect().find(HomeContent.self) != nil)
    }
}
