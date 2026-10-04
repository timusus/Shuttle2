import Foundation
import Testing
import UIKit
@testable import S2

/// `ArtworkLoader`'s decoding step, and its walk down an item's candidates (Kotlin's `ArtworkUrls`: the media
/// server's image, then the S2 artwork API's) against a stubbed network.
struct ArtworkLoaderTests {

    @Test func aRedirectToAnotherHostLosesThePlexToken() {
        var request = URLRequest(url: URL(string: "https://cdn.example.com/a.jpg?X-Plex-Token=abc&w=300")!)
        request.setValue("abc", forHTTPHeaderField: "X-Plex-Token")
        let origin = URL(string: "http://plex.local:32400/library/metadata/1/thumb/1")

        let redirected = PlexTokenRedirectGuard.redirected(request, from: origin)

        #expect(redirected.value(forHTTPHeaderField: "X-Plex-Token") == nil)
        #expect(redirected.url?.absoluteString == "https://cdn.example.com/a.jpg?w=300")
    }

    @Test func aRedirectWithinTheServerKeepsThePlexToken() {
        var request = URLRequest(url: URL(string: "http://plex.local:32400/other?X-Plex-Token=abc")!)
        request.setValue("abc", forHTTPHeaderField: "X-Plex-Token")
        let origin = URL(string: "http://plex.local:32400/library/metadata/1/thumb/1")

        let redirected = PlexTokenRedirectGuard.redirected(request, from: origin)

        #expect(redirected.value(forHTTPHeaderField: "X-Plex-Token") == "abc")
        #expect(redirected.url == request.url)
    }

    @Test func downsampleClampsToTheRequestedMaxPixelSize() {
        let data = Self.pngData(width: 400, height: 200)
        let image = ArtworkLoader.downsample(data, maxPixelSize: 100)
        #expect(image != nil)
        #expect(max(image!.size.width, image!.size.height) == 100)
    }

    @Test func downsamplePreservesAspectRatio() {
        let data = Self.pngData(width: 400, height: 200)
        let image = ArtworkLoader.downsample(data, maxPixelSize: 100)
        #expect(image?.size.width == 100)
        #expect(image?.size.height == 50)
    }

    @Test func downsampleReturnsNilForUndecodableData() {
        #expect(ArtworkLoader.downsample(Data([0x00, 0x01, 0x02]), maxPixelSize: 100) == nil)
    }

    @Test func downsampleReturnsNilForEmptyData() {
        #expect(ArtworkLoader.downsample(Data(), maxPixelSize: 100) == nil)
    }

    @Test func nonPositiveMaxPixelSizeStillDecodes() {
        // Clamped to at least 1 pixel rather than crashing ImageIO.
        let data = Self.pngData(width: 40, height: 40)
        #expect(ArtworkLoader.downsample(data, maxPixelSize: 0) != nil)
    }

    @Test func downsampleReturnsNilForAnImageUnderTheMinimumSizeOnItsShorterSide() {
        #expect(ArtworkLoader.downsample(Self.pngData(width: 400, height: 200), maxPixelSize: 100, minimumSize: 300) == nil)
        #expect(ArtworkLoader.downsample(Self.pngData(width: 400, height: 300), maxPixelSize: 100, minimumSize: 300) != nil)
    }

    @Test func cachedIsNilBeforeAnythingWasLoaded() {
        let source = ArtworkSource(id: "unloaded-\(UUID())") { [] }
        #expect(ArtworkLoader.shared.cached(source, maxPixelSize: 64) == nil)
    }

    // MARK: - The candidate chain

    /// The Jellyfin test server answers 500 for some tagged album images ("For Now", DMA's) and has none for others
    /// ("...And Justice for All"); Android then falls through to the S2 artwork API, and so must iOS.
    @MainActor @Test func aFailingServerImageFallsThroughToTheNextCandidate() async {
        let fetcher = StubFetcher(responses: [
            Self.server: (500, Data()),
            Self.s2: (200, Self.pngData(width: 40, height: 40)),
        ])
        let loader = ArtworkLoader(fetch: fetcher.fetch)
        let source = Self.source([
            ArtworkCandidate(url: Self.server),
            ArtworkCandidate(url: Self.s2, authorization: "Basic abc", unmeteredOnly: true),
        ])

        let image = await loader.image(for: source, maxPixelSize: 64)

        #expect(image != nil)
        let requests = await fetcher.requests
        #expect(requests.map(\.url) == [Self.server, Self.s2])
        #expect(requests[0].value(forHTTPHeaderField: "Authorization") == nil)
        #expect(requests[0].allowsExpensiveNetworkAccess)
        #expect(requests[1].value(forHTTPHeaderField: "Authorization") == "Basic abc")
        #expect(!requests[1].allowsExpensiveNetworkAccess)
    }

