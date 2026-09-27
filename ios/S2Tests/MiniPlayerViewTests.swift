import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// `MiniPlayerBar`'s content and commands, as plain values in (no Kotlin) per `.claude/rules/ios.md`.
@MainActor
struct MiniPlayerViewTests {
    private let artwork = ArtworkSource(id: 1) { nil }

    private func makeSut(
        title: String? = "Paranoid Android",
        artwork: ArtworkSource? = nil,
        onTap: @escaping () -> Void = {},
        onPlayPause: @escaping () -> Void = {},
        onNext: @escaping () -> Void = {}
    ) -> MiniPlayerBar {
        MiniPlayerBar(
            title: title, artist: title == nil ? nil : "Radiohead", artwork: artwork, isPlaying: true,
            onTap: onTap, onPlayPause: onPlayPause, onNext: onNext
        )
    }

    @Test func showsTheCurrentSongAndArtist() throws {
        let sut = makeSut()
        #expect((try? sut.inspect().find(text: "Paranoid Android")) != nil)
        #expect((try? sut.inspect().find(text: "Radiohead")) != nil)
    }

    @Test func showsAPlaceholderWhenNothingIsPlaying() throws {
        let sut = makeSut(title: nil)
        #expect((try? sut.inspect().find(text: "Not Playing")) != nil)
    }

    @Test func drawsTheSongsCoverWhenItHasOne() throws {
        let sut = makeSut(artwork: artwork)
        #expect((try? sut.inspect().find(RemoteArtwork<ArtworkPlaceholder>.self)) != nil)
    }

    @Test func drawsThePlaceholderTileWhenNothingIsQueued() throws {
        let sut = makeSut(title: nil, artwork: nil)
        #expect((try? sut.inspect().find(RemoteArtwork<ArtworkPlaceholder>.self)) == nil)
        #expect((try? sut.inspect().find(ArtworkPlaceholder.self)) != nil)
    }

    @Test func tappingTheSongOpensNowPlaying() throws {
        var tapped = false
        let sut = makeSut(onTap: { tapped = true })
        try sut.inspect().findAll(ViewType.Button.self)[0].tap()
        #expect(tapped)
    }

    @Test func tappingPlayPauseTogglesPlayback() throws {
        var toggled = false
        let sut = makeSut(onPlayPause: { toggled = true })
        try sut.inspect().findAll(ViewType.Button.self)[1].tap()
        #expect(toggled)
    }

    @Test func tappingNextAdvancesTheQueue() throws {
        var skipped = false
        let sut = makeSut(onNext: { skipped = true })
        try sut.inspect().findAll(ViewType.Button.self)[2].tap()
        #expect(skipped)
    }
}
