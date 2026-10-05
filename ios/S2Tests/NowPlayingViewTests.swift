import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// Now Playing's content and commands, as plain values in (no Kotlin) per `.claude/rules/ios.md`, and how each
/// tier presents it.
@MainActor
struct NowPlayingViewTests {
    private let queue: [NowPlayingQueueRow] = [
        .init(id: 1, title: "Paranoid Android", artist: "Radiohead", isCurrent: true),
        .init(id: 2, title: "Hyperballad", artist: "Björk", isCurrent: false),
    ]

    private func state(
        title: String? = "Paranoid Android",
        artwork: ArtworkSource? = nil,
        shuffleOn: Bool = false,
        repeatMode: NowPlayingRepeat = .off
    ) -> NowPlayingState {
        guard title != nil else { return .idle }
        return NowPlayingState(
            title: title, artist: "Radiohead", album: "OK Computer", artwork: artwork, isPlaying: true,
            positionMs: 90_000, durationMs: 386_000, queue: queue, shuffleOn: shuffleOn, repeatMode: repeatMode
        )
    }

    private func tap(_ label: String, in sut: NowPlayingContent) throws {
        try sut.inspect().find(viewWithAccessibilityLabel: label).button().tap()
    }

    @Test func showsTitleArtistAndAlbum() throws {
        let sut = NowPlayingContent(state: state())
        #expect((try? sut.inspect().find(text: "Paranoid Android")) != nil)
        #expect((try? sut.inspect().find(text: "Radiohead")) != nil)
        #expect((try? sut.inspect().find(text: "OK Computer")) != nil)
    }

    @Test func showsElapsedAndRemainingTimes() throws {
        let sut = NowPlayingContent(state: state())
        #expect((try? sut.inspect().find(text: "1:30")) != nil)
        #expect((try? sut.inspect().find(text: "-4:56")) != nil)
    }

    @Test func showsTheSharedEmptyStateWhenNothingIsPlaying() throws {
        let sut = NowPlayingContent(state: .idle)
        #expect((try? sut.inspect().find(EmptyState<EmptyView>.self)) != nil)
        #expect((try? sut.inspect().find(text: "Nothing Playing")) != nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityLabel: "Play")) == nil)
    }

    @Test func drawsTheCoverWhenTheSongHasOne() throws {
        let sut = NowPlayingContent(state: state(artwork: ArtworkSource(id: 1) { nil }))
        #expect((try? sut.inspect().find(RemoteArtwork<ArtworkPlaceholder>.self)) != nil)
    }

    @Test func drawsThePlaceholderWithoutArtwork() throws {
        let sut = NowPlayingContent(state: state(artwork: nil))
        #expect((try? sut.inspect().find(RemoteArtwork<ArtworkPlaceholder>.self)) == nil)
        #expect((try? sut.inspect().find(ArtworkPlaceholder.self)) != nil)
    }

    @Test func transportButtonsForwardToTheActions() throws {
        var calls: [String] = []
        var actions = PlayerActions()
        actions.playPause = { calls.append("playPause") }
        actions.previous = { calls.append("previous") }
        actions.next = { calls.append("next") }
        actions.toggleShuffle = { calls.append("shuffle") }
        actions.toggleRepeat = { calls.append("repeat") }
        var closed = false
        let sut = NowPlayingContent(state: state(), actions: actions, onClose: { closed = true })

        for label in ["Previous", "Pause", "Next", "Shuffle", "Repeat", "Close"] {
            try tap(label, in: sut)
        }
        #expect(calls == ["previous", "playPause", "next", "shuffle", "repeat"])
        #expect(closed)
    }

    @Test func shuffleAndRepeatReportTheirModes() throws {
        let sut = NowPlayingContent(state: state(shuffleOn: true, repeatMode: .one))
        let shuffle = try sut.inspect().find(viewWithAccessibilityLabel: "Shuffle")
        #expect(try shuffle.accessibilityValue().string() == "On")
        let off = NowPlayingContent(state: state(shuffleOn: false))
        #expect(try off.inspect().find(viewWithAccessibilityLabel: "Shuffle").accessibilityValue().string() == "Off")
    }

