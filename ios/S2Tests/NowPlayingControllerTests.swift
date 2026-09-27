import MediaPlayer
import Testing
import UIKit
@testable import S2

/// Now Playing info and remote commands against fake centres: what gets published, when elapsed/rate
/// are rewritten, and that each system command reaches the handler.
@MainActor
struct NowPlayingControllerTests {
    private final class FakeInfoCenter: NowPlayingInfoCenter {
        var nowPlayingInfo: [String: Any]? {
            didSet { writes += 1 }
        }
        var playbackState: MPNowPlayingPlaybackState = .unknown
        private(set) var writes = 0

        func value<T>(_ key: String) -> T? { nowPlayingInfo?[key] as? T }
    }

    private final class FakeCommandCenter: RemoteCommandCenter {
        var handlers: [RemoteCommand: (RemoteCommandEvent) -> MPRemoteCommandHandlerStatus] = [:]
        var intervals: (forward: TimeInterval, backward: TimeInterval)?

        func setHandler(for command: RemoteCommand, _ handler: ((RemoteCommandEvent) -> MPRemoteCommandHandlerStatus)?) {
            handlers[command] = handler
        }

        func setSkipIntervals(forward: TimeInterval, backward: TimeInterval) {
            intervals = (forward, backward)
        }

        @discardableResult
        func fire(_ command: RemoteCommand, _ event: RemoteCommandEvent = .plain) -> MPRemoteCommandHandlerStatus? {
            handlers[command]?(event)
        }
    }

    private final class RecordingHandler: NowPlayingCommandHandler {
        var calls: [String] = []
        func play() { calls.append("play") }
        func pause() { calls.append("pause") }
        func togglePlayPause() { calls.append("toggle") }
        func skipToNext() { calls.append("next") }
        func skipToPrevious() { calls.append("previous") }
        func seek(to position: TimeInterval) { calls.append("seek \(position)") }
        func skip(by interval: TimeInterval) { calls.append("skip \(interval)") }
    }

    private let info = FakeInfoCenter()
    private let commands = FakeCommandCenter()
    private var clock = Date(timeIntervalSince1970: 1_000)

    private func makeController(now: (() -> Date)? = nil) -> NowPlayingController {
        let start = clock
        return NowPlayingController(infoCenter: info, commandCenter: commands, now: now ?? { start })
    }

    private let song = NowPlayingItem(id: "1", title: "Teardrop", artist: "Massive Attack", album: "Mezzanine", duration: 330)

    // MARK: - Commands

    @Test func transportCommandsReachTheHandler() {
        let controller = makeController()
        let handler = RecordingHandler()
        controller.start(handler: handler)

        commands.fire(.play)
        commands.fire(.pause)
        commands.fire(.togglePlayPause)
        commands.fire(.nextTrack)
        commands.fire(.previousTrack)
        #expect(commands.fire(.changePlaybackPosition, .position(42)) == .success)

        #expect(handler.calls == ["play", "pause", "toggle", "next", "previous", "seek 42.0"])
    }

    @Test func tracksModeLeavesTheIntervalSkipsDisabled() {
        let controller = makeController()
        controller.start(handler: RecordingHandler())
        #expect(commands.handlers[.nextTrack] != nil)
        #expect(commands.handlers[.skipForward] == nil)
        #expect(commands.handlers[.skipBackward] == nil)
    }

    @Test func intervalModeSwapsNextAndPreviousForSkips() {
        let controller = makeController()
        let handler = RecordingHandler()
        controller.start(handler: handler)
        controller.setSkipMode(.interval(forward: 30, backward: 10))

        #expect(commands.handlers[.nextTrack] == nil)
        #expect(commands.handlers[.previousTrack] == nil)
        #expect(commands.intervals?.forward == 30)
        #expect(commands.intervals?.backward == 10)
        commands.fire(.skipForward, .interval(30))
        commands.fire(.skipBackward, .interval(10))
        #expect(handler.calls == ["skip 30.0", "skip -10.0"])

        controller.setSkipMode(.tracks)
        #expect(commands.handlers[.nextTrack] != nil)
        #expect(commands.handlers[.skipForward] == nil)
    }

    @Test func malformedEventsFail() {
        let controller = makeController()
        let handler = RecordingHandler()
        controller.start(handler: handler)
        #expect(commands.fire(.changePlaybackPosition, .plain) == .commandFailed)
        #expect(handler.calls.isEmpty)
    }

    @Test func aReleasedHandlerAnswersNoActionableItem() {
        let controller = makeController()
        do {
            let handler = RecordingHandler()
            controller.start(handler: handler)
        }
        #expect(commands.fire(.play) == .noActionableNowPlayingItem)
    }

    @Test func stopRemovesEveryTargetAndClearsTheInfo() {
        let controller = makeController()
        controller.start(handler: RecordingHandler())
        controller.setItem(song, position: 0, isPlaying: true, speed: 1)
        controller.stop()
        #expect(commands.handlers.isEmpty)
        #expect(info.nowPlayingInfo == nil)
        #expect(info.playbackState == .stopped)
    }

    // MARK: - Info

