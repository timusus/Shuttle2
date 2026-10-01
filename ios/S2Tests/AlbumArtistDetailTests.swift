import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// Album artist detail from its UiState: the hero, the album shelf's links, Top Songs, the song sections per sort
/// order with their folding headers, the sort menu, and what tapping a song plays.
@MainActor
struct AlbumArtistDetailTests {
    private func album(_ name: String, year: Int32? = nil, albumArtist: String = "Radiohead") -> Album {
        Album(
            name: name, albumArtist: albumArtist, artists: [albumArtist], songCount: 1, duration: 0,
            year: year.map { KotlinInt(int: $0) }, playCount: 0, lastSongPlayed: nil, lastSongCompleted: nil,
            groupKey: AlbumGroupKey(key: name.lowercased(), albumArtistGroupKey: AlbumArtistGroupKey(key: albumArtist.lowercased()), identity: nil),
            mediaProviders: [.jellyfin], artworkVersion: nil, dateAdded: nil
        )
    }

    private func artist() -> AlbumArtist {
        AlbumArtist(
            name: "Radiohead", artists: ["Radiohead"], albumCount: 2, songCount: 2, playCount: 0,
            groupKey: AlbumArtistGroupKey(key: "radiohead"), mediaProviders: [.jellyfin], artworkVersion: nil, appearsOnCount: 0
        )
    }

    /// The demo songs as one flat section, as the flat orders list them.
    private func flat(albums: [Album] = [], appearsOn: [Album] = []) -> AlbumArtistDetailUiState {
        AlbumArtistDetailUiState(
            albumArtist: artist(), albums: albums, appearsOn: appearsOn, songs: TestSongs.demo,
            sortOrder: .songTitle, sections: [.init(album: nil, songs: TestSongs.demo)], topSongs: [],
            currentSong: nil, expandedAlbums: [], loadingState: .ready, events: [], seed: ArtworkSeedNone.shared
        )
    }

    /// Two albums, Kid A (2000) holding the third and fourth demo songs and OK Computer (1997) the first two, then
    /// the fifth song with no album, in `order`; `expanded` names the unfolded albums.
    private func sectioned(_ order: ArtistSongSortOrder, expanded: [String] = [], topSongs: [Song] = []) -> AlbumArtistDetailUiState {
        let ok = album("OK Computer", year: 1997)
        let kidA = album("Kid A", year: 2000)
        let songs = TestSongs.demo
        let sections: [AlbumArtistDetailUiState.SongSection] = order.groupsByAlbum
            ? [.init(album: kidA, songs: [songs[2], songs[3]]), .init(album: ok, songs: [songs[0], songs[1]]), .init(album: nil, songs: [songs[4]])]
            : [.init(album: nil, songs: songs)]
        let expandedKeys = Set([ok, kidA].filter { expanded.contains($0.name ?? "") }.compactMap(\.groupKey))
        return AlbumArtistDetailUiState(
            albumArtist: artist(), albums: [kidA, ok], appearsOn: [], songs: sections.flatMap(\.songs), sortOrder: order, sections: sections,
            topSongs: topSongs, currentSong: nil, expandedAlbums: expandedKeys, loadingState: .ready, events: [],
            seed: ArtworkSeedNone.shared
        )
    }

    private func topSongRows(_ view: some View) throws -> Int {
        try view.inspect().findAll(ViewType.Button.self, where: { (try? $0.accessibilityIdentifier()) == "artistDetail.topSong" }).count
    }

    @Test func readyShowsHeroShelfAndSongs() throws {
        let sut = AlbumArtistDetailContent(state: flat(albums: [album("OK Computer"), album("Kid A")]))
        #expect((try? sut.inspect().find(text: "Radiohead")) != nil)
        #expect((try? sut.inspect().find(text: "2 albums · 5 songs")) != nil)
        #expect((try? sut.inspect().find(text: "OK Computer")) != nil)
        #expect((try? sut.inspect().find(text: "Kid A")) != nil)
        #expect((try? sut.inspect().find(text: "Paranoid Android")) != nil)
    }

