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
            mediaProviders: [.jellyfin], artworkVersion: nil, dateAdded: nil
        )
    }

    private func artist(_ name: String, albumCount: Int32 = 3) -> AlbumArtist {
        AlbumArtist(
            name: name, artists: [name], albumCount: albumCount, songCount: 30, playCount: 0,
            groupKey: AlbumArtistGroupKey(key: name.lowercased()), mediaProviders: [.jellyfin], artworkVersion: nil, appearsOnCount: 0
        )
    }

    private func genre(_ name: String) -> Genre { Genre(name: name, songCount: 40, duration: 0, mediaProviders: [.jellyfin]) }

    private func section(
        _ id: HomeSectionId,
        _ title: HomeSectionTitle,
        _ items: [HomeItem],
        subtitle: StringKey? = nil,
        progress: [String: HomeItemProgress] = [:]
    ) -> HomeSection {
        HomeSection(id: id, title: title, subtitle: subtitle, items: items, progress: progress)
    }

    private func progress(
        song: String? = "Airbag",
        fraction: Float = 0.4,
        shuffled: Bool = false,
        finished: Bool = false,
        updatedAt: Date = Date(timeIntervalSince1970: 1_000_000)
    ) -> HomeItemProgress {
        HomeItemProgress(
            songName: song, positionMs: 30_000, fraction: fraction, shuffled: shuffled, finished: finished,
            updatedAt: KotlinInstant.companion.fromEpochMilliseconds(epochMilliseconds: Int64(updatedAt.timeIntervalSince1970 * 1000))
        )
    }

    private func content(_ sections: [HomeSection] = []) -> HomeUiState {
        HomeUiStateContent(showWhatsNew: false, sections: sections, events: [], covers: [:], refreshing: false)
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
    @Test func theMostRecentItemIsTheCardOverTwoColumnsOfAtMostSixTilesOnAPhone() throws {
        let items: [HomeItem] = (1...10).map { HomeItemAlbumItem(album: album("Album \($0)")) }
        let grid = try JumpBackInGrid(items: items, perform: { _ in }, open: { _ in }).inspect()
        #expect(try grid.find(viewWithAccessibilityIdentifier: "homeGrid.card").accessibilityLabel().string() == "Album 1, Album")
        let lazyGrid = try grid.find(ViewType.LazyVGrid.self)
        #expect(try lazyGrid.columns().count == 2)
        let tiles = lazyGrid.findAll(ViewType.Button.self, where: { (try? $0.accessibilityIdentifier()) == "homeGrid.cell" })
        #expect(try tiles.map { try $0.accessibilityLabel().string() } == (2...7).map { "Album \($0), Album" })
        #expect(grid.findAll(ViewType.Button.self, where: { (try? $0.accessibilityIdentifier()) == "homeGrid.play" }).count == 1)
    }

    @Test func tilesNameTheirKindAndWhereTheirQueueWasLeft() throws {
        let artist = HomeItemArtistItem(albumArtist: artist("Alice In Chains"))
        let playlist = HomeItemSmartPlaylistItem(smartPlaylistId: .favourites)
        let genre = HomeItemGenreItem(genre: genre("Trip Hop"))
        let sut = HomeContent(state: content([section(.jumpBackIn, .jumpBackIn, [
            HomeItemAlbumItem(album: album("OK Computer")),
            HomeItemAlbumItem(album: album("Amnesiac")),
            artist,
            playlist,
            genre,
        ], progress: [
            artist.key: progress(song: "Rooster"),
            playlist.key: progress(shuffled: true),
            genre.key: progress(finished: true),
        ])]))
        #expect((try? sut.inspect().find(text: "Jump Back In")) != nil)
        for detail in ["Album", "Artist · Rooster", "Playlist · Shuffled", "Genre · Finished"] {
            #expect((try? sut.inspect().find(text: detail)) != nil, "no \(detail)")
        }
        let labels = try sut.inspect().findAll(ViewType.Button.self, where: { (try? $0.accessibilityIdentifier()) == "homeGrid.cell" })
            .map { try $0.accessibilityLabel().string() }
        #expect(labels == [
            "Amnesiac, Album",
            "Alice In Chains, Artist, on Rooster, 40% through",
            "Favourites, Playlist, on Airbag, shuffled",
            "Trip Hop, Genre, finished",
        ])
    }

    @Test func jumpBackInHasNoSubtitle() throws {
        let sut = HomeContent(state: content([
            section(.jumpBackIn, .jumpBackIn, [HomeItemAlbumItem(album: album("OK Computer"))], subtitle: .homeJumpBackInSubtitle),
        ]))
        #expect((try? sut.inspect().find(text: "Jump Back In")) != nil)
        #expect((try? sut.inspect().find(text: "Pick up where you left off")) == nil)
    }

    @Test func theCardSaysWhenAndOnWhichSongItsQueueWasLeft() throws {
        let item = HomeItemAlbumItem(album: album("OK Computer"))
        let now = Date(timeIntervalSince1970: 1_000_000 + 26 * 3600)
        let card = JumpBackInResumeCard(
            item: item, progress: progress(), tileKey: "jumpBackIn|album", perform: { _ in }, open: { _ in }, now: now
        )
        let sut = try card.inspect()
        #expect((try? sut.find(text: "Album · Yesterday")) != nil)
        #expect((try? sut.find(text: "Airbag")) != nil)
        #expect((try? sut.find(JumpBackInProgressBar.self)) != nil)
        #expect(try sut.find(viewWithAccessibilityIdentifier: "homeGrid.card").accessibilityLabel().string()
            == "OK Computer, Album, on Airbag, 40% through, Yesterday")
        #expect(try sut.find(viewWithAccessibilityIdentifier: "homeGrid.play").accessibilityLabel().string() == "Resume OK Computer")
    }

    @Test func aShuffledCardShowsTheShuffleGlyphInPlaceOfTheBar() throws {
        let card = JumpBackInResumeCard(
            item: HomeItemAlbumItem(album: album("OK Computer")), progress: progress(shuffled: true),
            tileKey: "a", perform: { _ in }, open: { _ in }
        )
        let sut = try card.inspect()
        #expect((try? sut.find(viewWithAccessibilityIdentifier: "homeGrid.shuffled")) != nil)
        #expect((try? sut.find(JumpBackInProgressBar.self)) == nil)
        #expect((try? sut.find(text: "Airbag")) != nil)
    }

    @Test func aFinishedCardOffersToPlayAgain() throws {
        let card = JumpBackInResumeCard(
            item: HomeItemAlbumItem(album: album("OK Computer")), progress: progress(finished: true),
            tileKey: "a", perform: { _ in }, open: { _ in }
        )
        let sut = try card.inspect()
        #expect((try? sut.find(text: "Finished · Play again")) != nil)
        #expect((try? sut.find(JumpBackInProgressBar.self)) == nil)
        #expect(try sut.find(viewWithAccessibilityIdentifier: "homeGrid.play").accessibilityLabel().string() == "Play OK Computer")
    }

    @Test func aTileShowsTheBarOnlyWhileItsQueueIsUnderWayInOrder() {
        #expect(JumpBackInText.showsBar(progress()))
        #expect(!JumpBackInText.showsBar(progress(fraction: 0)))
        #expect(!JumpBackInText.showsBar(progress(shuffled: true)))
        #expect(!JumpBackInText.showsBar(progress(finished: true)))
        #expect(!JumpBackInText.showsBar(nil))
    }

    @Test func aGridCellIsAsTallAsItsArtworkSlot() throws {
        let cell = JumpBackInCell(
            item: HomeItemAlbumItem(album: album("OK Computer")), progress: progress(),
            tileKey: "jumpBackIn|album", perform: { _ in }, open: { _ in }
        )
        #expect(try cell.inspect().find(ViewType.Button.self).fixedHeight() == ArtworkSize.albumRow)
        #expect(JumpBackInCell.artworkSide(for: HomeItemAlbumItem(album: album("OK Computer"))) == ArtworkSize.albumRow)
    }

    @Test func aPlaylistsOrGenresCoversCarryItsGlyph() throws {
        let genreItem = HomeItemGenreItem(genre: genre("Trip Hop"))
        let cell = JumpBackInCell(item: genreItem, progress: nil, tileKey: "jumpBackIn|genre", perform: { _ in }, open: { _ in })
        let covered = try cell.environment(\.homeCovers, [genreItem.key: [TestSongs.demo[0]]]).inspect()
        #expect((try? covered.find(ViewType.Image.self, where: { (try? $0.actualImage().name()) == GeneratedArtwork.genreSymbol })) != nil)
        let album = JumpBackInCell(item: HomeItemAlbumItem(album: album("OK Computer")), progress: nil, tileKey: "a", perform: { _ in }, open: { _ in })
        #expect((try? album.inspect().find(ViewType.Image.self, where: { (try? $0.actualImage().name()) == GeneratedArtwork.playlistSymbol })) == nil)
    }

    @Test func aPendingPlayShowsASpinnerInPlaceOfThePlayGlyph() throws {
        let item = HomeItemAlbumItem(album: album("OK Computer"))
        let idle = HomeContent(state: content([section(.jumpBackIn, .jumpBackIn, [item])]))
        let play = try idle.inspect().find(viewWithAccessibilityIdentifier: "homeGrid.play")
        #expect((try? play.find(ViewType.ProgressView.self)) == nil)
        #expect((try? play.find(ViewType.Image.self)) != nil)

        let pending = HomeContent(state: content([section(.jumpBackIn, .jumpBackIn, [item])]), pendingPlayKey: item.key)
        let spinning = try pending.inspect().find(viewWithAccessibilityIdentifier: "homeGrid.play")
        #expect((try? spinning.find(ViewType.ProgressView.self)) != nil)
        #expect((try? spinning.find(ViewType.Image.self)) == nil)
        #expect(try spinning.accessibilityValue().string() == "Starting")
    }

    @Test func aGridCellsPlayGoesThroughPlayAndIsDisabledWhilePending() throws {
        let item = HomeItemAlbumItem(album: album("OK Computer"))
        var played: [(String, MediaAction)] = []
        var actions: [MediaAction] = []
        let sut = HomeContent(
            state: content([section(.jumpBackIn, .jumpBackIn, [item])]),
            onAction: { actions.append($0) },
            onPlay: { item, action in played.append((item.key, action)) }
        )
        try sut.inspect().find(viewWithAccessibilityLabel: "Play OK Computer").button().tap()
        #expect(played.map(\.0) == [item.key])
        #expect(played.first?.1 is MediaActionResume)
        #expect(actions.isEmpty)

        let pending = HomeContent(
            state: content([section(.jumpBackIn, .jumpBackIn, [item])]),
            onAction: { actions.append($0) },
            pendingPlayKey: item.key,
            onPlay: { item, action in played.append((item.key, action)) }
        )
        let button = try pending.inspect().find(viewWithAccessibilityLabel: "Play OK Computer").button()
        #expect(button.isDisabled())
        #expect(throws: (any Error).self) { try button.tap() }
        #expect(played.count == 1)
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

    @Test func aGridCellsPlayButtonResumesItsItemWithItsContext() throws {
        var actions: [MediaAction] = []
        let item = HomeItemAlbumItem(album: album("OK Computer"))
        let sut = HomeContent(state: content([section(.jumpBackIn, .jumpBackIn, [item])]), onAction: { actions.append($0) })
        try sut.inspect().find(viewWithAccessibilityLabel: "Play OK Computer").button().tap()
        #expect(actions.count == 1)
        #expect(same(actions.first, item.resumeAction()))
        let resume = try #require(actions.first as? MediaActionResume)
        #expect((resume.context as AnyObject).isEqual(item.playContext as AnyObject))
        #expect(resume.context is PlayContextAlbum)
    }

    @Test func aGridCellsPlayResumesWhereOtherTilesPlayFromTheStart() {
        let item = HomeItemAlbumItem(album: album("OK Computer"))
        let resuming = HomeItemActions(item: item, perform: { _ in }, open: { _ in }, resumes: true)
        #expect(same(resuming.playAction, MediaActionResume(fromStart: item.playInOrderAction(), context: item.playContext)))
        #expect(same(HomeItemActions(item: item, perform: { _ in }, open: { _ in }).playAction, item.playInOrderAction()))
    }

    @Test func aGridCellsMenuListsPlayFromStartAfterPlay() throws {
        var actions: [MediaAction] = []
        let item = HomeItemAlbumItem(album: album("OK Computer"))
        let menu = try HomeItemActions(item: item, perform: { actions.append($0) }, open: { _ in }, resumes: true).menu.inspect()
        try menu.find(button: "Play from Start").tap()
        #expect(actions.count == 1)
        #expect(same(actions.first, item.playInOrderAction()))
        let plain = try HomeItemActions(item: item, perform: { _ in }, open: { _ in }).menu.inspect()
        #expect((try? plain.find(button: "Play from Start")) == nil)
    }

    // MARK: Shelves

    @Test func shelvesListTheirItemsUnderTheirTitles() throws {
        let sut = HomeContent(state: content([
            section(.aroundThisTime, .tonight, [HomeItemAlbumItem(album: album("Amnesiac"))]),
            section(.heavyRotation, .heavyRotation, [HomeItemArtistItem(albumArtist: artist("Massive Attack"))]),
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

    @Test func aSectionShowsItsSubtitleUnderItsTitle() throws {
        let sut = HomeContent(state: content([
            section(.heavyRotation, .heavyRotation, [HomeItemAlbumItem(album: album("Mezzanine"))], subtitle: .homeHeavyRotationSubtitle),
        ]))
        #expect((try? sut.inspect().find(text: "Heavy Rotation")) != nil)
        #expect((try? sut.inspect().find(text: "What you've played most in the last 4 weeks")) != nil)
    }

    @Test func aReloadAnimatesBetweenSectionAndItemIds() {
        let kidA = HomeItemAlbumItem(album: album("Kid A"))
        let amnesiac = HomeItemAlbumItem(album: album("Amnesiac"))
        let before = HomeContent.identity([section(.rediscover, .rediscover, [kidA, amnesiac])])
        #expect(before == HomeContent.identity([section(.rediscover, .rediscover, [kidA, amnesiac])]))
        #expect(before != HomeContent.identity([section(.rediscover, .rediscover, [amnesiac, kidA])]))
    }

    @Test func aMixedShelfNamesEachTilesKind() throws {
        let sut = HomeContent(state: content([section(.heavyRotation, .heavyRotation, [
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
            section(.heavyRotation, .heavyRotation, [HomeItemAlbumItem(album: album("Kid A"))]),
            section(.recentlyAdded, .recentlyAdded, [HomeItemAlbumItem(album: album("Amnesiac"))]),
        ]))
        #expect((try? sut.inspect().find(viewWithAccessibilityLabel: "See All Recently Added")) != nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityLabel: "See All Heavy Rotation")) == nil)
    }

    @Test func menuActionsCarryTheItemsContext() {
        let genreItem = HomeItemGenreItem(genre: genre("Trip Hop"))
        let play = genreItem.playInOrderAction() as? MediaActionPlay
        #expect((play?.context as? PlayContextGenre)?.name == "Trip Hop")
        let smart = HomeItemSmartPlaylistItem(smartPlaylistId: .favourites)
        let shuffle = smart.shuffleAction() as? MediaActionShuffle
        #expect(shuffle?.context is PlayContextSmartPlaylist)
    }

    @Test func generatedArtworkKeepsItsTonePerName() {
        #expect(ArtworkPalette.toneIndex("Trip Hop") == ArtworkPalette.toneIndex("trip hop"))
        // The slots Android's `GeneratedArtworkColors.index` picks for the same names (FNV-1a mod 8), so a genre has
        // one tone on both platforms.
        let slots = ["Jazz": 0, "Trip Hop": 5, "Rock": 4, "Ambient": 3]
        for (name, slot) in slots {
            #expect(ArtworkPalette.toneIndex(name) == slot, "\(name)")
        }
    }

    @Test func everyGenreTileHasTheSameGlyph() throws {
        let sut = HomeContent(state: content([section(.genrePicks, .genrePicks, [
            HomeItemGenreItem(genre: genre("Alternative Rock")),
            HomeItemGenreItem(genre: genre("Polka")),
        ])]))
        let glyphs = try sut.inspect().findAll(GeneratedArtwork.self).map { try $0.actualView().symbol }
        #expect(glyphs == [GeneratedArtwork.genreSymbol, GeneratedArtwork.genreSymbol])
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
