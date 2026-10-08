import PlaybackStreaming
import XCTest
@testable import S2Playback

/// A stream on an expensive path downloads a minute of audio ahead of the decoder (#958).
final class StreamReadAheadTests: XCTestCase {

    func testTheLibrarysBitrateSizesIt() {
        XCTAssertEqual(StreamReadAhead.readAhead(bitrateKbps: 320, sizeBytes: nil, durationMs: nil), GrowingFileReadAhead(bytes: 2_400_000))
        XCTAssertEqual(StreamReadAhead.readAhead(bitrateKbps: 128, sizeBytes: 1, durationMs: 1), GrowingFileReadAhead(bytes: 960_000))
    }

    /// 6 MB over four minutes is 200 kbps.
    func testWithoutABitrateTheFilesAverageSizesIt() {
        XCTAssertEqual(
            StreamReadAhead.readAhead(bitrateKbps: nil, sizeBytes: 6_000_000, durationMs: 240_000),
            GrowingFileReadAhead(bytes: 1_500_000)
        )
        XCTAssertEqual(
            StreamReadAhead.readAhead(bitrateKbps: 0, sizeBytes: 6_000_000, durationMs: 240_000),
            GrowingFileReadAhead(bytes: 1_500_000)
        )
    }

    func testWithNeitherItAssumes320Kbps() {
        XCTAssertEqual(StreamReadAhead.readAhead(bitrateKbps: nil, sizeBytes: nil, durationMs: nil), GrowingFileReadAhead(bytes: 2_400_000))
        XCTAssertEqual(StreamReadAhead.readAhead(bitrateKbps: nil, sizeBytes: 6_000_000, durationMs: 0), GrowingFileReadAhead(bytes: 2_400_000))
    }
}
