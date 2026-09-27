import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// `NowPlayingContent`'s content and commands, as plain values in (no Kotlin) per `.claude/rules/ios.md`.
@MainActor
struct NowPlayingViewTests {
    private let queue: [PlayerModel.QueueRow] = [
        .init(id: 1, title: "Paranoid Android", artist: "Radiohead", isCurrent: true),
        .init(id: 2, title: "Hyperballad", artist: "Björk", isCurrent: false),
    ]

    private func makeSut(
        title: String? = "Paranoid Android",
        queue: [PlayerModel.QueueRow]? = nil,
        onPlayPause: @escaping () -> Void = {},
        onNext: @escaping () -> Void = {},
        onPrevious: @escaping () -> Void = {},
        onSelectQueueItem: @escaping (Int) -> Void = { _ in }
    ) -> NowPlayingContent {
        NowPlayingContent(
            title: title,
            artist: title == nil ? nil : "Radiohead",
            album: title == nil ? nil : "OK Computer",
            isPlaying: true,
            positionMs: 90_000,
            durationMs: 386_000,
            queue: queue ?? (title == nil ? [] : self.queue),
            onSeek: { _ in },
            onPlayPause: onPlayPause,
            onNext: onNext,
            onPrevious: onPrevious,
            onSelectQueueItem: onSelectQueueItem
        )
    }

    @Test func showsTitleArtistAndAlbum() throws {
        let sut = makeSut()
        #expect((try? sut.inspect().find(text: "Paranoid Android")) != nil)
        #expect((try? sut.inspect().find(text: "Radiohead · OK Computer")) != nil)
    }

    @Test func showsAFallbackWhenNothingIsPlaying() throws {
        let sut = makeSut(title: nil)
        #expect((try? sut.inspect().find(text: "Nothing is playing")) != nil)
    }

    @Test func showsTheQueueWithEachSong() throws {
        let sut = makeSut()
        #expect((try? sut.inspect().find(text: "Hyperballad")) != nil)
    }

    @Test func tappingPlayPauseTogglesPlayback() throws {
        var toggled = false
        let sut = makeSut(onPlayPause: { toggled = true })
        try sut.inspect().findAll(ViewType.Button.self)[1].tap()
        #expect(toggled)
    }

    @Test func tappingPreviousAndNextForwardToTheModel() throws {
        var wentBack = false
        var wentForward = false
        let sut = makeSut(onNext: { wentForward = true }, onPrevious: { wentBack = true })
        try sut.inspect().findAll(ViewType.Button.self)[0].tap()
        try sut.inspect().findAll(ViewType.Button.self)[2].tap()
        #expect(wentBack)
        #expect(wentForward)
    }

    @Test func tappingAQueueRowSkipsToIt() throws {
        var selected: Int?
        let sut = makeSut(onSelectQueueItem: { selected = $0 })
        try sut.inspect().findAll(ViewType.Button.self)[4].tap()
        #expect(selected == 1)
    }
}
