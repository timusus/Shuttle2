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

    @Test func shapesFollowTheSpec() {
        #expect(S2Shape.artworkRow.cornerRadius == 8)
        #expect(S2Shape.artworkTile.cornerRadius == 16)
        #expect(S2Shape.artworkHero.cornerRadius == 20)
        #expect(S2Shape.artworkPlayer.cornerRadius == 20)
        #expect(S2Shape.card.cornerRadius == 16)
        #expect(S2Shape.artist.cornerRadius == nil)
        #expect(S2Shape.capsule.cornerRadius == nil)
    }

    @Test func anArtistsPictureIsACircleWhateverTheRole() {
        let artist = ArtworkSource(id: "radiohead", isArtist: true) { [] }
        let album = ArtworkSource(id: "ok-computer") { [] }
        #expect(S2Shape.artwork(.artworkRow, for: artist) == .artist)
        #expect(S2Shape.artwork(.artworkHero, for: artist) == .artist)
        #expect(S2Shape.artwork(.artworkTile, for: album) == .artworkTile)
        #expect(S2Shape.artwork(.artworkTile, for: nil) == .artworkTile)

        // A circle leaves the square's corners out; an album tile's continuous corner keeps a point that far in.
        let rect = CGRect(x: 0, y: 0, width: 100, height: 100)
        let nearCorner = CGPoint(x: 12, y: 12)
        #expect(!S2Shape.artist.path(in: rect).contains(nearCorner))
        #expect(S2Shape.artworkRow.path(in: rect).contains(nearCorner))
    }

    @Test func anArtistRowDrawsACircle() throws {
        let sut = MediaRow("Radiohead", artwork: ArtworkSource(id: "radiohead", isArtist: true) { [] })
        #expect(try sut.inspect().find(ViewType.Group.self).clipShape(S2Shape.self) == .artist)
    }

    @Test func accentIsNeutralDarkInLightModeAndLightInDarkMode() {
        let accentLight = Self.luminance(.s2Accent, dark: false)
        let accentDark = Self.luminance(.s2Accent, dark: true)
        #expect(accentLight < 0.15)
        #expect(accentDark > 0.8)
        // Neutral: no hue of its own.
        #expect(Self.chroma(.s2Accent, dark: false) < 0.03)
        #expect(Self.chroma(.s2Accent, dark: true) < 0.03)
        // A label on the accent is the opposite end of the scale.
        #expect(Self.luminance(.s2OnAccent, dark: false) > 0.9)
        #expect(Self.luminance(.s2OnAccent, dark: true) < 0.1)
    }

    @Test func textAndStatusRolesFollowTheScheme() {
        #expect(Self.luminance(.s2TextPrimary, dark: false) < 0.1)
        #expect(Self.luminance(.s2TextPrimary, dark: true) > 0.9)
        #expect(Self.luminance(.s2TextSecondary, dark: false) < Self.luminance(.s2TextSecondary, dark: true))
        #expect(Self.luminance(.s2Surface, dark: false) > 0.9)
        #expect(Self.luminance(.s2Surface, dark: true) < 0.1)
        let error = Self.rgb(.s2Error, dark: false)
        #expect(error.red > error.green && error.red > error.blue)
        let success = Self.rgb(.s2Success, dark: true)
        #expect(success.green > success.red && success.green > success.blue)
    }

    @Test func touchTargetsAreNeverBelowTheMinimum() {
        #expect(TouchTarget.minimum == 44)
        #expect(TouchTarget.side(TouchTarget.disc) == 44)
        #expect(TouchTarget.side(20) == 44)
        #expect(TouchTarget.side(60) == 60)
        #expect(RowHeight.text >= TouchTarget.minimum)
    }

    // MARK: Helpers

    private static func rgb(_ color: UIColor, dark: Bool) -> (red: CGFloat, green: CGFloat, blue: CGFloat) {
        let resolved = color.resolvedColor(with: UITraitCollection(userInterfaceStyle: dark ? .dark : .light))
        var red: CGFloat = 0, green: CGFloat = 0, blue: CGFloat = 0, alpha: CGFloat = 0
        resolved.getRed(&red, green: &green, blue: &blue, alpha: &alpha)
        return (red, green, blue)
    }

    private static func luminance(_ color: UIColor, dark: Bool) -> CGFloat {
        let c = rgb(color, dark: dark)
        return 0.2126 * c.red + 0.7152 * c.green + 0.0722 * c.blue
    }

    private static func chroma(_ color: UIColor, dark: Bool) -> CGFloat {
        let c = rgb(color, dark: dark)
        return max(c.red, c.green, c.blue) - min(c.red, c.green, c.blue)
    }
}
