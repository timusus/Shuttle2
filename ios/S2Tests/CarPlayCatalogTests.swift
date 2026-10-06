import CarPlay
import Shared
import Testing
@testable import S2

/// What CarPlay lists (#692, after Shuttle Podcasts' catalog tests): songs with Shuffle first and the playing one
/// marked, capped lists, Home's shelves as rows of artwork in the phone's order (offline only what plays), Library and
/// Playlists, empty states that explain themselves and inert informational rows; and the renderer drawing a row or an
/// image row and calling back its actions.
struct CarPlayCatalogTests {
    private func songs(_ count: Int) -> [CarPlaySong] {
        (0..<count).map { CarPlaySong(id: Int64($0), title: "Song \($0)", subtitle: "Artist") }
    }

    private func entries(_ prefix: String, _ count: Int, progress: Double? = nil, offline: Bool = true) -> [CarPlayEntry] {
        (0..<count).map {
            CarPlayEntry(id: "\(prefix)\($0)", title: "\(prefix) \($0)", subtitle: nil, progress: progress, playableOffline: offline)
        }
    }

    // MARK: - Songs

    @Test func songListsStartWithShuffleThenPlayEachSongFromItsPlace() throws {
        let rows = try #require(CarPlayCatalog.songs(songs(3)).first).rows
        #expect(rows.map(\.action) == [.shuffle, .playSong(index: 0), .playSong(index: 1), .playSong(index: 2)])
        #expect(rows.first?.title == CarPlayText.shuffle)
        #expect(rows.first?.symbol == "shuffle")
        #expect(rows[1].title == "Song 0")
        #expect(rows[1].subtitle == "Artist")
    }

    @Test func thePlayingSongIsMarked() throws {
        let rows = try #require(CarPlayCatalog.songs(songs(3), playingId: 1).first).rows
        #expect(rows.filter(\.isPlaying).map(\.title) == ["Song 1"])
    }

    @Test func songListsAreCappedAndTheCapCountsShuffle() throws {
        let rows = try #require(CarPlayCatalog.songs(songs(500)).first).rows
        #expect(rows.count == CarPlayCatalog.listRowLimit)
        let carCapped = try #require(CarPlayCatalog.songs(songs(500), limit: 12).first).rows
        #expect(carCapped.count == 12)
        #expect(carCapped.last?.action == .playSong(index: 10))
    }

    @Test func anEmptySongListSaysSoAndCantBeTapped() throws {
        let rows = try #require(CarPlayCatalog.songs([]).first).rows
        #expect(rows.count == 1)
        #expect(rows[0].title == CarPlayText.noSongs)
        #expect(!rows[0].isSelectable)
    }

    // MARK: - Library lists

    @Test func entriesOpenWithADisclosure() throws {
        let rows = try #require(CarPlayCatalog.entries(entries("Album", 2), empty: CarPlayText.noAlbums).first).rows
        #expect(rows.map(\.action) == [.open(id: "Album0"), .open(id: "Album1")])
        #expect(rows.allSatisfy { $0.accessory == .disclosure })
    }

    @Test func entriesAreCappedToTheCarsLimit() throws {
        let rows = try #require(CarPlayCatalog.entries(entries("Album", 300), empty: "", limit: 24).first).rows
        #expect(rows.count == 24)
        let uncapped = try #require(CarPlayCatalog.entries(entries("Album", 300), empty: "").first).rows
        #expect(uncapped.count == CarPlayCatalog.listRowLimit)
    }

    @Test func anEmptyLibraryListSaysWhatIsMissing() throws {
        let rows = try #require(CarPlayCatalog.entries([], empty: CarPlayText.noPlaylists).first).rows
        #expect(rows.map(\.title) == [CarPlayText.noPlaylists])
        #expect(rows.allSatisfy { !$0.isSelectable })
    }

    @Test func messagesAreInert() {
        for sections in [CarPlayCatalog.loading(), CarPlayCatalog.unavailable()] {
            #expect(sections.flatMap(\.rows).allSatisfy { $0.action == .none && !$0.isSelectable })
        }
        #expect(CarPlayCatalog.unavailable().first?.rows.first?.subtitle == CarPlayText.unavailableDetail)
    }