    @Test func tappingAnAlbumTileOpensIt() throws {
        var opened: Album?
        let sut = AlbumArtistDetailContent(state: flat(albums: [album("OK Computer"), album("Kid A")]), onAlbumTap: { opened = $0 })
        try sut.inspect().find(button: "OK Computer").tap()
        #expect(opened?.name == "OK Computer")
    }

    @Test func tappingASongPlaysFromItsIndex() throws {
        var played: (songs: [Song], index: Int, context: PlayContext)?
        let sut = AlbumArtistDetailContent(state: flat(), onPlay: { played = ($0, $1, $2) })
        try sut.inspect().find(button: "Teardrop").tap()
        #expect(played?.index == 2)
        #expect(played?.songs.map(\.id) == TestSongs.demo.map(\.id))
    }

    // MARK: - Sections (#631)

    @Test func albumOrdersShowOneHeaderPerAlbumAndOtherSongs() throws {
        let sut = AlbumArtistDetailContent(state: sectioned(.albumNewest, expanded: ["OK Computer", "Kid A"]))
        let headers = try sut.inspect().findAll(AlbumSectionHeader.self)
        #expect(try headers.map { try $0.actualView().album.name } == ["Kid A", "OK Computer"])
        #expect((try? sut.inspect().find(text: "2000 · 2 songs")) != nil)
        #expect((try? sut.inspect().find(text: "Other Songs")) != nil)
        #expect((try? sut.inspect().find(text: "Pyramid Song")) != nil)
        #expect((try? sut.inspect().find(button: "Expand All")) == nil)
        #expect((try? sut.inspect().find(button: "Collapse All")) != nil)
    }

    @Test func albumOrdersDropTheAlbumShelfForTheirHeaders() throws {
        let sut = AlbumArtistDetailContent(state: sectioned(.albumNewest))
        #expect(try sut.inspect().findAll(DetailAlbumShelf.self).isEmpty)
        // The one "Albums" is the song list's header, titling the album sections
        #expect(try sut.inspect().findAll(ViewType.Text.self, where: { try $0.string() == "Albums" }).count == 1)
        #expect((try? sut.inspect().find(SongsHeader.self).find(text: "Albums")) != nil)
        #expect((try? sut.inspect().find(button: "Collapse All")) != nil || (try? sut.inspect().find(button: "Expand All")) != nil)
    }

    @Test func flatOrdersKeepTheAlbumShelf() throws {
        let sut = AlbumArtistDetailContent(state: sectioned(.songTitle))
        #expect(try sut.inspect().findAll(DetailAlbumShelf.self).count == 1)
        #expect((try? sut.inspect().find(DetailAlbumShelf.self).find(text: "Albums")) != nil)
        #expect((try? sut.inspect().find(SongsHeader.self).find(text: "Songs")) != nil)
    }

    @Test func flatOrdersShowOnePlainList() throws {
        let sut = AlbumArtistDetailContent(state: sectioned(.mostPlayed))
        #expect(try sut.inspect().findAll(AlbumSectionHeader.self).isEmpty)
        #expect((try? sut.inspect().find(text: "Other Songs")) == nil)
        #expect((try? sut.inspect().find(button: "Expand All")) == nil)
        #expect((try? sut.inspect().find(text: "Paranoid Android")) != nil)
        #expect((try? sut.inspect().find(text: "Pyramid Song")) != nil)
    }

    @Test func collapsedAlbumsHideTheirSongsButOtherSongsStay() throws {
        let sut = AlbumArtistDetailContent(state: sectioned(.albumNewest, expanded: ["Kid A"]))
        #expect((try? sut.inspect().find(text: "Teardrop")) != nil)
        #expect((try? sut.inspect().find(text: "Paranoid Android")) == nil)
        #expect((try? sut.inspect().find(text: "Pyramid Song")) != nil)
        #expect((try? sut.inspect().find(button: "Expand All")) != nil)
    }

