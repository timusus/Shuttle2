import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// The detail frame's decisions: one column or two, when the bar takes over the hero's title, the Play/Shuffle
/// capsules (on a hero and on Home's resume card), and which tile is a zoom source.
@MainActor
struct DetailSupportTests {
    private func album(_ name: String) -> Album {
        Album(
            name: name, albumArtist: "Radiohead", artists: ["Radiohead"], songCount: 1, duration: 0,
            year: nil, playCount: 0, lastSongPlayed: nil, lastSongCompleted: nil,
            groupKey: AlbumGroupKey(key: name.lowercased(), albumArtistGroupKey: AlbumArtistGroupKey(key: "radiohead")),
            mediaProviders: [.jellyfin], artworkVersion: nil
        )
    }

    // MARK: - Columns

    @Test func compactIsOneColumnAtAnyWidth() {
        #expect(DetailColumns.resolve(tier: .compact, containerWidth: 1200) == .single)
    }

    @Test func regularBelowTheTwoColumnWidthIsOneColumn() {
        let justUnder = AdaptiveLayout.twoColumnMinWidth - 1
        #expect(DetailColumns.resolve(tier: .regular, containerWidth: justUnder) == .single)
        #expect(DetailColumns.resolve(tier: .wide, containerWidth: justUnder) == .single)
    }

    @Test func regularFromTheTwoColumnWidthPutsTheHeroInAColumn() {
        let inset = AdaptiveLayout.contentInset(.regular)
        // At the threshold a third is narrower than the cover and its margins, so the column holds the cover.
        #expect(
            DetailColumns.resolve(tier: .regular, containerWidth: AdaptiveLayout.twoColumnMinWidth)
                == .two(heroColumnWidth: ArtworkSize.heroRegular + inset * 2)
        )
        // Wider, the column is a third of the container.
        #expect(DetailColumns.resolve(tier: .wide, containerWidth: 1_500) == .two(heroColumnWidth: 500))
    }

    @Test func theColumnHeroCoverNeverOutgrowsTheRegularHero() {
        #expect(DetailHeroLayout.column(width: 500).artworkSize == ArtworkSize.heroRegular)
        #expect(DetailHeroLayout.column(width: 200).artworkSize == 200)
        #expect(DetailHeroLayout.stacked.artworkSize == ArtworkSize.hero)
    }

    // MARK: - The bar's title

    @Test func theBarTakesTheTitleOnlyOnceTheHeroTitleIsUnderIt() {
        // A short hero whose title sits just below the bar: still visible, so the bar stays empty.
        #expect(DetailTitleProbe.isVisible(titleMaxY: 120, barBottom: 110))
        // Its bottom edge at or above the bar's: under the bar, so the bar names the screen.
        #expect(!DetailTitleProbe.isVisible(titleMaxY: 110, barBottom: 110))
        #expect(!DetailTitleProbe.isVisible(titleMaxY: 40, barBottom: 110))
    }

    // MARK: - Hero actions

    @Test func heroActionsPlayAndShuffle() throws {
        var played = false
        var shuffled = false
        let sut = HeroActions(onPlay: { played = true }, onShuffle: { shuffled = true })
        try sut.inspect().find(viewWithAccessibilityLabel: "Play").button().tap()
        try sut.inspect().find(button: "Shuffle").tap()
        #expect(played)
        #expect(shuffled)
    }

    @Test func theResumeCardOffersPauseWhilePlayingAndPlayWhilePaused() throws {
        func home(playing: Bool, onToggle: @escaping () -> Void = {}, onShuffleQueue: @escaping () -> Void = {}) -> HomeContent {
            let resume = ResumeQueue(song: TestSongs.demo[0], songs: TestSongs.demo, timeLeftMs: 3_665_000, playing: playing)
            let state = HomeUiStateContent(
                showWhatsNew: false, recentlyPlayed: [], recentlyAdded: [], mostPlayed: [], somethingDifferent: [],
                songs: TestSongs.demo, resume: resume, events: []
            )
            return HomeContent(state: state, onTogglePlayback: onToggle, onShuffleQueue: onShuffleQueue)
        }
        var toggled = false
        var shuffledQueue = false
        let playing = home(playing: true, onToggle: { toggled = true }, onShuffleQueue: { shuffledQueue = true })
        #expect((try? playing.inspect().find(viewWithAccessibilityLabel: "Play")) == nil)
        try playing.inspect().find(viewWithAccessibilityLabel: "Pause").button().tap()
        // The card's Shuffle, whichever order the toolbar's Shuffle All comes in.
        for button in try playing.inspect().findAll(ViewType.Button.self) where (try? button.find(text: "Shuffle")) != nil {
            try button.tap()
        }
        #expect(toggled)
        #expect(shuffledQueue)
        // Over an hour left: hours, minutes and seconds.
        #expect((try? playing.inspect().find(text: "1:01:05 left")) != nil)

        let paused = home(playing: false)
        #expect((try? paused.inspect().find(viewWithAccessibilityLabel: "Pause")) == nil)
        #expect((try? paused.inspect().find(viewWithAccessibilityLabel: "Play")) != nil)
    }

    // MARK: - Zoom sources

    @Test func onlyTheTappedTileIsTheZoomSource() {
        let id = Route.album(album("OK Computer")).cacheKey
        // Nothing tapped yet: every tile has an id of its own.
        #expect(ZoomTile.sourceID(id, tileKey: "recentlyPlayed|ok", activeKey: nil) == "tile|recentlyPlayed|ok")
        // The tapped tile is the route's source; the same album on another shelf isn't.
        #expect(ZoomTile.sourceID(id, tileKey: "recentlyPlayed|ok", activeKey: "recentlyPlayed|ok") == id)
        #expect(ZoomTile.sourceID(id, tileKey: "mostPlayed|ok", activeKey: "recentlyPlayed|ok") != id)
    }

    @Test func aDetailShelfTileIsNotASourceUntilTapped() {
        let ok = album("OK Computer")
        let id = Route.album(ok).cacheKey
        let tileKey = "detailShelf|\(ok.stableId)"
        // Home's tile for this album was tapped earlier; the shelf's, untapped, doesn't duplicate its id.
        #expect(ZoomTile.sourceID(id, tileKey: tileKey, activeKey: nil) != id)
        #expect(ZoomTile.sourceID(id, tileKey: tileKey, activeKey: tileKey) == id)
    }

    @Test func tappingADetailShelfTileOpensItsAlbum() throws {
        var opened: Album?
        let sut = List {
            DetailAlbumShelf(title: "Albums", albums: [album("OK Computer"), album("Kid A")], subtitle: { _ in nil }) {
                opened = $0
            }
        }
        #expect((try? sut.inspect().find(ViewType.NavigationLink.self)) == nil)
        try sut.inspect().find(button: "Kid A").tap()
        #expect(opened?.name == "Kid A")
    }
}
