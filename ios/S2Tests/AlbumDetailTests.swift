import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// Album detail from its UiState: the loading/not-found placeholders, the hero's title/subtitle, disc grouping,
/// and what tapping a track plays.
@MainActor
struct AlbumDetailTests {
    private func song(_ id: Int64, _ name: String, track: Int32?, disc: Int32?, composer: String? = nil, work: String? = nil, movement: String? = nil, movementNumber: Int32? = nil, movementTotal: Int32? = nil) -> Song {
        Song(
            id: id, name: name, albumArtist: "Radiohead", artists: ["Radiohead"], album: "OK Computer",
            track: track.map { KotlinInt(int: $0) }, disc: disc.map { KotlinInt(int: $0) }, duration: 240_000,
            date: nil, genres: composer == nil ? [] : ["Classical"], path: "demo://\(id)", size: 0, mimeType: "audio/flac", lastModified: nil,
            lastPlayed: nil, lastCompleted: nil, playCount: 0, playbackPosition: 0, blacklisted: false,
            externalId: nil, mediaProvider: .shuttle, replayGainTrack: nil, replayGainAlbum: nil, lyrics: nil,
            grouping: nil, bitRate: nil, bitDepth: nil, sampleRate: nil, channelCount: nil, audioCodec: nil,
            artworkVersion: nil, artworkItemId: nil, dateAdded: nil, favouritedAt: nil, albumArtists: nil, artistsTag: nil,
            artistDisplay: nil, albumArtistDisplay: nil, compilation: nil, mbTrackId: nil, mbAlbumId: nil, mbReleaseGroupId: nil,
            mbArtistIds: nil, mbAlbumArtistIds: nil, serverAlbumId: nil, serverArtistIds: nil,
            serverAlbumArtistIds: nil, albumIdentity: nil, composer: composer, composerSort: nil, conductor: nil, arranger: nil,
            work: work, movementName: movement, movementNumber: movementNumber.map { KotlinInt(int: $0) }, movementTotal: movementTotal.map { KotlinInt(int: $0) }, showMovement: nil, performers: []
        )
    }

    private func album(songCount: Int32 = 2, year: Int32? = 1997, albumArtist: String? = "Radiohead", artists: [String] = ["Radiohead"], artistKey: String? = "radiohead", albumArtistKeys: [String]? = nil) -> Album {
        Album(
            name: "OK Computer", albumArtist: albumArtist, artists: artists, songCount: songCount, duration: 0,
            year: year.map { KotlinInt(int: $0) }, playCount: 0, lastSongPlayed: nil, lastSongCompleted: nil,
            groupKey: AlbumGroupKey(key: "ok computer", albumArtistGroupKey: AlbumArtistGroupKey(key: artistKey), identity: nil),
            mediaProviders: [.jellyfin], artworkVersion: nil, dateAdded: nil,
            albumArtistKeys: (albumArtistKeys ?? artistKey.map { [$0] } ?? []).map { AlbumArtistGroupKey(key: $0) }
        )
    }

    @Test func readyShowsHeroAndSongs() throws {
        let songs = [song(1, "Airbag", track: 1, disc: 1), song(2, "Paranoid Android", track: 2, disc: 1)]
        let state = AlbumDetailUiState(album: album(), songs: songs, currentSong: nil, loadingState: .ready, seed: ArtworkSeedNone.shared, moreByArtist: [], moreByArtistNames: [], classical: false)
        let sut = AlbumDetailContent(state: state)
        #expect((try? sut.inspect().find(text: "OK Computer")) != nil)
        #expect((try? sut.inspect().find(ViewType.Text.self, where: { try $0.string().hasPrefix("Radiohead · 1997 · 2 songs · ") })) != nil)
        #expect((try? sut.inspect().find(text: "Airbag")) != nil)
        #expect((try? sut.inspect().find(text: "Paranoid Android")) != nil)
    }

    @Test func multiDiscAlbumsGetDiscSections() throws {
        let songs = [song(1, "Track A", track: 1, disc: 1), song(2, "Track B", track: 1, disc: 2)]
        let state = AlbumDetailUiState(album: album(songCount: 2), songs: songs, currentSong: nil, loadingState: .ready, seed: ArtworkSeedNone.shared, moreByArtist: [], moreByArtistNames: [], classical: false)
        let sut = AlbumDetailContent(state: state)
        #expect((try? sut.inspect().find(text: "Disc 1")) != nil)
        #expect((try? sut.inspect().find(text: "Disc 2")) != nil)
    }

