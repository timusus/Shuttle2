import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// Home from its `HomeUiState` (#633): the Jump Back In grid and its columns per tier, the shelves'
/// type labels, cold start, what a tap opens or plays (with the item's play context), and the empty/loading states.
@MainActor
struct HomeViewTests {
    private func album(_ name: String, artist: String = "Radiohead", playCount: Int32 = 0) -> Album {
        Album(
            name: name, albumArtist: artist, artists: [artist], songCount: 10, duration: 0,
            year: nil, playCount: playCount, lastSongPlayed: nil, lastSongCompleted: nil,
            groupKey: AlbumGroupKey(key: name.lowercased(), albumArtistGroupKey: AlbumArtistGroupKey(key: artist.lowercased()), identity: nil),
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

    private func content(_ sections: [HomeSection] = []) -> HomeUiState {
        HomeUiStateContent(showWhatsNew: false, sections: sections, events: [], covers: [:])
    }

    private func same(_ lhs: MediaAction?, _ rhs: MediaAction) -> Bool {
        guard let lhs else { return false }
        return (lhs as AnyObject).isEqual(rhs as AnyObject)
    }

    @Test func loadingShowsAProgressView() throws {
        #expect((try? HomeContent(state: HomeUiStateLoading.shared).inspect().find(ViewType.ProgressView.self)) != nil)
    }

    @Test func emptyShowsTheEmptyState() throws {
        #expect((try? HomeContent(state: HomeUiStateEmpty.shared).inspect().find(text: "No Music")) != nil)
        #expect((try? HomeContent(state: HomeUiStateEmpty.shared).inspect().find(ViewType.List.self)) == nil)
    }

    // MARK: Jump Back In

    @Test(arguments: [(LayoutTier.compact, 2), (.regular, 4), (.wide, 4)] as [(LayoutTier, Int)])
    func theGridIsTwoColumnsOnAPhoneAndFourWider(tier: LayoutTier, columns: Int) {
        #expect(JumpBackInGrid.columnCount(tier: tier, accessibilitySize: false) == columns)
    }

    @Test(arguments: [LayoutTier.compact, .regular, .wide])
    func accessibilityTextSizesCollapseTheGridToOneColumn(tier: LayoutTier) {
        #expect(JumpBackInGrid.columnCount(tier: tier, accessibilitySize: true) == 1)
    }

    /// ViewInspector doesn't hand `.environment` values to an inspected view's `@Environment`, so this is the default
    /// (compact) tier; `columnCount` covers the others.
    @Test func theGridRendersTwoColumnsOfAtMostEightCellsOnAPhone() throws {
        let items: [HomeItem] = (1...10).map { HomeItemAlbumItem(album: album("Album \($0)")) }
        let lazyGrid = try JumpBackInGrid(items: items, perform: { _ in }, open: { _ in }).inspect().find(ViewType.LazyVGrid.self)
        #expect(try lazyGrid.columns().count == 2)
        #expect(lazyGrid.findAll(ViewType.Button.self, where: { (try? $0.accessibilityIdentifier()) == "homeGrid.cell" }).count == 8)
    }

    @Test func gridCellsCarryATypeLabel() throws {
        let sut = HomeContent(state: content([section(.jumpBackIn, .jumpBackIn, [
            HomeItemAlbumItem(album: album("OK Computer")),
            HomeItemArtistItem(albumArtist: artist("Massive Attack")),
            HomeItemSmartPlaylistItem(smartPlaylistId: .favourites),
            HomeItemGenreItem(genre: genre("Trip Hop")),
        ])]))
        #expect((try? sut.inspect().find(text: "Jump Back In")) != nil)
        for label in ["Album", "Artist", "Playlist", "Genre"] {
            #expect((try? sut.inspect().find(text: label)) != nil, "no \(label) label")
        }
    }

    @Test func tappingAGridCellOpensItsItem() throws {
        var opened: [HomeItem] = []
        let sut = HomeContent(
            state: content([section(.jumpBackIn, .jumpBackIn, [
                HomeItemAlbumItem(album: album("OK Computer")),
                HomeItemGenreItem(genre: genre("Trip Hop")),
            ])]),
            onOpen: { opened.append($0) }
        )
        try sut.inspect().find(button: "OK Computer").tap()
        try sut.inspect().find(button: "Trip Hop").tap()
        #expect((opened.first as? HomeItemAlbumItem)?.album.name == "OK Computer")
        #expect((opened.last as? HomeItemGenreItem)?.genre.name == "Trip Hop")
        #expect(HomeView.route(opened[1]) == .genre(name: "Trip Hop"))
    }

    @Test func aGridCellsPlayButtonDispatchesItsPlayActionWithItsContext() throws {
        var actions: [MediaAction] = []
        let item = HomeItemAlbumItem(album: album("OK Computer"))
        let sut = HomeContent(state: content([section(.jumpBackIn, .jumpBackIn, [item])]), onAction: { actions.append($0) })
        try sut.inspect().find(viewWithAccessibilityLabel: "Play OK Computer").button().tap()
        #expect(actions.count == 1)
        #expect(same(actions.first, item.playAction()))
        let play = try #require(actions.first as? MediaActionPlay)
        #expect((play.context as AnyObject).isEqual(item.playContext as AnyObject))
        #expect(play.context is PlayContextAlbum)
    }

    // MARK: Shelves

    @Test func shelvesListTheirItemsUnderTheirTitles() throws {
        let sut = HomeContent(state: content([
            section(.aroundThisTime, .tonight, [HomeItemAlbumItem(album: album("Amnesiac"))]),
            section(.onRepeat, .onRepeat, [HomeItemArtistItem(albumArtist: artist("Massive Attack"))]),
            section(.genrePicks, .genrePicks, [HomeItemGenreItem(genre: genre("Trip Hop"))]),
        ]))
        #expect((try? sut.inspect().find(text: "Tonight")) != nil)
        #expect((try? sut.inspect().find(text: "Amnesiac")) != nil)
        #expect((try? sut.inspect().find(text: "Radiohead")) != nil)
        #expect((try? sut.inspect().find(text: "3 albums")) != nil)
        #expect((try? sut.inspect().find(text: "Genre Picks")) != nil)
        #expect((try? sut.inspect().find(text: "40 songs")) != nil)
        #expect((try? sut.inspect().find(text: "Rediscover")) == nil)
    }

    @Test func aMixedShelfNamesEachTilesKind() throws {
        let sut = HomeContent(state: content([section(.onRepeat, .onRepeat, [
            HomeItemAlbumItem(album: album("Mezzanine", artist: "Massive Attack")),
            HomeItemArtistItem(albumArtist: artist("Portishead", albumCount: 12)),
        ])]))
        #expect((try? sut.inspect().find(text: "Album · Massive Attack")) != nil)
        #expect((try? sut.inspect().find(text: "Artist · 12 albums")) != nil)
    }

    @Test func aSmartPlaylistTileHasItsOwnIdentifier() throws {
        let sut = HomeContent(state: content([section(.rediscover, .rediscover, [
            HomeItemAlbumItem(album: album("Mezzanine")),
            HomeItemSmartPlaylistItem(smartPlaylistId: .favourites),
        ])]))
        let buttons = try sut.inspect().findAll(ViewType.Button.self)
        let ids = buttons.compactMap { try? $0.accessibilityIdentifier() }.filter { $0.hasPrefix("homeTile.") }
        #expect(ids == ["homeTile.album", "homeTile.smartPlaylist"])
    }

    @Test func tappingAShelfTileOpensItAndAGenrePickShufflesTheGenre() throws {
        var opened: [HomeItem] = []
        var actions: [MediaAction] = []
        let genreItem = HomeItemGenreItem(genre: genre("Trip Hop"))
        let sut = HomeContent(
            state: content([
                section(.rediscover, .rediscover, [HomeItemAlbumItem(album: album("Kid A"))]),
                section(.genrePicks, .genrePicks, [genreItem]),
            ]),
            onOpen: { opened.append($0) },
            onAction: { actions.append($0) }
        )
        try sut.inspect().find(button: "Kid A").tap()
        try sut.inspect().find(button: "Trip Hop").tap()
        #expect((opened.single as? HomeItemAlbumItem)?.album.name == "Kid A")
        #expect(same(actions.single, genreItem.playAction()))
        let shuffle = try #require(actions.first as? MediaActionShuffle)
        #expect((shuffle.context as? PlayContextGenre)?.name == "Trip Hop")
    }

    @Test func onlySectionsWithADestinationHaveSeeAll() throws {
        let sut = HomeContent(state: content([
            section(.onRepeat, .onRepeat, [HomeItemAlbumItem(album: album("Kid A"))]),
            section(.recentlyAdded, .recentlyAdded, [HomeItemAlbumItem(album: album("Amnesiac"))]),
        ]))
        #expect((try? sut.inspect().find(viewWithAccessibilityLabel: "See All Recently Added")) != nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityLabel: "See All On Repeat")) == nil)
    }

    @Test func menuActionsCarryTheItemsContext() {
        let genreItem = HomeItemGenreItem(genre: genre("Trip Hop"))
        let play = genreItem.playInOrderAction() as? MediaActionPlay
        #expect((play?.context as? PlayContextGenre)?.name == "Trip Hop")
        let smart = HomeItemSmartPlaylistItem(smartPlaylistId: .favourites)
        let shuffle = smart.shuffleAction() as? MediaActionShuffle
        #expect(shuffle?.context is PlayContextSmartPlaylist)
    }

    @Test func generatedArtworkKeepsItsHuePerName() {
        #expect(GeneratedArtwork.hue(for: "Trip Hop") == GeneratedArtwork.hue(for: "trip hop"))
        #expect(GeneratedArtwork.hue(for: "Trip Hop") != GeneratedArtwork.hue(for: "Jazz"))
        #expect(GeneratedArtwork.genreSymbol("Alternative Rock") == "guitars")
        #expect(GeneratedArtwork.genreSymbol("Polka") == "music.note")
    }

    // MARK: Cold start and the rest

    @Test func coldStartOffersShuffleAllAndSaysHomeLearns() throws {
        var shuffled = false
        let sut = HomeContent(
            state: content([
                section(.recentlyAdded, .recentlyAdded, [HomeItemAlbumItem(album: album("OK Computer"))]),
                section(.genrePicks, .genrePicks, [HomeItemGenreItem(genre: genre("Trip Hop"))]),
                section(.shuffleAll, .shuffleAll, []),
            ]),
            onShuffleAll: { shuffled = true }
        )
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "home.coldStartHint")) != nil)
        #expect((try? sut.inspect().find(text: "Recently Added")) != nil)
        #expect((try? sut.inspect().find(text: "Genre Picks")) != nil)
        try sut.inspect().find(button: "Shuffle All").tap()
        #expect(shuffled)
    }

    @Test func aHomeWithHistoryHasNoColdStartHint() throws {
        let sut = HomeContent(state: content([section(.jumpBackIn, .jumpBackIn, [HomeItemAlbumItem(album: album("OK Computer"))])]))
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "home.coldStartHint")) == nil)
    }

    @Test func noSectionsStillRendersTheScrollView() throws {
        let sut = HomeContent(state: content())
        #expect((try? sut.inspect().find(ViewType.ScrollView.self)) != nil)
        #expect((try? sut.inspect().find(text: "Jump Back In")) == nil)
    }

    @Test func theToolbarShuffleTriggersTheCallback() throws {
        var shuffled = false
        let sut = HomeContent(state: content([section(.jumpBackIn, .jumpBackIn, [HomeItemAlbumItem(album: album("OK Computer"))])]), onShuffleAll: { shuffled = true })
        try sut.inspect().find(button: "Shuffle").tap()
        #expect(shuffled)
    }

    @Test func theRootOnItsViewModelsRendersThroughObserving() throws {
        // Renders through `Observing` from the shared ViewModels' current values: the host app's library.
        let sut = HomeView(navigator: Navigator())
        #expect(throws: Never.self) { try sut.inspect().find(HomeContent.self) }
    }
}

private extension Array {
    /// The only element, or nil unless there's exactly one.
    var single: Element? { count == 1 ? first : nil }
}
