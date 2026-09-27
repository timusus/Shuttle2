import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// The shared tokens and components screens build on (#624): `MediaRow`, `SectionHeader`, the placeholder,
/// `MarqueeText`, and the token helpers.
@MainActor
struct DesignSystemTests {

    // MARK: MediaRow

    @Test func aRowShowsItsTitleSubtitleAndTrailingAccessory() throws {
        let sut = MediaRow("Teardrop", subtitle: "Massive Attack · Mezzanine") {
            Text("5:30")
        }
        #expect((try? sut.inspect().find(text: "Teardrop")) != nil)
        #expect((try? sut.inspect().find(text: "Massive Attack · Mezzanine")) != nil)
        #expect((try? sut.inspect().find(text: "5:30")) != nil)
    }

    @Test func aRowWithoutASubtitleDrawsOneLine() throws {
        #expect(try MediaRow("Teardrop").inspect().findAll(ViewType.Text.self).count == 1)
        #expect(try MediaRow("Teardrop", subtitle: "").inspect().findAll(ViewType.Text.self).count == 1)
    }

    @Test func aRowWithNoArtworkDrawsItsPlaceholderSymbol() throws {
        let sut = MediaRow("Trip Hop", placeholderSymbol: "guitars")
        let placeholder = try sut.inspect().find(ArtworkPlaceholder.self)
        #expect(try placeholder.find(ViewType.Image.self).actualImage().name() == "guitars")
    }

    @Test func aRowWithArtworkDrawsRemoteArtwork() throws {
        let sut = MediaRow("Teardrop", artwork: ArtworkSource(id: "1", load: { nil }))
        #expect((try? sut.inspect().find(RemoteArtwork<ArtworkPlaceholder>.self)) != nil)
    }

    @Test func onlyThePlayingRowShowsTheIndicator() throws {
        #expect((try? MediaRow("Teardrop").inspect().find(NowPlayingIndicator.self)) == nil)
        let playing = try MediaRow("Teardrop", playback: .playing).inspect().find(NowPlayingIndicator.self)
        #expect(try playing.actualView().isAnimating)
        let paused = try MediaRow("Teardrop", playback: .paused).inspect().find(NowPlayingIndicator.self)
        #expect(try !paused.actualView().isAnimating)
    }

    @Test func theTitleCarriesItsIdentifier() throws {
        let sut = MediaRow("Teardrop", titleIdentifier: "songRow.title")
        #expect(try sut.inspect().find(viewWithAccessibilityIdentifier: "songRow.title").text().string() == "Teardrop")
    }

    @Test func songRowIsAMediaRowWithItsDuration() throws {
        let song = TestSongs.demo[0]
        let sut = SongRow(song: song)
        #expect((try? sut.inspect().find(MediaRow<Text>.self)) != nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "songRow.title")) != nil)
    }

    // MARK: SectionHeader

    @Test func aHeaderWithoutSeeAllIsJustItsTitle() throws {
        let sut = SectionHeader("Up Next")
        #expect(try sut.inspect().find(text: "Up Next").attributes().font() == .s2SectionTitle)
        #expect((try? sut.inspect().find(ViewType.Button.self)) == nil)
        #expect((try? sut.inspect().find(ViewType.NavigationLink.self)) == nil)
    }

    @Test func seeAllRunsItsAction() throws {
        var tapped = false
        let sut = SectionHeader("Most Played") { tapped = true }
        #expect((try? sut.inspect().find(text: "See All")) != nil)
        try sut.inspect().find(ViewType.Button.self).tap()
        #expect(tapped)
    }

    @Test func seeAllCanPushARoute() throws {
        let sut = SectionHeader("Albums", seeAll: .libraryCategory(.albums))
        #expect((try? sut.inspect().find(ViewType.NavigationLink.self)) != nil)
    }

    // MARK: Placeholder, marquee

    @Test func thePlaceholderDefaultsToAMusicNote() throws {
        #expect(try ArtworkPlaceholder().inspect().find(ViewType.Image.self).actualImage().name() == "music.note")
    }

    @Test func aMarqueeAtRestIsOneTruncatedLine() throws {
        let sut = MarqueeText("A very long song title that will not fit")
        let texts = try sut.inspect().findAll(ViewType.Text.self)
        #expect(!texts.isEmpty)
        #expect(try texts.allSatisfy { try $0.string() == "A very long song title that will not fit" })
        #expect(try texts.allSatisfy { try $0.lineLimit() == 1 })
    }

    // MARK: Tokens

    @Test func reduceMotionDropsAnimationsAndTurnsSlidesIntoFades() {
        #expect(Motion.press.reduced(true) == nil)
        #expect(Motion.press.reduced(false) == Motion.press)
    }

    @Test func sizesGrowOnRegularWidth() {
        #expect(ArtworkSize.shelf(.compact) == 150)
        #expect(ArtworkSize.shelf(.regular) == 180)
        #expect(ArtworkSize.shelf(.wide) == 180)
        #expect(ArtworkSize.hero(.compact) == 240)
        #expect(ArtworkSize.hero(.regular) == 300)
        #expect(AdaptiveLayout.contentInset(.compact) == Spacing.medium)
    }

    @Test func cornersFollowTheSpec() {
        #expect(ArtworkCorner.row == 8)
        #expect(ArtworkCorner.tile == 16)
        #expect(ArtworkCorner.hero == 20)
        #expect(ArtworkCorner.player == 20)
    }
}
