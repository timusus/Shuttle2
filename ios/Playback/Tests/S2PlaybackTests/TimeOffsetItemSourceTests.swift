import Foundation
import PlaybackDecode
import PlaybackPlayer
import PlaybackPlayerTestSupport
import PlaybackStreaming
import PlaybackStreamingTestSupport
import XCTest
@testable import S2Playback
import S2PlaybackTestSupport

/// A progressive transcode seeks and recovers by re-opening the stream at a time offset; a stream served as stored is
/// left to the decoder (#1020).
@MainActor
final class TimeOffsetItemSourceTests: XCTestCase {
    private var server: PlaybackStreamingTestSupport.LoopbackMediaServer!
    private var storeDirectory: URL!
    private var store: GrowingFileStore!
    /// The 20 s fixture each request is answered with, whatever its offset: what the source asks for is under test.
    private var tone: Data!

    override func setUpWithError() throws {
        let url = try XCTUnwrap(S2PlaybackTestSupport.LoopbackMediaServer.fixtureURL("tone", withExtension: "mp3"))
        tone = try Data(contentsOf: url)
        server = try PlaybackStreamingTestSupport.LoopbackMediaServer(body: tone, mimeType: "audio/mpeg")
        server.streamsWithoutLength = true
        storeDirectory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        store = GrowingFileStore(directory: storeDirectory, budgetBytes: 64 * 1024 * 1024)
    }

    override func tearDown() {
        server.stop()
        try? FileManager.default.removeItem(at: storeDirectory)
        super.tearDown()
    }

    /// A rig whose items stream from the loopback origin, asked for at `?offset=<seconds>`; a transcode when
    /// `transcodeDurationMs` is set.
    private func makeRig(transcodeDurationMs: Int64?) -> PlayerTestRig<Int> {
        let (server, store) = (server!, store!)
        return PlayerTestRig { _ in
            TimeOffsetItemSource { startMs in
                let url = URL(string: "\(server.url.absoluteString)?offset=\(startMs / 1000)")!
                // swiftlint:disable:next force_try
                let reader = try! StreamStore.reader(url: url, headers: [:], store: store)
                return OffsetReader(reader: reader, transcodeDurationMs: transcodeDurationMs)
            }
        }
    }

