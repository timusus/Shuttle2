import Testing
import UIKit
@testable import S2

/// The generated artwork behind the debug "Use generated artwork" switch (`DebugArtwork`): the same seed draws the
/// same pixels, a different tone draws different ones, and the loader serves it instead of the network's cover.
@MainActor
struct DebugArtworkTests {

    private func source(_ id: String, key: String) -> ArtworkSource {
        ArtworkSource(id: id, cacheKey: key) { throw URLError(.notConnectedToInternet) }
    }

    private func pixels(_ image: UIImage?) -> Data? { image?.pngData() }

    @Test func sameSeedRendersIdenticalPixels() {
        let a = DebugArtwork.image(for: source("1", key: "album:Abbey Road"), maxPixelSize: 64)
        let b = DebugArtwork.image(for: source("2", key: "album:Abbey Road"), maxPixelSize: 64)
        #expect(pixels(a) != nil)
        #expect(pixels(a) == pixels(b))
    }

    @Test func seedsInDifferentToneSlotsRenderDifferentPixels() throws {
        let names = (0..<16).map { "album:\($0)" }
        let slots = Set(names.map(ArtworkPalette.toneIndex))
        #expect(slots.count > 1)
        let renders = Set(names.compactMap { pixels(DebugArtwork.image(for: source($0, key: $0), maxPixelSize: 32)) })
        #expect(renders.count > 1)
    }

    @Test func rendersTheRequestedSize() throws {
        let image = try #require(DebugArtwork.image(for: source("1", key: "song:1"), maxPixelSize: 80))
        #expect(image.size.width * image.scale == 80)
    }

    @Test func glyphFollowsTheItemsKind() {
        #expect(DebugArtwork.symbol(for: ArtworkSource(id: "a", cacheKey: "artist:a", isArtist: true) { [] }) == "music.mic")
        #expect(DebugArtwork.symbol(for: source("a", key: "album:a")) == "square.stack")
        #expect(DebugArtwork.symbol(for: source("a", key: "song:1")) == "music.note")
    }

    #if DEBUG
    @Test func theLoaderServesGeneratedArtworkWhenSwitchedOn() async throws {
        UserDefaults.standard.set(true, forKey: DebugArtwork.defaultsKey)
        defer { UserDefaults.standard.removeObject(forKey: DebugArtwork.defaultsKey) }
        let src = source("x", key: "album:debug-loader")
        let loaded = await ArtworkLoader.shared.image(for: src, maxPixelSize: 48)
        #expect(loaded != nil)
        #expect(ArtworkLoader.shared.cached(src, maxPixelSize: 48) != nil)
    }
    #endif
}