    @Test func tappingAHeaderTogglesItsAlbum() throws {
        var toggled: Album?
        let sut = AlbumArtistDetailContent(state: sectioned(.albumNewest), onToggleAlbum: { toggled = $0 })
        try sut.inspect().find(AlbumSectionHeader.self).find(ViewType.Button.self, where: { try $0.accessibilityIdentifier() == "artistDetail.albumHeader" }).tap()
        #expect(toggled?.name == "Kid A")
    }

    @Test func tappingAHeadersThumbnailOpensItsAlbum() throws {
        var opened: Album?
        var toggled: Album?
        let sut = AlbumArtistDetailContent(state: sectioned(.albumNewest), onAlbumTap: { opened = $0 }, onToggleAlbum: { toggled = $0 })
        try sut.inspect().find(AlbumSectionHeader.self).find(ViewType.Button.self, where: { try $0.accessibilityIdentifier() == "artistDetail.albumThumbnail" }).tap()
        #expect(opened?.name == "Kid A")
        #expect(toggled == nil)
    }

    @Test func headerTellsVoiceOverWhetherItIsExpanded() throws {
        let collapsed = AlbumSectionHeader(album: album("Kid A", year: 2000), songCount: 2, isExpanded: false)
        #expect(try collapsed.inspect().find(viewWithAccessibilityIdentifier: "artistDetail.albumHeader").accessibilityValue().string() == "Collapsed")
        let expanded = AlbumSectionHeader(album: album("Kid A", year: 2000), songCount: 2, isExpanded: true)
        #expect(try expanded.inspect().find(viewWithAccessibilityIdentifier: "artistDetail.albumHeader").accessibilityValue().string() == "Expanded")
    }

    @Test func tappingASectionedSongPlaysTheVisibleOrderFromIt() throws {
        var played: (songs: [Song], index: Int, context: PlayContext)?
        let state = sectioned(.albumNewest, expanded: ["OK Computer", "Kid A"])
        let sut = AlbumArtistDetailContent(state: state, onPlay: { played = ($0, $1, $2) })
        try sut.inspect().find(button: "Paranoid Android").tap()
        #expect(played?.index == 2)
        #expect(played?.songs.map(\.id) == state.songs.map(\.id))
    }

    @Test func expandAndCollapseAllCallTheViewModel() throws {
        var expandedAll = false
        var collapsedAll = false
        let collapsed = AlbumArtistDetailContent(state: sectioned(.albumNewest), onExpandAll: { expandedAll = true })
        try collapsed.inspect().find(button: "Expand All").tap()
        #expect(expandedAll)
        let expanded = AlbumArtistDetailContent(
            state: sectioned(.albumNewest, expanded: ["OK Computer", "Kid A"]),
            onCollapseAll: { collapsedAll = true }
        )
        try expanded.inspect().find(button: "Collapse All").tap()
        #expect(collapsedAll)
    }

    @Test func sortMenuListsTheOrdersAndSelectsOne() throws {
        var selected: ArtistSongSortOrder?
        let sut = SongsHeader(sortOrder: .albumNewest, allExpanded: false, onSortOrderSelected: { selected = $0 })
        let picker = try sut.inspect().find(ViewType.Picker.self)
        for order in ArtistSongSortOrder.menuOrder {
            #expect((try? picker.find(text: order.menuTitle)) != nil)
        }
        try picker.select(value: ArtistSongSortOrder.mostPlayed)
        #expect(selected == .mostPlayed)
    }

    @Test func sortMenuIsLabelledWithTheCurrentOrder() throws {
        let newest = SongsHeader(sortOrder: .albumNewest, allExpanded: false)
        #expect((try? newest.inspect().find(text: "Newest")) != nil)
        #expect((try? newest.inspect().find(text: "Albums")) != nil)
        let title = SongsHeader(sortOrder: .songTitle, allExpanded: false)
        #expect((try? title.inspect().find(text: "Title A–Z")) != nil)
        #expect((try? title.inspect().find(text: "Songs")) != nil)
    }

