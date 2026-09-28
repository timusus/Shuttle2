import Foundation
import Testing
import UIKit
@testable import S2

/// `ArtworkLoader`'s decoding step, and its walk down an item's candidates (Kotlin's `ArtworkUrls`: the media
/// server's image, then the S2 artwork API's) against a stubbed network.
struct ArtworkLoaderTests {

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
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: width, height: height))
        let image = renderer.image { context in
            UIColor.systemBlue.setFill()
            context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        }
        return image.pngData()!
    }
}