    /// What `CarPlaySceneDelegate` follows for its root (#946): the library while the App Store hasn't answered (fails
    /// open) and with Pro; the upgrade message without it, and back to the library once Pro arrives.
    @MainActor @Test func carPlayIsLockedOnlyWithoutProOnceTheStoreHasAnswered() async {
        let graph = makeTestGraph(audioPlayer: EngineAudioPlayer(engine: FakeAudioEngine()))
        let locked = graph.carPlayAccess.locked
        graph.storeEntitlements.setDebugOverrideNamed(name: "Store")
        #expect(await waitUntil { ProStatus(graph.storeEntitlements.entitlement.value) == .checking })
        #expect(!locked.value.boolValue)

        _ = graph.storeEntitlements.storeAnswered(purchases: [])
        #expect(await waitUntil { locked.value.boolValue })

        graph.storeEntitlements.setDebugOverrideNamed(name: "Pro")
        #expect(await waitUntil { !locked.value.boolValue })
    }

    /// Without Shuttle Music Pro the root is this alone (#946): nothing to browse or play, and the way to upgrade.
    @Test func theUpgradeRootIsOneInertRowSayingToUpgradeOnTheIPhone() throws {
        let rows = CarPlayCatalog.upgrade().flatMap(\.rows)
        let row = try #require(rows.first)

        #expect(rows.count == 1)
        #expect(row.action == .none && !row.isSelectable)
        #expect(row.title == "Upgrade to Shuttle Music Pro")
        #expect(row.subtitle == "CarPlay is part of Shuttle Music Pro. Open Shuttle Music on your iPhone to upgrade.")
    }

    // MARK: - Home

    @Test func homeIsAnImageRowPerShelfInOrderThenShuffleAll() throws {
        let rows = CarPlayCatalog.home([
            CarPlayHomeSection(id: "jumpBackIn", header: "Jump Back In", entries: entries("J", 2), resumes: true),
            CarPlayHomeSection(id: "heavyRotation", header: "Heavy Rotation", entries: entries("H", 3)),
        ]).flatMap(\.rows)
        #expect(rows.map(\.title) == ["Jump Back In", "Heavy Rotation", CarPlayText.shuffleAll])
        #expect(rows.map(\.action) == [.openShelf(id: "jumpBackIn"), .openShelf(id: "heavyRotation"), .shuffle])
        // Jump Back In's images resume, the rest play
        #expect(rows[0].images.map(\.action) == [.resume(id: "J0"), .resume(id: "J1")])
        #expect(rows[1].images.map(\.action) == [.play(id: "H0"), .play(id: "H1"), .play(id: "H2")])
        #expect(rows[1].images.map(\.title) == ["H 0", "H 1", "H 2"])
    }

    @Test func homeHidesEmptyShelvesAndCapsTheImages() {
        let rows = CarPlayCatalog.home(
            [
                CarPlayHomeSection(id: "empty", header: "Empty", entries: []),
                CarPlayHomeSection(id: "rediscover", header: "Rediscover", entries: entries("R", 30)),
            ],
            imageLimit: 5
        ).flatMap(\.rows)
        #expect(rows.map(\.title) == ["Rediscover", CarPlayText.shuffleAll])
        #expect(rows[0].images.count == 5)
        let uncapped = CarPlayCatalog.home([CarPlayHomeSection(id: "r", header: "R", entries: entries("R", 30))], imageLimit: 50)
        #expect(uncapped.first?.rows.first?.images.count == CarPlayCatalog.shelfImageLimit)
    }

    @Test func shuffleAllOnlyTakesARowThatsLeftOver() {
        let shelves = (0..<3).map { CarPlayHomeSection(id: "s\($0)", header: "S\($0)", entries: entries("S\($0)-", 1)) }
        let full = CarPlayCatalog.home(shelves, rowLimit: 3).flatMap(\.rows)
        #expect(full.map(\.action) == [.openShelf(id: "s0"), .openShelf(id: "s1"), .openShelf(id: "s2")])
        let short = CarPlayCatalog.home(shelves, rowLimit: 2).flatMap(\.rows)
        #expect(short.map(\.action) == [.openShelf(id: "s0"), .openShelf(id: "s1")])
    }

    @Test func offlineHomeShowsOnlyWhatPlaysAndNoShuffleAll() {
        let sections = [
            CarPlayHomeSection(id: "jumpBackIn", header: "Jump Back In", entries: entries("J", 2, offline: false) + entries("D", 1), resumes: true),
            CarPlayHomeSection(id: "rediscover", header: "Rediscover", entries: entries("R", 2, offline: false)),
        ]
        let rows = CarPlayCatalog.home(sections, offline: true).flatMap(\.rows)
        #expect(rows.map(\.title) == ["Jump Back In"])
        #expect(rows[0].images.map(\.action) == [.resume(id: "D0")])
        // Online, everything shows
        #expect(CarPlayCatalog.home(sections).flatMap(\.rows).count == 3)
    }

