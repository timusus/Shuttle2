import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// `MiniPlayerBar`'s content and commands, as plain values in (no Kotlin) per `.claude/rules/ios.md`.
@MainActor
struct MiniPlayerViewTests {
    private let artwork = ArtworkSource(id: 1) { nil }

    private func makeSut(
        title: String = "Paranoid Android",
        artwork: ArtworkSource? = nil,
        onTap: @escaping () -> Void = {},
        onPlayPause: @escaping () -> Void = {},
        onNext: @escaping () -> Void = {}
    ) -> MiniPlayerBar {
        MiniPlayerBar(
            title: title, artist: "Radiohead", artwork: artwork, isPlaying: true,
            onTap: onTap, onPlayPause: onPlayPause, onNext: onNext
        )
    }

    /// Under content that doesn't fill the screen (a spinner, an empty state), the bar still docks at the bottom of the
    /// safe area rather than just under the content, mid-screen (#623).
    @Test func theBarDocksAtTheBottomUnderContentThatDoesNotFill() async throws {
        let probe = FrameProbe()
        let view = ProgressView().dockedAtBottom {
            Color.red.frame(height: 50)
                .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { probe.frame = $0 }
        }
        let scene = try #require(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let window = UIWindow(windowScene: scene)
        window.frame = CGRect(x: 0, y: 0, width: 390, height: 800)
        window.rootViewController = UIHostingController(rootView: view)
        window.makeKeyAndVisible()
        defer { window.isHidden = true }
        window.layoutIfNeeded()
        for _ in 0..<20 where probe.frame == .zero {
            try await Task.sleep(for: .milliseconds(50))
        }

        let bottom = window.bounds.height - window.safeAreaInsets.bottom
        #expect(abs(probe.frame.maxY - bottom) < 1, "bar ends at \(probe.frame.maxY), the safe area at \(bottom)")
    }

    @Test func showsTheCurrentSongAndArtist() throws {
        let sut = makeSut()
        #expect((try? sut.inspect().find(text: "Paranoid Android")) != nil)
        #expect((try? sut.inspect().find(text: "Radiohead")) != nil)
    }

    @Test func drawsTheSongsCoverWhenItHasOne() throws {
        let sut = makeSut(artwork: artwork)
        #expect((try? sut.inspect().find(RemoteArtwork<ArtworkPlaceholder>.self)) != nil)
    }

    @Test func drawsThePlaceholderTileWhenTheSongHasNoCover() throws {
        let sut = makeSut(artwork: nil)
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

    @Test func theSongButtonSaysWhatItIsAndWhatItDoes() throws {
        let sut = makeSut()
        let button = try sut.inspect().findAll(ViewType.Button.self)[0]
        #expect(try button.accessibilityLabel().string() == "Paranoid Android, Radiohead")
        #expect(try button.accessibilityValue().string() == "Playing")
        #expect(try button.accessibilityHint().string() == "Opens Now Playing")
    }

    @Test func playPauseIsLabelledForWhatItWillDo() throws {
        let sut = makeSut()
        let button = try sut.inspect().findAll(ViewType.Button.self)[1]
        #expect(try button.accessibilityLabel().string() == "Pause")
    }

    @Test func tappingNextAdvancesTheQueue() throws {
        var skipped = false
        let sut = makeSut(onNext: { skipped = true })
        try sut.inspect().findAll(ViewType.Button.self)[2].tap()
        #expect(skipped)
    }
}

@MainActor
private final class FrameProbe {
    var frame: CGRect = .zero
}