    @Test func setItemPublishesMetadataPositionAndRate() {
        let controller = makeController()
        controller.setItem(song, position: 12, isPlaying: true, speed: 1.5)

        #expect(info.value(MPMediaItemPropertyTitle) == "Teardrop")
        #expect(info.value(MPMediaItemPropertyArtist) == "Massive Attack")
        #expect(info.value(MPMediaItemPropertyAlbumTitle) == "Mezzanine")
        #expect(info.value(MPMediaItemPropertyPlaybackDuration) == 330.0)
        #expect(info.value(MPNowPlayingInfoPropertyElapsedPlaybackTime) == 12.0)
        #expect(info.value(MPNowPlayingInfoPropertyPlaybackRate) == 1.5)
        #expect(info.value(MPNowPlayingInfoPropertyDefaultPlaybackRate) == 1.5)
        #expect(info.playbackState == .playing)
    }

    @Test func pausedPublishesRateZeroAndKeepsTheDefaultRate() {
        let controller = makeController()
        controller.setItem(song, position: 5, isPlaying: false, speed: 1.25)
        #expect(info.value(MPNowPlayingInfoPropertyPlaybackRate) == 0.0)
        #expect(info.value(MPNowPlayingInfoPropertyDefaultPlaybackRate) == 1.25)
        #expect(info.playbackState == .paused)
    }

    @Test func settingNilClearsTheInfo() {
        let controller = makeController()
        controller.setItem(song, position: 0, isPlaying: true, speed: 1)
        controller.setItem(nil, position: 0, isPlaying: false, speed: 1)
        #expect(info.nowPlayingInfo == nil)
        #expect(info.playbackState == .stopped)
    }

    @Test func progressTicksOnTheExtrapolatedLineAreNotWritten() {
        var now = clock
        let controller = makeController(now: { now })
        controller.setItem(song, position: 10, isPlaying: true, speed: 1)
        let writes = info.writes

        now += 3
        controller.updatePlayback(position: 13.2, isPlaying: true, speed: 1)
        #expect(info.writes == writes)
        #expect(info.value(MPNowPlayingInfoPropertyElapsedPlaybackTime) == 10.0)
    }

    @Test func aSeekPauseSpeedChangeOrDriftGuardIsWritten() {
        var now = clock
        let controller = makeController(now: { now })
        controller.setItem(song, position: 10, isPlaying: true, speed: 1)

        now += 1
        controller.updatePlayback(position: 100, isPlaying: true, speed: 1) // seek
        #expect(info.value(MPNowPlayingInfoPropertyElapsedPlaybackTime) == 100.0)

        controller.updatePlayback(position: 100, isPlaying: false, speed: 1) // pause
        #expect(info.value(MPNowPlayingInfoPropertyPlaybackRate) == 0.0)
        #expect(info.playbackState == .paused)

        controller.updatePlayback(position: 100, isPlaying: true, speed: 2) // resume at 2x
        #expect(info.value(MPNowPlayingInfoPropertyPlaybackRate) == 2.0)

        now += NowPlayingController.driftGuardSeconds
        controller.updatePlayback(position: 120, isPlaying: true, speed: 2) // on the line, but stale
        #expect(info.value(MPNowPlayingInfoPropertyElapsedPlaybackTime) == 120.0)
    }

    @Test func updateWithoutAnItemWritesNothing() {
        let controller = makeController()
        controller.updatePlayback(position: 3, isPlaying: true, speed: 1)
        #expect(info.nowPlayingInfo == nil)
        #expect(info.writes == 0)
    }

    // MARK: - Artwork

    @Test func artworkIsLoadedOncePerItemAndKeptAcrossMetadataEdits() async {
        let controller = makeController()
        var requested: [String] = []
        controller.loadArtwork = { item in
            requested.append(item.id)
            return Self.image()
        }
        controller.setItem(song, position: 0, isPlaying: true, speed: 1)
        await controller.artworkTask?.value
        #expect(info.nowPlayingInfo?[MPMediaItemPropertyArtwork] is MPMediaItemArtwork)

        var edited = song
        edited.title = "Teardrop (Remastered)"
        controller.setItem(edited, position: 0, isPlaying: true, speed: 1)
        await controller.artworkTask?.value
        #expect(requested == ["1"])
        #expect(info.nowPlayingInfo?[MPMediaItemPropertyArtwork] is MPMediaItemArtwork)
    }

    @Test func lateArtworkForAPreviousItemIsDropped() async {
        let controller = makeController()
        let gate = AsyncGate()
        controller.loadArtwork = { item in
            if item.id == "1" { await gate.wait() }
            return item.id == "1" ? Self.image() : nil
        }
        controller.setItem(song, position: 0, isPlaying: true, speed: 1)
        let firstLoad = controller.artworkTask
        let next = NowPlayingItem(id: "2", title: "Angel", artist: nil, album: nil, duration: 0)
        controller.setItem(next, position: 0, isPlaying: true, speed: 1)
        await gate.open()
        await firstLoad?.value
        await controller.artworkTask?.value

        #expect(info.value(MPMediaItemPropertyTitle) == "Angel")
        #expect(info.nowPlayingInfo?[MPMediaItemPropertyArtwork] == nil)
        #expect(info.nowPlayingInfo?[MPMediaItemPropertyPlaybackDuration] == nil)
    }

    private static func image() -> UIImage {
        UIGraphicsImageRenderer(size: CGSize(width: 4, height: 4)).image { context in
            UIColor.red.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 4, height: 4))
        }
    }
}

/// Holds an artwork load until the test lets it finish.
private actor AsyncGate {
    private var isOpen = false
    private var waiters: [CheckedContinuation<Void, Never>] = []

    func wait() async {
        if isOpen { return }
        await withCheckedContinuation { waiters.append($0) }
    }

    func open() {
        isOpen = true
        waiters.forEach { $0.resume() }
        waiters.removeAll()
    }
}