    @Test func singleDiscAlbumsGetNoDiscSectionTitle() throws {
        let songs = [song(1, "Airbag", track: 1, disc: 1)]
        let state = AlbumDetailUiState(album: album(songCount: 1), songs: songs, currentSong: nil, loadingState: .ready, seed: ArtworkSeedNone.shared, moreByArtist: [], moreByArtistNames: [], classical: false)
        let sut = AlbumDetailContent(state: state)
        #expect((try? sut.inspect().find(text: "Disc 1")) == nil)
    }

    private func moreByState(_ others: [Album]) -> AlbumDetailUiState {
        AlbumDetailUiState(album: album(songCount: 1), songs: [song(1, "Airbag", track: 1, disc: 1)], currentSong: nil, loadingState: .ready, seed: ArtworkSeedNone.shared, moreByArtist: others, moreByArtistNames: others.isEmpty ? [] : ["Radiohead"], classical: false)
    }

    @Test func moreByShelfShowsTheArtistsOtherAlbums() throws {
        let sut = AlbumDetailContent(state: moreByState([album(year: 2000)]))
        #expect((try? sut.inspect().find(text: "More by Radiohead")) != nil)
        #expect(try sut.inspect().findAll(ViewType.Button.self, where: { (try? $0.accessibilityIdentifier()) == "detailTile.moreBy" }).count == 1)
    }

    @Test func moreByShelfIsHiddenWithoutAlbums() throws {
        let sut = AlbumDetailContent(state: moreByState([]))
        #expect((try? sut.inspect().find(text: "More by Radiohead")) == nil)
    }

    @Test func tappingAMoreByTileOpensItsAlbum() throws {
        var opened: Album?
        let other = album(year: 2000)
        let sut = AlbumDetailContent(state: moreByState([other]), onAlbumTap: { opened = $0 })
        try sut.inspect().find(ViewType.Button.self, where: { (try? $0.accessibilityIdentifier()) == "detailTile.moreBy" }).tap()
        #expect(opened?.stableId == other.stableId)
    }

    @Test func tappingATrackPlaysFromItsIndex() throws {
        var played: Int?
        let songs = [song(1, "Airbag", track: 1, disc: 1), song(2, "Paranoid Android", track: 2, disc: 1)]
        let state = AlbumDetailUiState(album: album(), songs: songs, currentSong: nil, loadingState: .ready, seed: ArtworkSeedNone.shared, moreByArtist: [], moreByArtistNames: [], classical: false)
        let sut = AlbumDetailContent(state: state, onPlay: { played = $0 })
        try sut.inspect().find(button: "Paranoid Android").tap()
        #expect(played == 1)
    }

    @Test func artistLineGoesToTheArtist() throws {
        var opened = 0
        let songs = [song(1, "Airbag", track: 1, disc: 1)]
        let state = AlbumDetailUiState(album: album(songCount: 1), songs: songs, currentSong: nil, loadingState: .ready, seed: ArtworkSeedNone.shared, moreByArtist: [], moreByArtistNames: [], classical: false)
        let sut = AlbumDetailContent(state: state, onGoToArtist: { opened += 1 })
        try sut.inspect().find(ViewType.Button.self, where: { (try? $0.accessibilityLabel().string()) == "Go to Radiohead" }).tap()
        #expect(opened == 1)
        // The eyebrow no longer repeats the artist.
        #expect((try? sut.inspect().find(ViewType.Text.self, where: { try $0.string().hasPrefix("1997 · 1 song") })) != nil)
    }

    private func artistButton(_ sut: AlbumDetailContent, _ label: String) -> InspectableView<ViewType.Button>? {
        try? sut.inspect().find(ViewType.Button.self, where: { (try? $0.accessibilityLabel().string()) == label })
    }

    @Test func noArtistKeyMeansNoArtistButton() throws {
        let songs = [song(1, "Airbag", track: 1, disc: 1)]
        let state = AlbumDetailUiState(album: album(songCount: 1, artistKey: nil), songs: songs, currentSong: nil, loadingState: .ready, seed: ArtworkSeedNone.shared, moreByArtist: [], moreByArtistNames: [], classical: false)
        let sut = AlbumDetailContent(state: state, onGoToArtist: {})
        #expect(artistButton(sut, "Go to Radiohead") == nil)
        // The artist stays in the eyebrow.
        #expect((try? sut.inspect().find(ViewType.Text.self, where: { try $0.string().hasPrefix("Radiohead · 1997") })) != nil)
    }

