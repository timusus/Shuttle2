import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// The detail frame's decisions: one column or two, when the bar takes over the hero's title, the Play/Shuffle
/// capsules on a hero, and which tile is a zoom source.
@MainActor
struct DetailSupportTests {
    private func album(_ name: String) -> Album {
        Album(
            name: name, albumArtist: "Radiohead", artists: ["Radiohead"], songCount: 1, duration: 0,
            year: nil, playCount: 0, lastSongPlayed: nil, lastSongCompleted: nil,
            groupKey: AlbumGroupKey(key: name.lowercased(), albumArtistGroupKey: AlbumArtistGroupKey(key: "radiohead"), identity: nil),
            mediaProviders: [.jellyfin], artworkVersion: nil, dateAdded: nil
        )
    }

    // MARK: - Columns

    @Test func compactIsOneColumnAtAnyWidth() {
        #expect(DetailColumns.resolve(tier: .compact, containerWidth: 1200) == .single)
    }

    @Test func regularBelowTheTwoColumnWidthIsOneColumn() {
        let justUnder = AdaptiveLayout.twoColumnMinWidth - 1
        #expect(DetailColumns.resolve(tier: .regular, containerWidth: justUnder) == .single)
        #expect(DetailColumns.resolve(tier: .wide, containerWidth: justUnder) == .single)
    }