    @Test func anOfflineHomeWithNothingDownloadedSaysSo() {
        let rows = CarPlayCatalog.home([CarPlayHomeSection(id: "r", header: "R", entries: entries("R", 2, offline: false))], offline: true).flatMap(\.rows)
        #expect(rows.map(\.title) == [CarPlayText.offlineEmpty])
        #expect(rows.first?.subtitle == CarPlayText.offlineEmptyDetail)
        #expect(rows.allSatisfy { $0.action == .none })
    }

    @Test func anEmptyHomeStillOffersShuffleAllAndSaysHowItFillsIn() {
        let rows = CarPlayCatalog.home([CarPlayHomeSection(id: "empty", header: "Empty", entries: [])]).flatMap(\.rows)
        #expect(rows.map(\.action) == [.shuffle, .none])
        #expect(rows.last?.title == CarPlayText.homeEmpty)
        #expect(rows.last?.subtitle == CarPlayText.homeEmptyDetail)
    }

    @Test func aShelfListsItsItemsAndJumpBackInsResumeWithProgress() throws {
        let jumpBackIn = CarPlayHomeSection(id: "jumpBackIn", header: "Jump Back In", entries: entries("J", 3, progress: 0.333), resumes: true)
        let rows = try #require(CarPlayCatalog.shelf(jumpBackIn).first).rows
        #expect(rows.map(\.action) == [.resume(id: "J0"), .resume(id: "J1"), .resume(id: "J2")])
        // Progress to the percent, so a playing item doesn't redraw the list every tick
        #expect(rows.first?.progress == 0.33)
        let rediscover = CarPlayHomeSection(id: "rediscover", header: "Rediscover", entries: entries("R", 30, progress: 0.5))
        let suggestions = try #require(CarPlayCatalog.shelf(rediscover, limit: 12).first).rows
        #expect(suggestions.count == 12)
        #expect(suggestions.allSatisfy { $0.progress == nil })
        #expect(suggestions.first?.action == .play(id: "R0"))
    }

    @Test func anOfflineShelfKeepsOnlyWhatPlays() {
        let section = CarPlayHomeSection(id: "r", header: "R", entries: entries("R", 2, offline: false) + entries("D", 1))
        #expect(CarPlayCatalog.shelf(section, offline: true).flatMap(\.rows).map(\.action) == [.play(id: "D0")])
        let none = CarPlayHomeSection(id: "r", header: "R", entries: entries("R", 2, offline: false))
        #expect(CarPlayCatalog.shelf(none, offline: true).flatMap(\.rows).map(\.title) == [CarPlayText.offlineEmpty])
    }

    // MARK: - Library and Playlists

    @Test func libraryOpensArtistsAlbumsSongsAndGenres() throws {
        let rows = try #require(CarPlayCatalog.library().first).rows
        #expect(rows.map(\.title) == [CarPlayText.artists, CarPlayText.albums, CarPlayText.songs, CarPlayText.genres])
        #expect(rows.map(\.action) == CarPlayLibraryCategory.allCases.map { .open(id: $0.rawValue) })
        #expect(rows.allSatisfy { $0.accessory == .disclosure && $0.symbol != nil })
    }

    @Test func playlistsListTheAutoPlaylistsThenTheUsersOwnUnderOneCap() {
        let sections = CarPlayCatalog.playlists(smart: entries("S", 3), playlists: entries("P", 10), limit: 8)
        #expect(sections.map(\.header) == [CarPlayText.autoPlaylists, CarPlayText.playlists])
        #expect(sections[0].rows.map(\.action) == [.open(id: "S0"), .open(id: "S1"), .open(id: "S2")])
        #expect(sections[1].rows.count == 5)
        #expect(CarPlayCatalog.playlists(smart: [], playlists: entries("P", 2)).map(\.header) == [CarPlayText.playlists])
    }

    @Test func noPlaylistsAtAllSaysSo() {
        #expect(CarPlayCatalog.playlists(smart: [], playlists: []).flatMap(\.rows).map(\.title) == [CarPlayText.noPlaylists])
    }

    // MARK: - Queue

    @Test func theQueueStartsAtTheCurrentSongAndPlaysTheTappedOne() throws {
        let rows = try #require(CarPlayCatalog.queue(songs(5), currentIndex: 2).first).rows
        #expect(rows.map(\.action) == [.playSong(index: 2), .playSong(index: 3), .playSong(index: 4)])
        #expect(rows.first?.isPlaying == true)
        #expect(rows.dropFirst().allSatisfy { !$0.isPlaying })
    }