    @Test func compilationLinkNamesTheAlbumArtist() throws {
        let songs = [song(1, "Airbag", track: 1, disc: 1)]
        let compilation = album(songCount: 1, albumArtist: "Various Artists", artists: ["A", "B", "C"], artistKey: "various artists")
        let state = AlbumDetailUiState(album: compilation, songs: songs, currentSong: nil, loadingState: .ready, seed: ArtworkSeedNone.shared, moreByArtist: [], moreByArtistNames: [], classical: false)
        let sut = AlbumDetailContent(state: state, onGoToArtist: {})
        #expect(artistButton(sut, "Go to Various Artists") != nil)
        #expect(artistButton(sut, "Go to A, B, C") == nil)
    }

    @Test func linkMapping() {
        let normal = AlbumArtistLink(name: "Radiohead", key: "radiohead")
        #expect(normal?.name == "Radiohead")
        #expect(normal?.route == .albumArtist(albumArtistKey: "radiohead"))
        let compilation = AlbumArtistLink(album(albumArtist: "Various Artists", artists: ["A", "B"], artistKey: "various artists"))
        #expect(compilation?.name == "Various Artists")
        #expect(compilation?.route == .albumArtist(albumArtistKey: "various artists"))
        // An album of several album artists, or one featuring another, opens its primary artist, not their joint key
        let several = AlbumArtistLink(album(albumArtist: "Radiohead, Thom Yorke", artistKey: "radiohead, thom yorke", albumArtistKeys: ["radiohead", "thom yorke"]))
        #expect(several?.route == .albumArtist(albumArtistKey: "radiohead"))
        let featuring = AlbumArtistLink(album(albumArtist: "Radiohead feat. Björk", artistKey: "radiohead feat. björk", albumArtistKeys: ["radiohead"]))
        #expect(featuring?.route == .albumArtist(albumArtistKey: "radiohead"))
        #expect(AlbumArtistLink(name: "Radiohead", key: nil) == nil)
        #expect(AlbumArtistLink(album(artistKey: nil)) == nil)
        #expect(AlbumArtistLink(name: nil, key: "radiohead") == nil)
        #expect(AlbumArtistLink(name: "  ", key: "radiohead") == nil)
        #expect(AlbumArtistLink(album(albumArtist: "")) == nil)
    }

    @Test func moreMenuActsOnTheWholeAlbum() throws {
        var next: String?
        var queued: String?
        var artist = 0
        let songs = [song(1, "Airbag", track: 1, disc: 1)]
        let state = AlbumDetailUiState(album: album(songCount: 1), songs: songs, currentSong: nil, loadingState: .ready, seed: ArtworkSeedNone.shared, moreByArtist: [], moreByArtistNames: [], classical: false)
        let sut = AlbumDetailContent(
            state: state,
            onPlayAlbumNext: { next = $0.name },
            onAddAlbumToQueue: { queued = $0.name },
            onGoToArtist: { artist += 1 }
        )
        let menu = try sut.inspect().find(ViewType.Menu.self)
        try menu.find(button: "Play Next").tap()
        try menu.find(button: "Add to Queue").tap()
        try menu.find(button: "Go to Artist").tap()
        #expect(next == "OK Computer")
        #expect(queued == "OK Computer")
        #expect(artist == 1)
    }

    @Test func moreMenuHidesGoToArtistWithoutAKey() throws {
        let songs = [song(1, "Airbag", track: 1, disc: 1)]
        let state = AlbumDetailUiState(album: album(songCount: 1, artistKey: nil), songs: songs, currentSong: nil, loadingState: .ready, seed: ArtworkSeedNone.shared, moreByArtist: [], moreByArtistNames: [], classical: false)
        let menu = try AlbumDetailContent(state: state, onGoToArtist: {}).inspect().find(ViewType.Menu.self)
        #expect((try? menu.find(button: "Go to Artist")) == nil)
    }