    private var offsetsAskedFor: [String] {
        server.requestHeads.compactMap { head in
            head.range(of: #"offset=\d+"#, options: .regularExpression).map { String(head[$0]) }
        }
    }

    func testATranscodeDeclaresTheSongsDurationAndIsSeekable() {
        let rig = makeRig(transcodeDurationMs: 31_000)
        rig.player.setItems([1])
        rig.player.play()

        rig.run(for: 1)

        XCTAssertEqual(rig.player.duration ?? 0, 31, accuracy: 0.01, "the song's, not the transcode container's")
        XCTAssertTrue(rig.player.isSeekable)
        XCTAssertFalse(rig.player.isLive)
        XCTAssertEqual(rig.player.position, 1, accuracy: 0.3)
    }

    func testASeekInATranscodeReRequestsItAtTheOffsetAndPlaysOnFromThere() {
        let rig = makeRig(transcodeDurationMs: 20_000)
        var adjusted = false
        rig.onEvent = { if case .seekAdjusted = $0 { adjusted = true } }
        rig.player.setItems([1])
        rig.player.play()
        rig.run(for: 1)

        rig.player.seek(to: 12.5)
        rig.run(for: 1)

        XCTAssertEqual(offsetsAskedFor, ["offset=0", "offset=12"])
        XCTAssertEqual(rig.player.position, 13.5, accuracy: 0.3)
        XCTAssertFalse(adjusted, "the frames up to the target are dropped, so the seek lands where it was asked")
    }

    func testADroppedTranscodeReOpensAtItsPosition() {
        let rig = makeRig(transcodeDurationMs: 20_000)
        server.closesAfterBodyBytes = tone.count / 2
        rig.player.setItems([1, 2])
        rig.player.play()

        rig.run(for: 13)

        XCTAssertEqual(rig.player.currentIndex, 0, "a drop isn't the end of the song")
        XCTAssertEqual(rig.player.position, 13, accuracy: 0.3)
        XCTAssertEqual(offsetsAskedFor.first, "offset=0")
        let reopen = offsetsAskedFor.dropFirst().first
        XCTAssertTrue(["offset=8", "offset=9", "offset=10"].contains(reopen ?? ""), "re-opened at \(reopen ?? "nothing")")
    }

    func testASeekPastTheExpectedEndEndsTheItemWithoutAskingAgain() {
        let rig = makeRig(transcodeDurationMs: 19_000)
        var adjusted: [TimeInterval] = []
        rig.onEvent = { if case let .seekAdjusted(_, _, position) = $0 { adjusted.append(position) } }
        rig.player.setItems([1, 2])
        rig.player.play()
        rig.run(for: 1)

        rig.player.seek(to: 30)
        rig.run(for: 1)

        XCTAssertEqual(adjusted.count, 1)
        XCTAssertEqual(adjusted.first ?? 0, 19, accuracy: 0.01)
        XCTAssertEqual(rig.player.currentIndex, 1)
        XCTAssertFalse(offsetsAskedFor.contains("offset=19"))
    }

    /// No duration to clamp a seek to or tell a short end by: the seek is refused, and answered with where it stayed.
    func testATranscodeOfUnknownDurationRefusesASeekCleanly() {
        let rig = makeRig(transcodeDurationMs: -1)
        var adjusted: [TimeInterval] = []
        rig.onEvent = { if case let .seekAdjusted(_, _, position) = $0 { adjusted.append(position) } }
        rig.player.setItems([1])
        rig.player.play()
        rig.run(for: 1)

        rig.player.seek(to: 12.5)
        rig.run(for: 1)

        XCTAssertFalse(rig.player.isSeekable)
        XCTAssertEqual(adjusted.count, 1)
        XCTAssertEqual(adjusted.first ?? 0, 1, accuracy: 0.3)
        XCTAssertEqual(Set(offsetsAskedFor), ["offset=0"])
        XCTAssertEqual(rig.player.position, 2, accuracy: 0.3)
    }

    func testAStreamServedAsStoredSeeksWithoutAskingAtAnOffset() {
        server.streamsWithoutLength = false
        let rig = makeRig(transcodeDurationMs: nil)
        rig.player.setItems([1])
        rig.player.play()
        rig.run(for: 1)

        rig.player.seek(to: 12.5)
        rig.run(for: 1)

        XCTAssertEqual(Set(offsetsAskedFor), ["offset=0"])
        XCTAssertEqual(rig.player.duration ?? 0, 20, accuracy: 0.1)
        XCTAssertEqual(rig.player.position, 13.5, accuracy: 0.3)
    }
}

/// A loopback stream as the app's resolved reader presents it.
private final class OffsetReader: TimeOffsetByteReader, @unchecked Sendable {
    private let reader: StreamByteReader
    let transcodeDurationMs: Int64?

    init(reader: StreamByteReader, transcodeDurationMs: Int64?) {
        self.reader = reader
        self.transcodeDurationMs = transcodeDurationMs
    }

    var totalLength: Int64? { reader.totalLength }
    var position: Int64 { reader.position }
    var fetchedEnd: Int64? { reader.fetchedEnd }
    func read(into buffer: UnsafeMutableRawPointer, maxLength: Int) throws -> Int { try reader.read(into: buffer, maxLength: maxLength) }
    func seek(to offset: Int64) throws { try reader.seek(to: offset) }
    func cancel() { reader.cancel() }
    func interrupt() { reader.interrupt() }
    func clearInterrupt() { reader.clearInterrupt() }
}
