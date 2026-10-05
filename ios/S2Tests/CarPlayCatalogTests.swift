import CarPlay
import Testing
@testable import S2

/// What CarPlay lists (#692, after Shuttle Podcasts' catalog tests): songs with Shuffle first and the playing one
/// marked, capped lists, Home's sections trimmed to the car's budget with Jump Back In kept whole, empty states that
/// explain themselves and inert informational rows; and the renderer drawing a row and calling back its action.
struct CarPlayCatalogTests {
    private func songs(_ count: Int) -> [CarPlaySong] {
        (0..<count).map { CarPlaySong(id: Int64($0), title: "Song \($0)", subtitle: "Artist") }
    }

    private func entries(_ prefix: String, _ count: Int, progress: Double? = nil) -> [CarPlayEntry] {
        (0..<count).map { CarPlayEntry(id: "\(prefix)\($0)", title: "\(prefix) \($0)", subtitle: nil, progress: progress) }
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

    // MARK: - Home

    @Test func homeOffersShuffleAllThenEachSectionsItemsWhichPlay() throws {
        let sections = CarPlayCatalog.home([
            CarPlayHomeSection(id: "jumpBackIn", header: "Jump Back In", entries: entries("J", 2, progress: 0.333), keepsAll: true),
            CarPlayHomeSection(id: "heavyRotation", header: "Heavy Rotation", entries: entries("H", 2)),
        ])
        #expect(sections.map(\.header) == [nil, "Jump Back In", "Heavy Rotation"])
        let shuffle = try #require(sections.first?.rows.first)
        #expect(shuffle.title == CarPlayText.shuffleAll)
        #expect(shuffle.action == .shuffle)
        #expect(sections[1].rows.map(\.action) == [.play(id: "J0"), .play(id: "J1")])
        // Progress to the percent, so a playing item doesn't redraw Home every tick
        #expect(sections[1].rows.first?.progress == 0.33)
        #expect(sections[2].rows.first?.progress == nil)
    }

    @Test func homeCapsEachSuggestionSection() {
        let sections = CarPlayCatalog.home([CarPlayHomeSection(id: "rediscover", header: "Rediscover", entries: entries("R", 30))])
        #expect(sections[1].rows.count == CarPlayCatalog.homeSectionRowLimit)
    }

    @Test func homeTrimsSuggestionsToTheBudgetButKeepsJumpBackInWhole() {
        let sections = CarPlayCatalog.home(
            [
                CarPlayHomeSection(id: "heavyRotation", header: "Heavy Rotation", entries: entries("H", 8)),
                CarPlayHomeSection(id: "jumpBackIn", header: "Jump Back In", entries: entries("J", 6), keepsAll: true),
                CarPlayHomeSection(id: "rediscover", header: "Rediscover", entries: entries("R", 8)),
            ],
            budget: CarPlayBudget(maxItems: 10, maxSections: 10)
        )
        // Shuffle All (1) and Jump Back In (6) leave 3 for the suggestions, which go in order
        #expect(sections.map(\.header) == [nil, "Heavy Rotation", "Jump Back In"])
        #expect(sections[1].rows.count == 3)
        #expect(sections[2].rows.count == 6)
    }

    @Test func homeDropsSectionsPastTheSectionBudget() {
        let sections = CarPlayCatalog.home(
            [
                CarPlayHomeSection(id: "a", header: "A", entries: entries("A", 1)),
                CarPlayHomeSection(id: "b", header: "B", entries: entries("B", 1)),
                CarPlayHomeSection(id: "c", header: "C", entries: entries("C", 1)),
            ],
            budget: CarPlayBudget(maxItems: 100, maxSections: 3)
        )
        #expect(sections.map(\.header) == [nil, "A", "B"])
    }

    @Test func anEmptyHomeStillOffersShuffleAllAndSaysHowItFillsIn() {
        let rows = CarPlayCatalog.home([CarPlayHomeSection(id: "empty", header: "Empty", entries: [])]).flatMap(\.rows)
        #expect(rows.map(\.action) == [.shuffle, .none])
        #expect(rows.last?.title == CarPlayText.homeEmpty)
        #expect(rows.last?.subtitle == CarPlayText.homeEmptyDetail)
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
}