    private func classicalState(currentSongId: Int64? = nil) -> AlbumDetailUiState {
        let work = "Symphony No. 5 in C minor, Op. 67"
        let songs = [
            song(1, "Allegro con brio", track: 1, disc: 1, composer: "Beethoven", work: work, movement: "Allegro con brio", movementNumber: 1, movementTotal: 2),
            song(2, "Andante con moto", track: 2, disc: 1, composer: "Beethoven", work: work, movement: "Andante con moto", movementNumber: 2, movementTotal: 2),
        ]
        let rows = AlbumRowsKt.buildAlbumRows(songs: songs, albumArtist: "Radiohead", currentSongId: currentSongId.map { KotlinLong(value: $0) }, toggled: [], otherRecordings: [:])
        return AlbumDetailUiState(album: album(songCount: 2), songs: songs, currentSong: nil, loadingState: .ready, seed: ArtworkSeedNone.shared, moreByArtist: [], moreByArtistNames: [], classical: true, rows: rows, otherRecordings: [:])
    }

    @Test func classicalAlbumShowsWorkHeaderMovementsAndNoDiscDivider() throws {
        let sut = AlbumDetailContent(state: classicalState(), onOpenComposer: { _ in })
        #expect((try? sut.inspect().find(ViewType.Text.self, where: { try $0.string().hasPrefix("Symphony No. 5") })) != nil)
        #expect((try? sut.inspect().find(text: "Allegro con brio")) != nil)
        #expect((try? sut.inspect().find(text: "Andante con moto")) != nil)
        #expect((try? sut.inspect().find(text: "Disc 1")) == nil)
        // The subtitle counts tracks and drops the artist, which the credits line names.
        #expect((try? sut.inspect().find(ViewType.Text.self, where: { try $0.string().hasPrefix("1997 · 2 tracks") })) != nil)
    }

    @Test func tappingAMovementPlaysFromItsIndexInTheAlbum() throws {
        var played: Int?
        let sut = AlbumDetailContent(state: classicalState(), onPlay: { played = $0 })
        try sut.inspect().find(button: "Andante con moto").tap()
        #expect(played == 1)
    }

    @Test func composerEyebrowOpensTheComposer() throws {
        var opened: String?
        let sut = AlbumDetailContent(state: classicalState(), onOpenComposer: { opened = $0 })
        try sut.inspect().find(ViewType.Button.self, where: { (try? $0.accessibilityLabel().string())?.hasPrefix("Open composer") == true }).tap()
        #expect(opened == "Beethoven")
    }

    @Test func theComposerTapPushesTheComposerRoute() throws {
        let navigator = Navigator()
        let sut = AlbumDetailContent(state: classicalState(), onOpenComposer: { navigator.open(.composer(name: $0)) })
        try sut.inspect().find(ViewType.Button.self, where: { (try? $0.accessibilityLabel().string())?.hasPrefix("Open composer") == true }).tap()
        #expect(navigator.homePath.last == .composer(name: "Beethoven"))
    }

    @Test func classicalAlbumWithoutComposerRouteLeavesTheEyebrowUntappable() throws {
        let sut = AlbumDetailContent(state: classicalState())
        #expect((try? sut.inspect().find(ViewType.Button.self, where: { (try? $0.accessibilityLabel().string())?.hasPrefix("Open composer") == true })) == nil)
        #expect((try? sut.inspect().find(text: "Beethoven")) != nil)
    }

    @Test func shortWorkHeaderPlaysItsMovements() throws {
        var playedWork: [Song] = []
        let sut = AlbumDetailContent(state: classicalState(), onPlayWork: { playedWork = $0 })
        try sut.inspect().find(ViewType.Button.self, where: { (try? $0.accessibilityLabel().string())?.hasPrefix("Play Symphony No. 5") == true }).tap()
        #expect(playedWork.map(\.id) == [1, 2])
    }

    /// A 21-movement work then a one-movement work: the first folds by default.
    private func longWorkState(toggled: Set<Int64> = []) -> AlbumDetailUiState {
        var songs = (1...21).map { i in
            song(Int64(i), "Variation \(i)", track: Int32(i), disc: 1, composer: "Bach", work: "Goldberg Variations", movement: "Variation \(i)", movementNumber: Int32(i), movementTotal: 21)
        }
        songs.append(song(22, "Aria", track: 22, disc: 1, composer: "Bach", work: "Aria da capo", movement: "Aria", movementNumber: 1, movementTotal: 2))
        songs.append(song(23, "Da capo", track: 23, disc: 1, composer: "Bach", work: "Aria da capo", movement: "Da capo", movementNumber: 2, movementTotal: 2))
        let rows = AlbumRowsKt.buildAlbumRows(songs: songs, albumArtist: "Bach", currentSongId: nil, toggled: Set(toggled.map { KotlinLong(value: $0) }), otherRecordings: [:])
        return AlbumDetailUiState(album: album(songCount: 23), songs: songs, currentSong: nil, loadingState: .ready, seed: ArtworkSeedNone.shared, moreByArtist: [], moreByArtistNames: [], classical: true, rows: rows, otherRecordings: [:])
    }