    @Test func repeatOffAllAndOneEachReadAndLookDistinct() throws {
        let expected: [(NowPlayingRepeat, String, String)] = [(.off, "Off", "repeat"), (.all, "All", "repeat"), (.one, "One", "repeat.1")]
        for (mode, value, symbol) in expected {
            let sut = NowPlayingContent(state: state(repeatMode: mode))
            let button = try sut.inspect().find(viewWithAccessibilityLabel: "Repeat")
            #expect(try button.accessibilityValue().string() == value)
            #expect(try button.find(ViewType.Image.self).actualImage().name() == symbol)
        }
        #expect(NowPlayingContent.repeatSymbol(.off) == "repeat")
        #expect(NowPlayingContent.repeatSymbol(.all) == "repeat")
        #expect(NowPlayingContent.repeatSymbol(.one) == "repeat.1")
    }

    @Test func theBottomBarHoldsAudioSleepTimerAirPlayAndQueue() throws {
        let sut = NowPlayingContent(state: state())
        let bar = try sut.inspect().find(viewWithAccessibilityIdentifier: "nowPlaying.bottomBar")
        let ids = bar.findAll(where: { view in
            guard let id = try? view.accessibilityIdentifier() else { return false }
            return id.hasPrefix("nowPlaying.") && id != "nowPlaying.bottomBar"
        }).compactMap { try? $0.accessibilityIdentifier() }
        #expect(ids == ["nowPlaying.audio", "nowPlaying.sleepTimer", "nowPlaying.airPlay", "nowPlaying.queue"])
        #expect(ids.count <= 5)
        // Superseded here: the bottom bar's overflow menu (now the top bar's More) and the speed menu.
        #expect((try? bar.find(viewWithAccessibilityLabel: "More")) == nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityLabel: "Playback Speed")) == nil)
    }

    @Test func audioAndSleepTimerReportTheirState() throws {
        var playing = state()
        playing.playbackSpeed = 1.5
        playing.sleepTimerActive = true
        let sut = NowPlayingContent(state: playing)
        let audio = try sut.inspect().find(viewWithAccessibilityLabel: "Audio")
        #expect(try audio.accessibilityValue().string() == "Speed " + NowPlayingAudioSheet.format(1.5))
        #expect((try? audio.button()) != nil)
        let sleepTimer = try sut.inspect().find(viewWithAccessibilityLabel: "Sleep Timer")
        #expect(try sleepTimer.accessibilityValue().string() == "On")
        // Its glyph is hidden, so it doesn't surface as a second "Snooze" element (#650).
        #expect(try sleepTimer.find(ViewType.Image.self).accessibilityHidden())
    }

    @Test func theAudioSheetShowsTheSpeedAndSetsAPreset() throws {
        var set: [Float] = []
        let sut = NowPlayingAudioSheet(speed: 1.2) { set.append($0) }
        #expect((try? sut.inspect().find(text: "1.2×")) != nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityLabel: "1.2× speed")) != nil)
        let other = try sut.inspect().find(viewWithAccessibilityLabel: "2× speed")
        try other.button().tap()
        #expect(set == [2])
    }

    @Test func theAudioSheetPushesEqualizerAndPlaybackSettings() throws {
        let sut = NowPlayingAudioSheet(speed: 1) { _ in }
        #expect((try? sut.inspect().find(ViewType.NavigationLink.self)) != nil)
        #expect((try? sut.inspect().find(text: "Equalizer & Playback Settings")) != nil)
    }

    @Test func speedAndTimerSnapToTheirSteps() {
        #expect(abs(NowPlayingAudioSheet.snap(1.26) - 1.3) < 0.001)
        #expect(NowPlayingAudioSheet.snap(0.1) == 0.5)
        #expect(NowPlayingAudioSheet.snap(9) == 2)
        #expect(NowPlayingAudioSheet.format(1) == "1×")
        #expect(NowPlayingSleepTimerSheet.remainingText(ms: 754_000) == "12m 34s")
        #expect(NowPlayingSleepTimerSheet.remainingText(ms: 5_000) == "5s")
    }

    @Test func theSleepTimerSheetStartsOrStops() throws {
        var started: [(Int, Bool)] = []
        let off = NowPlayingSleepTimerSheet(isActive: false, playToEnd: false, startTimer: { started.append(($0, $1)) }, stopTimer: {})
        try off.inspect().find(viewWithAccessibilityIdentifier: "sleepTimer.start").button().tap()
        try off.inspect().find(viewWithAccessibilityIdentifier: "sleepTimer.endOfTrack").button().tap()
        #expect(started.map(\.0) == [30, 0])
        #expect(started.map(\.1) == [false, true])

        var stopped = 0
        let on = NowPlayingSleepTimerSheet(isActive: true, playToEnd: false, startTimer: { _, _ in }, stopTimer: { stopped += 1 })
        #expect((try? on.inspect().find(viewWithAccessibilityIdentifier: "sleepTimer.start")) == nil)
        try on.inspect().find(viewWithAccessibilityIdentifier: "sleepTimer.stop").button().tap()
        #expect(stopped == 1)
    }