    @Test func regularFromTheTwoColumnWidthPutsTheHeroInAColumn() {
        let inset = AdaptiveLayout.contentInset(.regular)
        // At the threshold a third is narrower than the cover and its margins, so the column holds the cover.
        #expect(
            DetailColumns.resolve(tier: .regular, containerWidth: AdaptiveLayout.twoColumnMinWidth)
                == .two(heroColumnWidth: ArtworkSize.heroRegular + inset * 2)
        )
        // Wider, the column is a third of the container.
        #expect(DetailColumns.resolve(tier: .wide, containerWidth: 1_500) == .two(heroColumnWidth: 500))
    }

    @Test func theColumnHeroCoverNeverOutgrowsTheRegularHero() {
        #expect(DetailHeroLayout.column(width: 500).artworkSize == ArtworkSize.heroRegular)
        #expect(DetailHeroLayout.column(width: 200).artworkSize == 200)
        #expect(DetailHeroLayout.stacked.artworkSize == ArtworkSize.hero)
    }

    @Test func theHeroCoverShrinksAtTheAccessibilitySizes() {
        let cap = DetailHeroLayout.accessibilityArtworkSize
        #expect(DetailHeroLayout.stacked.artworkSize(isAccessibilitySize: true) == cap)
        #expect(DetailHeroLayout.column(width: 500).artworkSize(isAccessibilitySize: true) == cap)
        #expect(DetailHeroLayout.column(width: 100).artworkSize(isAccessibilitySize: true) == 100)
        #expect(DetailHeroLayout.stacked.artworkSize(isAccessibilitySize: false) == ArtworkSize.hero)
        #expect(DetailHeroLayout.column(width: 500).artworkSize(isAccessibilitySize: false) == ArtworkSize.heroRegular)
    }

    // MARK: - Full-bleed backdrop

    @Test func theBackdropIsSquareOnCompactWhileTheAppIsTallEnough() {
        // An iPhone 16 in portrait.
        #expect(DetailBleed.backdropHeight(width: 393, containerHeight: 852, tier: .compact) == 393)
    }

    @Test func theBackdropIsFourByThreeOnRegular() {
        #expect(DetailBleed.backdropHeight(width: 600, containerHeight: 1_200, tier: .regular) == 450)
    }

    @Test func theBackdropNeverTakesMoreThanHalfTheApp() {
        // An iPhone in landscape: the first rows still show under it.
        #expect(DetailBleed.backdropHeight(width: 852, containerHeight: 393, tier: .compact) == 196.5)
        // Nothing measured yet.
        #expect(DetailBleed.backdropHeight(width: 0, containerHeight: 0, tier: .compact) == 0)
    }

    // MARK: - The bar's title

    @Test func theBarTakesTheTitleOnlyOnceTheHeroTitleIsUnderIt() {
        // A short hero whose title sits just below the bar: still visible, so the bar stays empty.
        #expect(DetailTitleProbe.isVisible(titleMaxY: 120, barBottom: 110))
        // Its bottom edge at or above the bar's: under the bar, so the bar names the screen.
        #expect(!DetailTitleProbe.isVisible(titleMaxY: 110, barBottom: 110))
        #expect(!DetailTitleProbe.isVisible(titleMaxY: 40, barBottom: 110))
    }

    // MARK: - The hero's palette (#744)

    /// Navy, gold, pale beige, a pastel pink, a yellow, a near-black blue, a maroon and a pure green.
    nonisolated static let coverColours: [UInt32] = [0x15_34_6C, 0xD4_A0_17, 0xE8_D9_B5, 0xFF_E0_F0, 0xFA_E8_5A, 0x0A_0A_20, 0x5A_10_10, 0x00_FF_00]
    /// `s2TextSecondary` in the light scheme.
    static let secondaryText = ContrastSafeTint.RGB(hex: 0x66_66_6B)

    @Test(arguments: coverColours)
    func theDarkHeroIsUnchanged(hex: UInt32) {
        let palette = DetailPalette(extracted: .init(hex: hex), secondaryText: Self.secondaryText, isDarkScheme: true)
        #expect(palette == .inheriting(isDarkScheme: true))
        #expect(palette.wash == nil && palette.tint == nil && palette.ink == nil)
        #expect(palette.washOpacity == 0.22 && palette.tonalOpacity == 0.26)
    }

    @Test func aLightHeroWithNoCoverColourWashesInTheAccent() {
        let palette = DetailPalette(extracted: nil, secondaryText: Self.secondaryText, isDarkScheme: false)
        #expect(palette.wash == nil && palette.tint == nil)
        #expect(palette.washOpacity == 0.22 && palette.tonalOpacity == 0.16)
    }

    @Test(arguments: coverColours)
    func theLightHeroWashKeepsTheCoversHueAndEveryLabelClearsAA(hex: UInt32) throws {
        typealias RGB = ContrastSafeTint.RGB
        let cover = RGB(hex: hex)
        let palette = DetailPalette(extracted: cover, secondaryText: Self.secondaryText, isDarkScheme: false)
        let wash = try #require(palette.wash)
        let tint = try #require(palette.tint)
        let ink = try #require(palette.ink)

        // The cover's hue, at a tone with some colour in it.
        let hue = ContrastSafeTint.HSB(cover).hue
        let drift = abs(hue - ContrastSafeTint.HSB(wash).hue)
        #expect(min(drift, 360 - drift) < 2)
        #expect(ContrastSafeTint.HSB(wash).saturation >= DetailPalette.Tone.lightWashSaturation.lowerBound - 0.001)

        // Captions on the top of the wash, the tint on the Shuffle capsule over it, and the ink on the Play fill.
        let washTop = ContrastSafeTint.wash(of: wash, over: DetailPalette.lightPage, opacity: palette.washOpacity)
        let capsule = ContrastSafeTint.wash(of: wash, over: washTop, opacity: palette.tonalOpacity)
        #expect(ContrastSafeTint.contrastRatio(Self.secondaryText, washTop) >= ContrastSafeTint.minimumContrast)
        #expect(ContrastSafeTint.contrastRatio(tint, capsule) >= ContrastSafeTint.minimumContrast)
        #expect(ContrastSafeTint.contrastRatio(tint, washTop) >= ContrastSafeTint.minimumContrast)
        #expect(ContrastSafeTint.contrastRatio(ink, tint) >= ContrastSafeTint.minimumContrast)

        // More colour at the top of the hero than the wash of the darkened tint it replaces.
        let before = ContrastSafeTint.wash(
            of: ContrastSafeTint.safeTint(for: cover, isDarkScheme: false), over: DetailPalette.lightPage, opacity: 0.22
        )
        #expect(ContrastSafeTint.HSB(washTop).saturation > ContrastSafeTint.HSB(before).saturation)
    }

    @Test func aLightCoverWashesAtFullStrengthAndADarkOneIsHeldBackForTheCaptions() {
        let gold = DetailPalette(extracted: .init(hex: 0xFA_E8_5A), secondaryText: Self.secondaryText, isDarkScheme: false)
        let navy = DetailPalette(extracted: .init(hex: 0x15_34_6C), secondaryText: Self.secondaryText, isDarkScheme: false)
        #expect(gold.washOpacity == DetailPalette.Tone.lightWashMaxOpacity)
        #expect(navy.washOpacity < DetailPalette.Tone.lightWashMaxOpacity)
        #expect(navy.washOpacity > 0.1)
    }

    // MARK: - Hero actions

    @Test func heroActionsPlayAndShuffle() throws {
        var played = false
        var shuffled = false
        let sut = HeroActions(onPlay: { played = true }, onShuffle: { shuffled = true })
        try sut.inspect().find(viewWithAccessibilityLabel: "Play").button().tap()
        try sut.inspect().find(button: "Shuffle").tap()
        #expect(played)
        #expect(shuffled)
    }

    // MARK: - Zoom sources

    @Test func onlyTheTappedTileIsTheZoomSource() {
        let id = Route.album(album("OK Computer")).cacheKey
        // Nothing tapped yet: every tile has an id of its own.
        #expect(ZoomTile.sourceID(id, tileKey: "recentlyPlayed|ok", activeKey: nil) == "tile|recentlyPlayed|ok")
        // The tapped tile is the route's source; the same album on another shelf isn't.
        #expect(ZoomTile.sourceID(id, tileKey: "recentlyPlayed|ok", activeKey: "recentlyPlayed|ok") == id)
        #expect(ZoomTile.sourceID(id, tileKey: "mostPlayed|ok", activeKey: "recentlyPlayed|ok") != id)
    }

    @Test func aDetailShelfTileIsNotASourceUntilTapped() {
        let ok = album("OK Computer")
        let id = Route.album(ok).cacheKey
        let tileKey = "detailShelf|\(ok.stableId)"
        // Home's tile for this album was tapped earlier; the shelf's, untapped, doesn't duplicate its id.
        #expect(ZoomTile.sourceID(id, tileKey: tileKey, activeKey: nil) != id)
        #expect(ZoomTile.sourceID(id, tileKey: tileKey, activeKey: tileKey) == id)
    }

    @Test func tappingADetailShelfTileOpensItsAlbum() throws {
        var opened: Album?
        let sut = List {
            DetailAlbumShelf(title: "Albums", albums: [album("OK Computer"), album("Kid A")], subtitle: { _ in nil }) {
                opened = $0
            }
        }
        #expect((try? sut.inspect().find(ViewType.NavigationLink.self)) == nil)
        try sut.inspect().find(button: "Kid A").tap()
        #expect(opened?.name == "Kid A")
    }
}