    @Test func longWorkStartsFoldedAndItsHeaderTapTogglesIt() throws {
        var toggled: Int64?
        let sut = AlbumDetailContent(state: longWorkState(), onToggleWork: { toggled = $0 })
        #expect((try? sut.inspect().find(text: "Variation 3")) == nil)
        try sut.inspect().find(ViewType.Button.self, where: { (try? $0.find(text: "Goldberg Variations")) != nil }).tap()
        #expect(toggled == 1)
    }

    @Test func toggledLongWorkShowsItsMovements() throws {
        let sut = AlbumDetailContent(state: longWorkState(toggled: [1]))
        #expect((try? sut.inspect().find(text: "Variation 3")) != nil)
    }

    @Test func classicalShuffleSendsShuffleWorksAndOtherwiseShuffle() {
        #expect(classicalState().shuffleAction is MediaActionShuffleWorks)
        let plain = AlbumDetailUiState(album: album(), songs: [], currentSong: nil, loadingState: .ready, seed: ArtworkSeedNone.shared, moreByArtist: [], moreByArtistNames: [], classical: false)
        #expect(plain.shuffleAction is MediaActionShuffle)
    }

    @Test func heroShuffleButtonRunsOnShuffle() throws {
        var shuffled = false
        let sut = AlbumDetailContent(state: classicalState(), onShuffle: { shuffled = true })
        try sut.inspect().find(button: "Shuffle").tap()
        #expect(shuffled)
    }

    @Test func classicalAlbumWithoutASingleComposerHasNoEyebrow() throws {
        let songs = [
            song(1, "A", track: 1, disc: 1, composer: "Beethoven", work: "Work A", movement: "A", movementNumber: 1, movementTotal: 2),
            song(2, "B", track: 2, disc: 1, composer: "Beethoven", work: "Work A", movement: "B", movementNumber: 2, movementTotal: 2),
            song(3, "C", track: 3, disc: 1, composer: "Mozart", work: "Work B", movement: "C", movementNumber: 1, movementTotal: 2),
            song(4, "D", track: 4, disc: 1, composer: "Mozart", work: "Work B", movement: "D", movementNumber: 2, movementTotal: 2),
        ]
        let rows = AlbumRowsKt.buildAlbumRows(songs: songs, albumArtist: "Radiohead", currentSongId: nil, toggled: [], otherRecordings: [:])
        let state = AlbumDetailUiState(album: album(), songs: songs, currentSong: nil, loadingState: .ready, seed: ArtworkSeedNone.shared, moreByArtist: [], moreByArtistNames: [], classical: true, rows: rows, otherRecordings: [:])
        let sut = AlbumDetailContent(state: state, onGoToArtist: {})
        // "Radiohead" is the credits line; an artist eyebrow would be a second Text.
        #expect(try sut.inspect().findAll(ViewType.Text.self, where: { try $0.string() == "Radiohead" }).count <= 1)
        #expect((try? sut.inspect().find(button: "Go to Radiohead")) == nil)
    }

    @Test func clockDurationShowsHoursFromAnHour() {
        #expect(clockDuration(ms: 245_000) == "4:05")
        #expect(clockDuration(ms: 3_723_000) == "1:02:03")
    }

    @Test func placeholders() throws {
        let empty = AlbumDetailUiState(album: nil, songs: [], currentSong: nil, loadingState: .loading, seed: ArtworkSeedNone.shared, moreByArtist: [], moreByArtistNames: [], classical: false)
        #expect((try? AlbumDetailContent(state: empty).inspect().find(ViewType.ProgressView.self)) != nil)
        let notFound = AlbumDetailUiState(album: nil, songs: [], currentSong: nil, loadingState: .empty, seed: ArtworkSeedNone.shared, moreByArtist: [], moreByArtistNames: [], classical: false)
        #expect((try? AlbumDetailContent(state: notFound).inspect().find(text: "Album Not Found")) != nil)
    }
}