    @Test func tappingTheArtistOrAlbumOpensIt() throws {
        var sent: [NowPlayingSongAction] = []
        var actions = PlayerActions()
        actions.songAction = { sent.append($0) }
        var playing = state()
        playing.songActions = NowPlayingSongAction.allCases
        let sut = NowPlayingContent(state: playing, actions: actions)
        try sut.inspect().find(viewWithAccessibilityIdentifier: "nowPlaying.artist").button().tap()
        try sut.inspect().find(viewWithAccessibilityIdentifier: "nowPlaying.album").button().tap()
        #expect(sent == [.goToArtist, .goToAlbum])
    }

    @Test func theArtistAndAlbumAreTextWhenTheSongCannotOpenThem() throws {
        var playing = state()
        playing.songActions = [.addToPlaylist]
        let sut = NowPlayingContent(state: playing)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "nowPlaying.artist").button()) == nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "nowPlaying.album").button()) == nil)
        #expect((try? sut.inspect().find(text: "Radiohead")) != nil)
    }

    @Test func theFavouriteButtonReportsAndTogglesTheSong() throws {
        var toggled = 0
        var actions = PlayerActions()
        actions.toggleFavourite = { toggled += 1 }
        var favourite = state()
        favourite.isFavourite = true
        let sut = NowPlayingContent(state: favourite, actions: actions)

        let button = try sut.inspect().find(viewWithAccessibilityLabel: "Favorite")
        #expect(try button.accessibilityValue().string() == "On")
        try button.button().tap()
        #expect(toggled == 1)
        #expect((try? NowPlayingContent(state: state()).inspect().find(viewWithAccessibilityLabel: "Favorite")
            .accessibilityValue().string()) == "Off")
    }

    @Test func theSongMenuOffersTheSongsActionsAndForwardsThem() throws {
        var sent: [NowPlayingSongAction] = []
        var choices: [PlaylistChoice] = []
        var newPlaylist = 0
        let menu = NowPlayingSongMenu(
            songActions: NowPlayingSongAction.allCases,
            playlists: [.init(id: 7, name: "Road Trip")],
            onAction: { sent.append($0) },
            onNewPlaylist: { newPlaylist += 1 },
            onAddToPlaylist: { choices.append($0) }
        )

        try menu.inspect().find(button: "Go to Album").tap()
        try menu.inspect().find(button: "Go to Artist").tap()
        try menu.inspect().find(button: "Exclude").tap()
        try menu.inspect().find(button: "Favorites").tap()
        try menu.inspect().find(button: "Road Trip").tap()
        try menu.inspect().find(button: "New Playlist…").tap()
        #expect(sent == [.goToAlbum, .goToArtist, .exclude])
        #expect(choices == [.favourites, .playlist(id: 7)])
        #expect(newPlaylist == 1)
    }

    @Test func theSongMenuLeavesOutWhatTheSongDoesNotOffer() throws {
        let menu = NowPlayingSongMenu(
            songActions: [.addToPlaylist], playlists: [], onAction: { _ in }, onNewPlaylist: {}, onAddToPlaylist: { _ in }
        )
        #expect((try? menu.inspect().find(button: "Go to Album")) == nil)
        #expect((try? menu.inspect().find(button: "Exclude")) == nil)
        #expect((try? menu.inspect().find(button: "Favorites")) != nil)
    }

    @Test func theFavouriteSitsInTheTopBarAndIsGoneWhenNothingPlays() throws {
        #expect((try? NowPlayingContent(state: state()).inspect().find(viewWithAccessibilityIdentifier: "nowPlaying.favourite")) != nil)
        #expect((try? NowPlayingContent(state: .idle).inspect().find(viewWithAccessibilityIdentifier: "nowPlaying.favourite")) == nil)
    }

    @Test func theMoreMenuSitsInTheTopBarAndIsGoneWhenNothingPlays() throws {
        #expect((try? NowPlayingContent(state: state()).inspect().find(viewWithAccessibilityIdentifier: "nowPlaying.more")) != nil)
        #expect((try? NowPlayingContent(state: .idle).inspect().find(viewWithAccessibilityIdentifier: "nowPlaying.more")) == nil)
    }

    @Test func showsTheAirPlayRoutePicker() throws {
        let sut = NowPlayingContent(state: state())
        #expect((try? sut.inspect().find(AirPlayButton.self)) != nil)
    }

    @Test func showsANoticeWithItsAction() throws {
        var undone = false
        let notice = PlayerNotice(message: "Removed from queue", actionTitle: "Undo", action: { undone = true })
        let sut = NowPlayingContent(state: state(), notice: .constant(notice))
        #expect((try? sut.inspect().find(text: "Removed from queue")) != nil)
        try sut.inspect().find(button: "Undo").tap()
        #expect(undone)
    }

    @Test func theQueueListsEachSongAndSkipsToATappedOne() throws {
        var selected: Int64?
        var actions = PlayerActions()
        actions.selectQueueItem = { selected = $0 }
        let sut = NowPlayingQueueList(queue: queue, actions: actions)
        #expect((try? sut.inspect().find(text: "Hyperballad")) != nil)
        try sut.inspect().find(viewWithAccessibilityLabel: "Hyperballad, Björk").button().tap()
        #expect(selected == 2)
        #expect((try? sut.inspect().find(viewWithAccessibilityLabel: "Paranoid Android, Radiohead, now playing")) != nil)
    }

    @Test func theQueueHeadsNowPlayingUpNextAndPlayedAlike() throws {
        let withHistory = [NowPlayingQueueRow(id: 0, title: "Airbag", artist: "Radiohead", isCurrent: false)] + queue
        let sut = NowPlayingQueueList(queue: withHistory, isPlaying: true)
        let headers = try sut.inspect().findAll(SectionHeader.self).map { try $0.find(ViewType.Text.self).string() }
        #expect(headers == ["Now Playing", "Up Next", "Played"])
        // The playing song is a row like the rest, carrying the playing indicator.
        let current = try sut.inspect().find(viewWithAccessibilityIdentifier: "queue.nowPlaying")
        #expect((try? current.find(MediaRow<EmptyView>.self)) != nil)
    }

    @Test func theQueueRowsShowTheirDurationsAndUpNextItsSummary() throws {
        let timed = [
            NowPlayingQueueRow(id: 1, title: "Now", artist: nil, isCurrent: true, durationMs: 200_000),
            NowPlayingQueueRow(id: 2, title: "Next", artist: nil, isCurrent: false, durationMs: 125_000),
            NowPlayingQueueRow(id: 3, title: "Unknown Length", artist: nil, isCurrent: false),
        ]
        let sut = NowPlayingQueueList(queue: timed)
        #expect((try? sut.inspect().find(text: "3:20")) != nil)
        #expect((try? sut.inspect().find(text: "2:05")) != nil)
        #expect((try? sut.inspect().find(text: "0:00")) == nil)
        // An unknown length leaves the total out rather than undercounting it.
        let summary = try #require(NowPlayingQueueList.upNextSummary(Array(timed.dropFirst())))
        #expect(summary == "2 songs")
        let subtitles = try sut.inspect().findAll(SectionHeader.self).map { try $0.actualView().subtitle }
        #expect(subtitles == [nil, summary])
    }

    @Test func upNextSummaryCountsSongsAndTotalsTheirTime() {
        func rows(_ durations: [Int]) -> [NowPlayingQueueRow] {
            durations.enumerated().map { .init(id: Int64($0), title: "S", artist: nil, isCurrent: false, durationMs: $1) }
        }
        let en = Locale(identifier: "en_US")
        func time(_ durations: [Int]) -> String? {
            NowPlayingQueueList.remainingTime(rows(durations), locale: en)
        }
        func formatted(_ ms: Int64) -> String {
            Duration.milliseconds(ms).formatted(.units(allowed: [.hours, .minutes], width: .abbreviated).locale(en))
        }
        #expect(NowPlayingQueueList.upNextSummary([]) == nil)
        #expect(NowPlayingQueueList.upNextSummary(rows([240_000]), locale: en) == "1 song · \(formatted(240_000))")
        #expect(time([2_400_000, 1_920_000]) == formatted(4_320_000))
        #expect(time([2_580_000]) == formatted(2_580_000))
        // Sub-minute totals and any unknown length leave the time out.
        #expect(NowPlayingQueueList.upNextSummary(rows([0, 0])) == "2 songs")
        #expect(time([30_000]) == nil)
        #expect(time([240_000, 0]) == nil)
    }

    @Test func aQueueRowsSpokenLabelIncludesItsArtistAndLength() {
        let en = Locale(identifier: "en_US")
        let playing = NowPlayingQueueRow(id: 1, title: "Airbag", artist: "Radiohead", isCurrent: true, durationMs: 200_000)
        #expect(NowPlayingQueueList.accessibilityLabel(for: playing, locale: en) == "Airbag, Radiohead, now playing, 3 minutes, 20 seconds")
        let untimed = NowPlayingQueueRow(id: 2, title: "Lucky", artist: nil, isCurrent: false)
        #expect(NowPlayingQueueList.accessibilityLabel(for: untimed, locale: en) == "Lucky")
    }

    @Test func theQualityLineIsSpokenInWords() {
        let en = Locale(identifier: "en_US")
        #expect(AudioQuality(codec: "flac", bitDepth: 24, sampleRate: 96_000).spokenBadge(locale: en) == "FLAC, 24 bit, 96 kilohertz")
        #expect(AudioQuality(mimeType: "audio/mpeg", bitRate: 320).spokenBadge(locale: en) == "MP3, 320 kilobits per second")
        #expect(AudioQuality().spokenBadge(locale: en) == nil)
    }

    @Test func aTranscodesQualityLineIsWhatTheServerSends() {
        let en = Locale(identifier: "en_US")
        #expect(AudioQuality(delivered: DeliveredFormat(codec: "MP3", bitrateKbps: KotlinInt(int: 128))).badge(locale: en) == "MP3 · 128 kbps")
        #expect(AudioQuality(delivered: DeliveredFormat(codec: "OPUS", bitrateKbps: nil)).badge(locale: en) == "OPUS")
    }

    @Test func theQualityLineShowsTheFormatAndHidesWhenUnknown() throws {
        var playing = state()
        playing.quality = AudioQuality(codec: "flac", bitDepth: 24, sampleRate: 96_000)
        let line = try NowPlayingContent(state: playing).inspect().find(viewWithAccessibilityIdentifier: "nowPlaying.quality")
        #expect(try line.text().string() == "FLAC · 24/96 kHz")

        playing.quality = AudioQuality(mimeType: "audio/mpeg", bitRate: 320)
        #expect(playing.qualityBadge == "MP3 · 320 kbps")

        for unknown in [nil, AudioQuality(), AudioQuality(bitDepth: 24, sampleRate: 96_000)] {
            playing.quality = unknown
            #expect(playing.qualityBadge == nil)
            let sut = NowPlayingContent(state: playing)
            #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "nowPlaying.quality")) == nil)
        }
    }

    @Test func swipingARowRemovesItByUid() throws {
        var removed: [Int64] = []
        var actions = PlayerActions()
        actions.removeQueueItem = { removed.append($0) }
        let sut = NowPlayingQueueList(queue: queue, actions: actions)
        // Up Next starts after the playing song: its first row is the queue's second.
        try sut.inspect().find(ViewType.ForEach.self).callOnDelete(IndexSet(integer: 0))
        #expect(removed == [2])
    }

    @Test func draggingARowMovesItAfterItsNewNeighbour() throws {
        var moves: [(Int64, Int64?)] = []
        var actions = PlayerActions()
        actions.moveQueueItem = { moves.append(($0, $1)) }
        let longer = queue + [.init(id: 3, title: "Airbag", artist: "Radiohead", isCurrent: false)]
        let sut = NowPlayingQueueList(queue: longer, actions: actions)
        // Up Next's offsets: dragging its second row to its top puts it straight after the playing song.
        try sut.inspect().find(ViewType.ForEach.self).callOnMove(IndexSet(integer: 1), 0)
        #expect(moves.count == 1)
        #expect(moves.first?.0 == 3)
        #expect(moves.first?.1 == 1)
    }

    @Test func theQueuesContextMenuPlaysNextAndRemoves() throws {
        var next: [Int64] = []
        var removed: [Int64] = []
        var excluded: [Int64] = []
        let menu = NowPlayingQueueRowMenu(
            item: queue[1], playNext: { next.append($0) }, remove: { removed.append($0) }, exclude: { excluded.append($0) }
        )
        try menu.inspect().find(button: "Play Next").tap()
        try menu.inspect().find(button: "Remove from Queue").tap()
        try menu.inspect().find(button: "Exclude").tap()
        #expect(next == [2])
        #expect(removed == [2])
        // Exclude (#650), destructive as in the song menu.
        #expect(excluded == [2])
        #expect(try menu.inspect().find(button: "Exclude").role() == .destructive)
        // The playing song can't be played next, but can be excluded.
        let current = NowPlayingQueueRowMenu(item: queue[0], playNext: { _ in }, remove: { _ in }, exclude: { _ in })
        #expect((try? current.inspect().find(button: "Play Next")) == nil)
        #expect((try? current.inspect().find(button: "Remove from Queue")) != nil)
        #expect((try? current.inspect().find(button: "Exclude")) != nil)
    }

    @Test func anEmptyQueueSaysSo() throws {
        let sut = NowPlayingQueueList(queue: [])
        #expect((try? sut.inspect().find(text: "Queue Empty")) != nil)
    }

    @Test func moveReportsTheMovedUidAndTheOneItNowFollows() throws {
        let rows: [NowPlayingQueueRow] = (1...4).map { .init(id: Int64($0), title: "\($0)", artist: nil, isCurrent: $0 == 1) }
        // Row 4 to the top.
        let toTop = try #require(NowPlayingQueueList.move(rows, from: [3], to: 0))
        #expect(toTop.rows.map(\.id) == [4, 1, 2, 3])
        #expect(toTop.uid == 4)
        #expect(toTop.afterUid == nil)
        // Row 1 to the end (List's destination is the offset before the move).
        let toEnd = try #require(NowPlayingQueueList.move(rows, from: [0], to: 4))
        #expect(toEnd.rows.map(\.id) == [2, 3, 4, 1])
        #expect(toEnd.uid == 1)
        #expect(toEnd.afterUid == 4)
        // Row 2 down one.
        let down = try #require(NowPlayingQueueList.move(rows, from: [1], to: 3))
        #expect(down.rows.map(\.id) == [1, 3, 2, 4])
        #expect(down.afterUid == 3)
        // Onto itself: nothing to do.
        #expect(NowPlayingQueueList.move(rows, from: [1], to: 1) == nil)
        #expect(NowPlayingQueueList.move(rows, from: [1], to: 2) == nil)
    }

    @Test func songActionsKeepOnlyThoseIOSCanRun() {
        #expect(NowPlayingSongAction(.addToPlaylist) == .addToPlaylist)
        #expect(NowPlayingSongAction(.goToAlbum) == .goToAlbum)
        #expect(NowPlayingSongAction(.goToArtist) == .goToArtist)
        #expect(NowPlayingSongAction(.exclude) == .exclude)
        #expect(NowPlayingSongAction(.editTags) == nil)
        #expect(NowPlayingSongAction(.songInfo) == .songInfo)
        #expect(NowPlayingSongAction(.share) == nil)
    }

    @Test func aNewPlaylistNeedsANonBlankName() {
        #expect(PlaylistChoice.trimmedName("  Road Trip ") == "Road Trip")
        #expect(PlaylistChoice.trimmedName("   ") == nil)
    }

    @Test func scrubberReadsElapsedOfTotalToVoiceOver() throws {
        let sut = NowPlayingScrubber(positionMs: 90_000, durationMs: 386_000) { _ in }
        let slider = try sut.inspect().find(viewWithAccessibilityLabel: "Playback position")
        #expect(try slider.accessibilityValue().string() == "1:30 of 6:26")
    }

    // ViewInspector can't invoke accessibility actions on iOS 16+, so the adjustable action's arithmetic is
    // tested directly.
    @Test func voiceOverAdjustSeeksByAStepClampedToTheSong() {
        #expect(NowPlayingScrubber.adjusted(positionMs: 90_000, durationMs: 386_000, .increment) == 105_000)
        #expect(NowPlayingScrubber.adjusted(positionMs: 90_000, durationMs: 386_000, .decrement) == 75_000)
        #expect(NowPlayingScrubber.adjusted(positionMs: 380_000, durationMs: 386_000, .increment) == 386_000)
        #expect(NowPlayingScrubber.adjusted(positionMs: 5_000, durationMs: 386_000, .decrement) == 0)
    }

    @Test func compactPresentsFullScreenAndOtherTiersAFormSheet() {
        #expect(NowPlayingPresentationStyle.resolve(for: .compact) == .fullScreenCover)
        #expect(NowPlayingPresentationStyle.resolve(for: .regular) == .formSheet)
        #expect(NowPlayingPresentationStyle.resolve(for: .wide) == .formSheet)
    }

    @Test func subSheetsAreSheetsInCompactAndPopoversOtherwise() {
        #expect(PlayerSubSheetStyle.resolve(for: .compact) == .sheet)
        #expect(PlayerSubSheetStyle.resolve(for: .regular) == .popover)
        #expect(PlayerSubSheetStyle.resolve(for: .wide) == .popover)
    }
}