    /// A Plex server's artwork urls carry no token (#720); Kotlin hands it over as a header.
    @Test func aCandidateSendsItsHeaders() {
        let request = ArtworkCandidate(url: Self.server, headers: ["X-Plex-Token": "token-1"]).request

        #expect(request.value(forHTTPHeaderField: "X-Plex-Token") == "token-1")
        #expect(request.value(forHTTPHeaderField: "Authorization") == nil)
    }

    /// A server's thumbnail-sized artist image counts as absent (#823): the next candidate, the top album's cover, is drawn.
    @MainActor @Test func aCandidateUnderItsMinimumSizeFallsThroughToTheNext() async {
        let fetcher = StubFetcher(responses: [
            Self.server: (200, Self.pngData(width: 40, height: 40)),
            Self.s2: (200, Self.pngData(width: 30, height: 60)),
        ])
        let loader = ArtworkLoader(fetch: fetcher.fetch)

        let image = await loader.image(for: Self.source([ArtworkCandidate(url: Self.server, minimumSize: 50), ArtworkCandidate(url: Self.s2)]), maxPixelSize: 64)

        #expect(image?.size.width == 30)
        #expect(await fetcher.requests.map(\.url) == [Self.server, Self.s2])
    }

    @MainActor @Test func theFirstCandidateThatLoadsWinsAndTheRestAreNotAsked() async {
        let fetcher = StubFetcher(responses: [Self.server: (200, Self.pngData(width: 40, height: 40))])
        let loader = ArtworkLoader(fetch: fetcher.fetch)

        let image = await loader.image(for: Self.source([ArtworkCandidate(url: Self.server), ArtworkCandidate(url: Self.s2)]), maxPixelSize: 64)

        #expect(image != nil)
        #expect(await fetcher.requests.map(\.url) == [Self.server])
    }

    @MainActor @Test func aLoadedItemIsDrawnFromMemoryWithoutLookingItUpAgain() async {
        let fetcher = StubFetcher(responses: [Self.s2: (200, Self.pngData(width: 40, height: 40))])
        let loader = ArtworkLoader(fetch: fetcher.fetch)
        var lookups = 0
        let source = ArtworkSource(id: "cached-\(UUID())") {
            lookups += 1
            return [ArtworkCandidate(url: Self.s2)]
        }

        _ = await loader.image(for: source, maxPixelSize: 64)

        #expect(loader.cached(source, maxPixelSize: 64) != nil)
        #expect(await loader.image(for: source, maxPixelSize: 64) != nil)
        #expect(lookups == 1)
    }

    @Test func itemKeysCarryTheKindAndTheArtworkVersion() {
        #expect(ArtworkSource.itemKey("album", "ok computer", version: nil) == "album:ok computer")
        #expect(ArtworkSource.itemKey("album", "ok computer", version: "v2") == "album:ok computer_v2")
        #expect(ArtworkSource.itemKey("artist", "x", version: nil) != ArtworkSource.itemKey("album", "x", version: nil))
    }

    @MainActor @Test func aChangedArtworkVersionMissesTheCacheAndLooksTheItemUpAgain() async {
        let fetcher = StubFetcher(responses: [Self.s2: (200, Self.pngData(width: 40, height: 40))])
        let loader = ArtworkLoader(fetch: fetcher.fetch)
        let id = "versioned-\(UUID())"
        var lookups = 0
        func source(version: String) -> ArtworkSource {
            ArtworkSource(id: id, cacheKey: ArtworkSource.itemKey("album", id, version: version)) {
                lookups += 1
                return [ArtworkCandidate(url: Self.s2)]
            }
        }

        _ = await loader.image(for: source(version: "1"), maxPixelSize: 64)

        #expect(loader.cached(source(version: "1"), maxPixelSize: 64) != nil)
        #expect(loader.cached(source(version: "2"), maxPixelSize: 64) == nil)
        #expect(await loader.image(for: source(version: "2"), maxPixelSize: 64) != nil)
        #expect(lookups == 2)
    }

