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
        #expect((try? sut.inspect().find(text: "Radiohead · OK Computer")) != nil)
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
        let repeatButton = try sut.inspect().find(viewWithAccessibilityLabel: "Repeat")
        #expect(try repeatButton.accessibilityValue().string() == "One")
    }

    @Test func theQueueListsEachSongAndSkipsToATappedOne() throws {
        var selected: Int?
        let sut = NowPlayingQueueList(queue: queue, onSelect: { selected = $0 })
        #expect((try? sut.inspect().find(text: "Hyperballad")) != nil)
        try sut.inspect().find(viewWithAccessibilityLabel: "Hyperballad").button().tap()
        #expect(selected == 1)
        #expect((try? sut.inspect().find(viewWithAccessibilityLabel: "Paranoid Android, now playing")) != nil)
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