    @Test func expandAllIsAnIconButtonNamedForVoiceOver() throws {
        let collapsed = SongsHeader(sortOrder: .albumNewest, allExpanded: false)
        let expand = try collapsed.inspect().find(viewWithAccessibilityIdentifier: "artistDetail.expandAll")
        #expect(try expand.accessibilityLabel().string() == "Expand All")
        let expanded = SongsHeader(sortOrder: .albumNewest, allExpanded: true)
        let collapse = try expanded.inspect().find(viewWithAccessibilityIdentifier: "artistDetail.expandAll")
        #expect(try collapse.accessibilityLabel().string() == "Collapse All")
    }

    // MARK: - Hero

    private func image(width: Int, height: Int) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format).image { context in
            UIColor.gray.setFill()
            context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        }
    }

    @Test func aPhotoRunsFullBleedOnlyWhenItsShortestSideIsSharpEnough() {
        #expect(ArtistHeroPhoto.resolve(nil) == .compact)
        #expect(ArtistHeroPhoto.resolve(image(width: 599, height: 1200)) == .compact)
        #expect(ArtistHeroPhoto.resolve(image(width: 400, height: 400)) == .compact)
        #expect(ArtistHeroPhoto.resolve(image(width: 600, height: 600)).image != nil)
        #expect(ArtistHeroPhoto.resolve(image(width: 1200, height: 800)).image != nil)
    }

    @Test func aFullBleedHeroPutsTheNameOverThePhotoAndDropsTheSquare() throws {
        let sut = AlbumArtistDetailContent(state: flat(albums: [album("OK Computer", year: 1997)]), heroPhoto: .resolve(image(width: 800, height: 800)))
        let hero = try sut.inspect().find(ArtistBleedHero.self)
        #expect((try? hero.find(text: "Radiohead")) != nil)
        #expect((try? hero.find(text: "1 album · 5 songs")) != nil)
        #expect((try? hero.find(button: "Shuffle")) != nil)
        #expect((try? sut.inspect().find(ArtistBackdrop.self)) != nil)
    }

    @Test func noPhotoKeepsTheCompactHero() throws {
        let sut = AlbumArtistDetailContent(state: flat(), heroPhoto: .compact)
        #expect(try sut.inspect().findAll(ArtistBleedHero.self).isEmpty)
        #expect(try sut.inspect().findAll(ArtistBackdrop.self).isEmpty)
        #expect((try? sut.inspect().find(text: "Radiohead")) != nil)
    }

    @Test func aPendingPhotoShowsTheNameAndActionsOverAPlaceholderAtOnce() throws {
        let sut = AlbumArtistDetailContent(state: flat(), heroPhoto: .pending)
        let hero = try sut.inspect().find(ArtistBleedHero.self)
        #expect((try? hero.find(text: "Radiohead")) != nil)
        #expect((try? hero.find(button: "Play")) != nil)
        let backdrop = try sut.inspect().find(ArtistBackdrop.self).actualView()
        #expect(backdrop.image == nil)
        #expect(ArtistHeroPhoto.pending.isFullBleed)
        #expect(!ArtistHeroPhoto.compact.isFullBleed)
    }

    /// The backdrop over a white photo: darkened at the top for the bars, and faded out at the bottom into whatever is
    /// behind it rather than ending in a hard edge.
    @Test func theBackdropDarkensTheTopAndFadesOutAtTheBottom() throws {
        let size = CGSize(width: 300, height: 400)
        let renderer = ImageRenderer(content: ArtistBackdrop(image: whiteImage()).frame(width: size.width, height: size.height))
        renderer.scale = 1
        let cgImage = try #require(renderer.cgImage)
        let top = try pixel(cgImage, x: 150, y: 1)
        let middle = try pixel(cgImage, x: 150, y: 160)
        let bottom = try pixel(cgImage, x: 150, y: 399)
        #expect(middle.alpha > 0.99 && middle.white > 0.99, "untouched between the scrims")
        #expect(top.white < 0.7 && top.white > 0.55, "the top scrim, about 35% black")
        #expect(bottom.alpha < 0.1, "faded out at the bottom edge")
    }

    private func whiteImage() -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: 800, height: 800), format: format).image { context in
            UIColor.white.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 800, height: 800))
        }
    }

    /// The un-premultiplied grey level and alpha of one pixel.
    private func pixel(_ image: CGImage, x: Int, y: Int) throws -> (white: CGFloat, alpha: CGFloat) {
        var data = [UInt8](repeating: 0, count: 4)
        let context = try #require(CGContext(
            data: &data, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4,
            space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ))
        context.draw(image, in: CGRect(x: -x, y: -(image.height - 1 - y), width: image.width, height: image.height))
        let alpha = CGFloat(data[3]) / 255
        return (alpha > 0 ? CGFloat(data[0]) / 255 / alpha : 0, alpha)
    }

    @Test func aFullBleedHeroPlaysAndShufflesTheArtist() throws {
        var played = false
        var shuffled = false
        let sut = AlbumArtistDetailContent(
            state: flat(), onPlay: { _, _, _ in played = true }, onShuffle: { _, _ in shuffled = true },
            heroPhoto: .resolve(image(width: 800, height: 800))
        )
        let hero = try sut.inspect().find(ArtistBleedHero.self)
        try hero.find(button: "Play").tap()
        try hero.find(button: "Shuffle").tap()
        #expect(played)
        #expect(shuffled)
    }

    @Test func topSongsHiddenWhenNone() throws {
        let sut = AlbumArtistDetailContent(state: sectioned(.albumNewest))
        #expect((try? sut.inspect().find(text: "Top Songs")) == nil)
    }

    @Test func topSongsShowFiveOnCompactWithSeeAll() throws {
        let top = TestSongs.demo + [TestSongs.song(6, "Karma Police", artist: "Radiohead", album: "OK Computer", durationMs: 264_000)]
        var selected: ArtistSongSortOrder?
        // Compact is the default `\.layoutTier`
        let sut = AlbumArtistDetailContent(state: sectioned(.albumNewest, topSongs: top), onSortOrderSelected: { selected = $0 })
        #expect((try? sut.inspect().find(text: "Top Songs")) != nil)
        #expect(try topSongRows(sut) == 5)
        try sut.inspect().find(text: "See All").find(ViewType.Button.self, relation: .parent).tap()
        #expect(selected == .mostPlayed)
    }

    @Test func topSongsWithinTheLimitHaveNoSeeAll() throws {
        let sut = AlbumArtistDetailContent(state: sectioned(.albumNewest, topSongs: Array(TestSongs.demo.prefix(3))))
        #expect(try topSongRows(sut) == 3)
        #expect((try? sut.inspect().find(text: "See All")) == nil)
    }

    @Test func topSongsLimitIsFiveOnCompactAndTenOnRegular() {
        #expect(AlbumArtistDetailContent.topSongsLimit(.compact) == 5)
        #expect(AlbumArtistDetailContent.topSongsLimit(.regular) == 10)
        #expect(AlbumArtistDetailContent.topSongsLimit(.wide) == 10)
    }

    @Test func tappingATopSongPlaysTheTopSongs() throws {
        var played: (songs: [Song], index: Int, context: PlayContext)?
        let top = [TestSongs.demo[3], TestSongs.demo[0]]
        let sut = AlbumArtistDetailContent(state: sectioned(.albumNewest, topSongs: top), onPlay: { played = ($0, $1, $2) })
        try sut.inspect().find(where: { (try? $0.accessibilityIdentifier()) == "artistDetail.topSong" }).button().tap()
        #expect(played?.index == 0)
        #expect(played?.songs.map(\.id) == top.map(\.id))
    }

    @Test func placeholders() throws {
        let loading = AlbumArtistDetailUiState(
            albumArtist: nil, albums: [], appearsOn: [], songs: [], sortOrder: .albumNewest, sections: [], topSongs: [],
            currentSong: nil, expandedAlbums: [], loadingState: .loading,
            events: [], seed: ArtworkSeedNone.shared
        )
        #expect((try? AlbumArtistDetailContent(state: loading).inspect().find(ViewType.ProgressView.self)) != nil)
        let notFound = AlbumArtistDetailUiState(
            albumArtist: nil, albums: [], appearsOn: [], songs: [], sortOrder: .albumNewest, sections: [], topSongs: [],
            currentSong: nil, expandedAlbums: [], loadingState: .empty,
            events: [], seed: ArtworkSeedNone.shared
        )
        #expect((try? AlbumArtistDetailContent(state: notFound).inspect().find(text: "Artist Not Found")) != nil)
    }

    // MARK: - Play context (#633)

    @Test func tappingASongPassesTheArtistsPlayContext() throws {
        var context: PlayContext?
        let sut = AlbumArtistDetailContent(state: flat(), onPlay: { _, _, playContext in context = playContext })
        try sut.inspect().find(button: "Teardrop").tap()
        #expect(context is PlayContextAlbumArtist)
    }

    @Test func heroAndTopSongsPassTheArtistsPlayContext() throws {
        var heroContext: PlayContext?
        var topSongContext: PlayContext?
        let top = [TestSongs.demo[0]]
        let sut = AlbumArtistDetailContent(
            state: sectioned(.albumNewest, topSongs: top),
            onPlay: { _, _, context in topSongContext = context },
            onShuffle: { _, context in heroContext = context }
        )
        try sut.inspect().find(where: { (try? $0.accessibilityIdentifier()) == "artistDetail.topSong" }).button().tap()
        #expect(topSongContext is PlayContextAlbumArtist)
        try sut.inspect().find(button: "Shuffle").tap()
        #expect(heroContext is PlayContextAlbumArtist)
    }

    @Test func albumHeaderPlayAndShufflePassTheAlbumsOwnContext() throws {
        var playedContext: PlayContext?
        var shuffledContext: PlayContext?
        let sut = AlbumArtistDetailContent(
            state: sectioned(.albumNewest, expanded: ["Kid A"]),
            onPlay: { _, _, context in playedContext = context },
            onShuffle: { _, context in shuffledContext = context }
        )
        let header = try sut.inspect().find(AlbumSectionHeader.self).actualView()
        header.onPlay()
        header.onShuffle()
        #expect(playedContext is PlayContextAlbum)
        #expect(shuffledContext is PlayContextAlbum)
    }

    // MARK: - Issue #636

    @Test func expandAllHiddenWithNoAlbumSections() throws {
        let state = AlbumArtistDetailUiState(
            albumArtist: artist(), albums: [], appearsOn: [], songs: TestSongs.demo, sortOrder: .albumNewest,
            sections: [.init(album: nil, songs: TestSongs.demo)], topSongs: [], currentSong: nil,
            expandedAlbums: [], loadingState: .ready, events: [], seed: ArtworkSeedNone.shared
        )
        let sut = AlbumArtistDetailContent(state: state)
        #expect((try? sut.inspect().find(button: "Expand All")) == nil)
        #expect((try? sut.inspect().find(button: "Collapse All")) == nil)
    }

    @Test func tappingADuplicateIdSongPlaysFromItsOwnPosition() throws {
        let ok = album("OK Computer", year: 1997)
        let kidA = album("Kid A", year: 2000)
        // The same song id, on two albums (a bug source, #636): the second listing's row must play from its own
        // position, not the first listing's.
        let onKidA = TestSongs.song(1, "Paranoid Android (Kid A)", artist: "Radiohead", album: "Kid A", durationMs: 386_000)
        let onOkComputer = TestSongs.song(1, "Paranoid Android (OK Computer)", artist: "Radiohead", album: "OK Computer", durationMs: 386_000)
        let sections: [AlbumArtistDetailUiState.SongSection] = [
            .init(album: kidA, songs: [onKidA, TestSongs.demo[2]]),
            .init(album: ok, songs: [TestSongs.demo[1], onOkComputer]),
        ]
        let state = AlbumArtistDetailUiState(
            albumArtist: artist(), albums: [kidA, ok], appearsOn: [], songs: sections.flatMap(\.songs), sortOrder: .albumNewest,
            sections: sections, topSongs: [], currentSong: nil,
            expandedAlbums: Set([kidA, ok].compactMap(\.groupKey)), loadingState: .ready, events: [], seed: ArtworkSeedNone.shared
        )
        var played: (songs: [Song], index: Int)?
        let sut = AlbumArtistDetailContent(state: state, onPlay: { songs, index, _ in played = (songs, index) })
        try sut.inspect().find(button: "Paranoid Android (OK Computer)").tap()
        #expect(played?.index == 3)
        #expect(played?.songs.map(\.id) == state.songs.map(\.id))
    }

    // MARK: - Appears On (#637)

    private func appearsOnTiles(_ view: some View) throws -> Int {
        try view.inspect().findAll(ViewType.Button.self, where: { (try? $0.accessibilityIdentifier()) == "detailTile.appearsOn" }).count
    }

    @Test func appearsOnHiddenWhenNothingCreditsTheArtist() throws {
        let sut = AlbumArtistDetailContent(state: flat(albums: [album("OK Computer")]))
        #expect((try? sut.inspect().find(text: "Appears On")) == nil)
        #expect(try appearsOnTiles(sut) == 0)
    }

    @Test func appearsOnShelfFollowsTheAlbumsAndOpensOthersAlbums() throws {
        var opened: Album?
        let compilation = album("Help: A Day in the Life", year: 2005, albumArtist: "Various Artists")
        let sut = AlbumArtistDetailContent(state: flat(albums: [album("OK Computer")], appearsOn: [compilation]), onAlbumTap: { opened = $0 })
        #expect((try? sut.inspect().find(text: "Appears On")) != nil)
        #expect((try? sut.inspect().find(text: "Various Artists")) != nil)
        #expect(try appearsOnTiles(sut) == 1)
        try sut.inspect().find(button: "Help: A Day in the Life").tap()
        #expect(opened?.name == "Help: A Day in the Life")
    }

    @Test func aShelfTilesMenuPlaysOrQueuesItsAlbum() throws {
        var played: Album?
        var next: Album?
        var queued: Album?
        let compilation = album("Help: A Day in the Life", albumArtist: "Various Artists")
        let menu = DetailAlbumMenuItems(
            album: compilation,
            actions: DetailAlbumActions(onPlay: { played = $0 }, onPlayNext: { next = $0 }, onAddToQueue: { queued = $0 })
        )
        try menu.inspect().find(button: "Play").tap()
        try menu.inspect().find(button: "Play Next").tap()
        try menu.inspect().find(button: "Add to Queue").tap()
        #expect([played, next, queued].map { $0?.name } == Array(repeating: "Help: A Day in the Life", count: 3))
        // Without actions the menu has nothing to offer.
        let none = DetailAlbumMenuItems(album: compilation, actions: DetailAlbumActions())
        #expect((try? none.inspect().find(button: "Play")) == nil)
    }

    @Test func anArtistOnlyCreditedElsewhereCountsJustTheirSongs() throws {
        let sut = AlbumArtistDetailContent(state: flat(appearsOn: [album("Graduation", albumArtist: "Kanye West")]))
        #expect((try? sut.inspect().find(text: "5 songs")) != nil)
        #expect((try? sut.inspect().find(text: "Albums")) == nil)
    }
}
