import Foundation
import Testing
import UIKit
@testable import S2

/// The widgets' copy of Now Playing (#758): `NowPlayingStore` round-trips the snapshot and its cover, and
/// `NowPlayingWidgetPublisher` writes only on a change of song or play state, fetches each song's cover once and drops
/// a late one.
@MainActor
struct NowPlayingWidgetPublisherTests {
    private let directory = FileManager.default.temporaryDirectory
        .appendingPathComponent("NowPlayingWidgetPublisherTests-\(UUID().uuidString)", isDirectory: true)
    private var store: NowPlayingStore { NowPlayingStore(directory: directory) }

    private final class Loads {
        var reloads = 0
        var artworkAsked: [String] = []
        /// Each item's cover, when it's let through; nil holds the load until the test says.
        var release: [String: Bool] = [:]
    }

    private let loads = Loads()

    private func item(_ id: String, title: String = "Song") -> NowPlayingItem {
        NowPlayingItem(id: id, title: title, artist: "Artist", album: "Album", duration: 180, artwork: nil)
    }

    private static let image = UIGraphicsImageRenderer(size: CGSize(width: 4, height: 4)).image { context in
        UIColor.red.setFill()
        context.fill(CGRect(x: 0, y: 0, width: 4, height: 4))
    }

    private func makeSut() -> NowPlayingWidgetPublisher {
        let sut = NowPlayingWidgetPublisher(store: store)
        let loads = loads
        sut.reload = { loads.reloads += 1 }
        sut.loadArtwork = { item in
            loads.artworkAsked.append(item.id)
            while loads.release[item.id] != true {
                if Task.isCancelled { return nil }
                try? await Task.sleep(for: .milliseconds(5))
            }
            return Self.image
        }
        return sut
    }

    // MARK: - NowPlayingStore

    @Test func theStoreRoundTripsTheSnapshotAndCover() throws {
        let snapshot = NowPlayingSnapshot(itemID: "1", title: "Song", artist: "Artist", album: nil, isPlaying: true, hasArtwork: true)

        try store.write(snapshot, artwork: Data([1, 2, 3]))

        #expect(store.read() == snapshot)
        #expect(store.readArtwork() == Data([1, 2, 3]))
    }

    @Test func aWriteKeepsOrRemovesTheCover() throws {
        var snapshot = NowPlayingSnapshot(itemID: "1", title: "Song", artist: nil, album: nil, isPlaying: true, hasArtwork: true)
        try store.write(snapshot, artwork: Data([1]))

        snapshot.isPlaying = false
        try store.write(snapshot, keepArtwork: true)
        #expect(store.readArtwork() == Data([1]))

        try store.write(snapshot)
        #expect(store.readArtwork() == nil)
    }

    @Test func writingNilClearsTheStore() throws {
        try store.write(NowPlayingSnapshot(itemID: "1", title: "Song", artist: nil, album: nil, isPlaying: false, hasArtwork: true), artwork: Data([1]))

        try store.write(nil)

        #expect(store.read() == nil)
        #expect(store.readArtwork() == nil)
    }

    @Test func aStoreWithoutAContainerReadsNothing() throws {
        let store = NowPlayingStore(directory: nil)
        try store.write(NowPlayingSnapshot(itemID: "1", title: "Song", artist: nil, album: nil, isPlaying: false, hasArtwork: false))
        #expect(store.read() == nil)
    }

    // MARK: - NowPlayingWidgetPublisher

    @Test func aNewSongIsWrittenThenItsCover() async {
        let sut = makeSut()

        sut.update(item: item("1", title: "First"), isPlaying: true)

        #expect(store.read()?.title == "First")
        #expect(store.read()?.isPlaying == true)
        #expect(store.read()?.hasArtwork == false)
        #expect(loads.reloads == 1)

        loads.release["1"] = true
        #expect(await waitUntil { store.read()?.hasArtwork == true })
        #expect(store.readArtwork() != nil)
        #expect(loads.reloads == 2)
    }

    @Test func anUnchangedSongWritesNothing() async {
        let sut = makeSut()
        loads.release["1"] = true
        sut.update(item: item("1"), isPlaying: true)
        #expect(await waitUntil { store.read()?.hasArtwork == true })
        let reloads = loads.reloads

        sut.update(item: item("1"), isPlaying: true)
        sut.update(item: item("1"), isPlaying: true)

        #expect(loads.reloads == reloads)
    }

    @Test func aPlayStateChangeKeepsTheCover() async {
        let sut = makeSut()
        loads.release["1"] = true
        sut.update(item: item("1"), isPlaying: true)
        #expect(await waitUntil { store.read()?.hasArtwork == true })

        sut.update(item: item("1"), isPlaying: false)

        #expect(store.read()?.isPlaying == false)
        #expect(store.read()?.hasArtwork == true)
        #expect(store.readArtwork() != nil)
        #expect(loads.artworkAsked == ["1"], "the cover is fetched once per song")
    }

    @Test func aLateCoverForThePreviousSongIsDropped() async throws {
        let sut = makeSut()
        sut.update(item: item("1"), isPlaying: true)
        sut.update(item: item("2"), isPlaying: true)

        loads.release["1"] = true
        try await Task.sleep(for: .milliseconds(50))
        #expect(store.read()?.itemID == "2")
        #expect(store.read()?.hasArtwork == false)

        loads.release["2"] = true
        #expect(await waitUntil { store.read()?.hasArtwork == true })
        #expect(store.read()?.itemID == "2")
    }

    @Test func anEmptiedQueueClearsTheWidgets() {
        let sut = makeSut()
        sut.update(item: item("1"), isPlaying: false)

        sut.update(item: nil, isPlaying: false)
        sut.update(item: nil, isPlaying: false)

        #expect(store.read() == nil)
        #expect(sut.snapshot == nil)
        #expect(loads.reloads == 2, "only the first nil writes")
    }
}
