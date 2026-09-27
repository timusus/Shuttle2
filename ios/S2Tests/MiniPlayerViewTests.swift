import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// `MiniPlayerBar`'s content and commands, as plain values in (no Kotlin) per `.claude/rules/ios.md`.
@MainActor
struct MiniPlayerViewTests {
    @Test func showsTheCurrentSongAndArtist() throws {
        let sut = MiniPlayerBar(title: "Paranoid Android", artist: "Radiohead", isPlaying: true, onTap: {}, onPlayPause: {}, onNext: {})
        #expect((try? sut.inspect().find(text: "Paranoid Android")) != nil)
        #expect((try? sut.inspect().find(text: "Radiohead")) != nil)
    }

    @Test func showsAPlaceholderWhenNothingIsPlaying() throws {
        let sut = MiniPlayerBar(title: nil, artist: nil, isPlaying: false, onTap: {}, onPlayPause: {}, onNext: {})
        #expect((try? sut.inspect().find(text: "Not Playing")) != nil)
    }

    @Test func tappingTheSongOpensNowPlaying() throws {
        var tapped = false
        let sut = MiniPlayerBar(title: "Paranoid Android", artist: "Radiohead", isPlaying: true, onTap: { tapped = true }, onPlayPause: {}, onNext: {})
        try sut.inspect().findAll(ViewType.Button.self)[0].tap()
        #expect(tapped)
    }

    @Test func tappingPlayPauseTogglesPlayback() throws {
        var toggled = false
        let sut = MiniPlayerBar(title: "Paranoid Android", artist: "Radiohead", isPlaying: true, onTap: {}, onPlayPause: { toggled = true }, onNext: {})
        try sut.inspect().findAll(ViewType.Button.self)[1].tap()
        #expect(toggled)
    }

    @Test func tappingNextAdvancesTheQueue() throws {
        var skipped = false
        let sut = MiniPlayerBar(title: "Paranoid Android", artist: "Radiohead", isPlaying: true, onTap: {}, onPlayPause: {}, onNext: { skipped = true })
        try sut.inspect().findAll(ViewType.Button.self)[2].tap()
        #expect(skipped)
    }
}