    @Test func anEmptyQueueSaysSo() {
        #expect(CarPlayCatalog.queue([], currentIndex: nil).first?.rows.map(\.title) == [CarPlayText.queueEmpty])
    }

    // MARK: - Strings

    @Test func theTextComesFromTheCarPlayTable() {
        #expect(CarPlayText.home == "Home")
        #expect(CarPlayText.shuffleAll == "Shuffle All")
        #expect(CarPlayText.libraryEmptyDetail == "Add music in Shuttle Music on your iPhone.")
        #expect(CarPlayText.library == "Library")
        #expect(CarPlayText.genres == "Genres")
        #expect(CarPlayText.autoPlaylists == "Auto Playlists")
        #expect(CarPlayText.noGenres == "No genres")
        #expect(CarPlayText.offlineEmpty == "Nothing to play offline")
        #expect(CarPlayText.albums == "Albums")
    }

    // MARK: - Now Playing

    @Test func nowPlayingButtonsFollowShuffleAndRepeat() {
        let off = CarPlayNowPlayingButton.set(shuffleOn: false, repeatMode: .off)
        #expect(off == [.shuffle(on: false), .repeatMode(.off)])
        #expect(off.count <= CarPlayNowPlayingButton.limit)
        #expect(off.map(\.systemImage) == ["shuffle", "repeat"])
        let on = CarPlayNowPlayingButton.set(shuffleOn: true, repeatMode: .one)
        #expect(on.map(\.systemImage) == ["shuffle.circle.fill", "repeat.1.circle.fill"])
        #expect(CarPlayNowPlayingButton.repeatMode(.all).systemImage == "repeat.circle.fill")
    }

    // MARK: - Renderer

    @Test @MainActor func theRendererDrawsARowAndCallsBackItsAction() async {
        let row = CarPlayRow(id: "1", title: "Song", subtitle: "Artist", progress: 0.5, isPlaying: true, action: .playSong(index: 3))
        var performed: [CarPlayRowAction] = []
        let item = CarPlayListRenderer.item(for: row) { performed.append($0) }
        #expect(item.text == "Song")
        #expect(item.detailText == "Artist")
        #expect(item.isPlaying)
        #expect(item.playbackProgress == 0.5)
        #expect(item.isEnabled)
        let handler = try? #require(item.handler)
        await withCheckedContinuation { continuation in
            handler?(item) { continuation.resume() }
        }
        #expect(performed == [.playSong(index: 3)])
    }

    @Test @MainActor func theRendererLeavesInformationalRowsInertAndMarksDisclosures() {
        let message = CarPlayListRenderer.item(for: CarPlayRow(id: "m", title: "Nothing", action: .none)) { _ in }
        #expect(!message.isEnabled)
        #expect(message.handler == nil)
        let entry = CarPlayListRenderer.item(for: CarPlayRow(id: "a", title: "Album", accessory: .disclosure, action: .open(id: "a"))) { _ in }
        #expect(entry.accessoryType == .disclosureIndicator)
        let shuffle = CarPlayListRenderer.item(for: CarPlayCatalog.shuffleRow()) { _ in }
        #expect(shuffle.image != nil)
    }

    @Test @MainActor func theRendererKeepsTheCatalogsSections() {
        let sections = CarPlayListRenderer.sections(CarPlayCatalog.songs(songs(2))) { _ in }
        #expect(sections.count == 1)
        #expect(sections[0].items.count == 3)
    }

    @Test @MainActor func theRendererDrawsAShelfAsAnImageRowWhoseImagesAndRowAct() async throws {
        let row = try #require(CarPlayCatalog.home([
            CarPlayHomeSection(id: "jumpBackIn", header: "Jump Back In", entries: entries("J", 3), resumes: true),
        ]).first?.rows.first)
        let sections = CarPlayListRenderer.sections([CarPlaySectionModel(header: nil, rows: [row])]) { _ in }
        #expect(sections[0].items.first is CPListImageRowItem)
        var performed: [CarPlayRowAction] = []
        let item = CarPlayListRenderer.imageRow(for: row) { performed.append($0) }
        #expect(item.text == "Jump Back In")
        let imageHandler = try #require(item.listImageRowHandler)
        await withCheckedContinuation { continuation in
            imageHandler(item, 1) { continuation.resume() }
        }
        // An index past the images does nothing
        await withCheckedContinuation { continuation in
            imageHandler(item, 9) { continuation.resume() }
        }
        let rowHandler = try #require(item.handler)
        await withCheckedContinuation { continuation in
            rowHandler(item) { continuation.resume() }
        }
        #expect(performed == [.resume(id: "J1"), .openShelf(id: "jumpBackIn")])
    }
}