    @MainActor @Test func anItemNoCandidateLoadsForIsNotCachedSoTheNextLookTriesAgain() async {
        let fetcher = StubFetcher(responses: [Self.server: (404, Data()), Self.s2: (200, Data([0x00, 0x01]))])
        let loader = ArtworkLoader(fetch: fetcher.fetch)
        let source = Self.source([ArtworkCandidate(url: Self.server), ArtworkCandidate(url: Self.s2)])

        #expect(await loader.image(for: source, maxPixelSize: 64) == nil)
        #expect(loader.cached(source, maxPixelSize: 64) == nil)
        _ = await loader.image(for: source, maxPixelSize: 64)
        #expect(await fetcher.requests.count == 4)
    }

    @MainActor @Test func aSourceWithOneUrlHasOneCandidateAndNoneForNil() async throws {
        #expect(try await ArtworkSource(id: 1) { "https://example.com/a.jpg" }.candidates() == [ArtworkCandidate(url: URL(string: "https://example.com/a.jpg")!)])
        #expect(try await ArtworkSource(id: 1) { nil }.candidates().isEmpty)
    }

    // MARK: - Callers that draw a source's cover

    /// Now Playing's tint and backdrop go through the same chain as the thumbnail, so a song whose cover only the S2
    /// artwork API has is tinted and backed by it too, not left on the accent and a bare gradient.
    @MainActor @Test func theTintIsReadFromTheFallbackWhenTheServerImageFails() async {
        let fetcher = StubFetcher(responses: [
            Self.server: (500, Data()),
            Self.s2: (200, Self.pngData(width: 40, height: 40)),
        ])
        let loader = ArtworkLoader(fetch: fetcher.fetch)
        let extractor = ArtworkColorExtractor { source, pixels in await loader.image(for: source, maxPixelSize: pixels) }

        let color = await extractor.color(for: Self.source([ArtworkCandidate(url: Self.server), ArtworkCandidate(url: Self.s2)]))

        #expect(color != nil)
        #expect(await fetcher.requests.map(\.url) == [Self.server, Self.s2])
    }

    @MainActor @Test func theBackdropIsDrawnFromTheFallbackWhenTheServerImageFails() async throws {
        let fetcher = StubFetcher(responses: [
            Self.server: (404, Data()),
            Self.s2: (200, Self.pngData(width: 400, height: 400)),
        ])
        let loader = ArtworkLoader(fetch: fetcher.fetch)

        let image = await ArtworkBackground.image(
            for: Self.source([ArtworkCandidate(url: Self.server), ArtworkCandidate(url: Self.s2)]),
            loader: loader
        )

        let loaded = try #require(image)
        #expect(max(loaded.size.width, loaded.size.height) == CGFloat(ArtworkBackground.pixels))
        #expect(await fetcher.requests.map(\.url) == [Self.server, Self.s2])
    }

    @MainActor @Test func noSourceOrNoCandidatesDrawsNoBackdropAndNoTint() async {
        let fetcher = StubFetcher(responses: [:])
        let loader = ArtworkLoader(fetch: fetcher.fetch)
        let extractor = ArtworkColorExtractor { source, pixels in await loader.image(for: source, maxPixelSize: pixels) }

        #expect(await ArtworkBackground.image(for: nil, loader: loader) == nil)
        #expect(await ArtworkBackground.image(for: Self.source([]), loader: loader) == nil)
        #expect(await extractor.color(for: ArtworkSource(id: "none", load: { nil })) == nil)
        #expect(await fetcher.requests.isEmpty)
    }

    private static let server = URL(string: "https://jellyfin.example/Items/album/Images/Primary")!
    private static let s2 = URL(string: "https://api.shuttlemusicplayer.app/v1/artwork?artist=A&album=B")!

    private static func source(_ candidates: [ArtworkCandidate]) -> ArtworkSource {
        ArtworkSource(id: UUID()) { candidates }
    }

    /// Answers each url with a canned status and body, and records the requests it was sent.
    private actor StubFetcher {
        let responses: [URL: (Int, Data)]
        private(set) var requests: [URLRequest] = []

        init(responses: [URL: (Int, Data)]) { self.responses = responses }

        nonisolated var fetch: ArtworkLoader.Fetch {
            { request in try await self.respond(to: request) }
        }

        private func respond(to request: URLRequest) throws -> (Data, URLResponse) {
            requests.append(request)
            guard let url = request.url, let (status, data) = responses[url] else { throw URLError(.cannotConnectToHost) }
            return (data, HTTPURLResponse(url: url, statusCode: status, httpVersion: nil, headerFields: nil)!)
        }
    }

    private static func pngData(width: Int, height: Int) -> Data {
        // At 1x, so the image is `width` by `height` pixels rather than the screen's scale times that.
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format)
        let image = renderer.image { context in
            UIColor.systemBlue.setFill()
            context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        }
        return image.pngData()!
    }
}
