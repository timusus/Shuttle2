import MediaPlayer
import UIKit

/// The song the lock screen, Control Center and CarPlay show.
struct NowPlayingItem: Equatable {
    /// Identifies the queue item, so a late artwork load can't land on the next song.
    var id: String
    var title: String
    var artist: String?
    var album: String?
    /// Seconds; 0 when unknown.
    var duration: TimeInterval
}

/// What the system's transport controls can ask the player for. Called on the main thread.
@MainActor
protocol NowPlayingCommandHandler: AnyObject {
    func play()
    func pause()
    func togglePlayPause()
    func skipToNext()
    func skipToPrevious()
    /// Scrub to `position` seconds into the current song.
    func seek(to position: TimeInterval)
    /// Jump by `interval` seconds, negative for back (audiobook and podcast songs).
    func skip(by interval: TimeInterval)
}

/// The system commands S2 answers.
enum RemoteCommand: CaseIterable {
    case play, pause, togglePlayPause, nextTrack, previousTrack, changePlaybackPosition, skipForward, skipBackward
}

/// A command's payload, unwrapped from its `MPRemoteCommandEvent` subclass.
enum RemoteCommandEvent: Equatable {
    case plain
    case position(TimeInterval)
    case interval(TimeInterval)
}

/// `MPRemoteCommandCenter` behind a seam: its events can't be constructed, so tests fire handlers instead.
@MainActor
protocol RemoteCommandCenter: AnyObject {
    /// Enables `command` with `handler`, replacing any earlier one; nil disables it.
    func setHandler(for command: RemoteCommand, _ handler: ((RemoteCommandEvent) -> MPRemoteCommandHandlerStatus)?)
    /// The intervals drawn in the skip glyphs.
    func setSkipIntervals(forward: TimeInterval, backward: TimeInterval)
}

/// `MPNowPlayingInfoCenter` behind a seam.
protocol NowPlayingInfoCenter: AnyObject {
    var nowPlayingInfo: [String: Any]? { get set }
    var playbackState: MPNowPlayingPlaybackState { get set }
}

extension MPNowPlayingInfoCenter: NowPlayingInfoCenter {}

/// Publishes the current song to `MPNowPlayingInfoCenter` and routes `MPRemoteCommandCenter` commands to
/// a `NowPlayingCommandHandler` (phase 6, docs/architecture/ios-port/phase-6-playback.md). Adapted from
/// Shuttle Podcasts' `NowPlayingInfoManager` and `RemoteCommandHandler`.
///
/// Elapsed time and rate are written when the playback state changes or the position jumps, not per
/// progress tick: the system extrapolates between writes from the rate, and a write per tick makes the
/// lock screen scrubber stutter.
///
/// `PlaybackSystemCoordinator` owns one, handles its commands with the Kotlin `IosPlayerController` and
/// feeds it from the player's flows.
///
/// TODO(#588): `loadArtwork` goes through the shared image loader once one exists.
@MainActor
final class NowPlayingController {
    /// Whether the skip buttons move between songs or jump within one.
    enum SkipMode: Equatable {
        case tracks
        case interval(forward: TimeInterval, backward: TimeInterval)
    }

    /// Loads the artwork for an item. Called once per new item; the result is dropped if the item has
    /// changed by the time it arrives.
    var loadArtwork: (NowPlayingItem) async -> UIImage? = { _ in nil }

    static let discontinuitySeconds: TimeInterval = 1
    static let driftGuardSeconds: TimeInterval = 10

    private let infoCenter: NowPlayingInfoCenter
    private let commandCenter: RemoteCommandCenter
    private let now: () -> Date
    private weak var handler: NowPlayingCommandHandler?
    private(set) var skipMode: SkipMode = .tracks
    private(set) var item: NowPlayingItem?
    private var info: [String: Any]?
    private var lastPublish: (at: Date, position: TimeInterval, rate: Float, speed: Float)?
    /// The artwork load for the current item, exposed so tests can await it.
    private(set) var artworkTask: Task<Void, Never>?

    init(
        infoCenter: NowPlayingInfoCenter = MPNowPlayingInfoCenter.default(),
        commandCenter: RemoteCommandCenter? = nil,
        now: @escaping () -> Date = Date.init
    ) {
        self.infoCenter = infoCenter
        self.commandCenter = commandCenter ?? SystemRemoteCommandCenter()
        self.now = now
    }

    // MARK: - Commands

    /// Registers the command targets, routed to `handler` (held weakly).
    func start(handler: NowPlayingCommandHandler) {
        self.handler = handler
        register(.play) { $0.play() }
        register(.pause) { $0.pause() }
        register(.togglePlayPause) { $0.togglePlayPause() }
        register(.changePlaybackPosition) { handler, event in
            guard case let .position(position) = event else { return .commandFailed }
            handler.seek(to: position)
            return .success
        }
        applySkipMode()
    }

    /// Removes every command target and clears the Now Playing info.
    func stop() {
        RemoteCommand.allCases.forEach { commandCenter.setHandler(for: $0, nil) }
        handler = nil
        setItem(nil, position: 0, isPlaying: false, speed: 1)
    }

    /// Songs normally skip between tracks; audiobook and podcast songs jump by an interval instead.
    func setSkipMode(_ mode: SkipMode) {
        guard mode != skipMode else { return }
        skipMode = mode
        if handler != nil { applySkipMode() }
    }

    private func applySkipMode() {
        switch skipMode {
        case .tracks:
            register(.nextTrack) { $0.skipToNext() }
            register(.previousTrack) { $0.skipToPrevious() }
            commandCenter.setHandler(for: .skipForward, nil)
            commandCenter.setHandler(for: .skipBackward, nil)
        case let .interval(forward, backward):
            commandCenter.setHandler(for: .nextTrack, nil)
            commandCenter.setHandler(for: .previousTrack, nil)
            commandCenter.setSkipIntervals(forward: forward, backward: backward)
            register(.skipForward) { handler, event in
                guard case let .interval(interval) = event else { return .commandFailed }
                handler.skip(by: interval)
                return .success
            }
            register(.skipBackward) { handler, event in
                guard case let .interval(interval) = event else { return .commandFailed }
                handler.skip(by: -interval)
                return .success
            }
        }
    }

    private func register(_ command: RemoteCommand, _ action: @escaping (NowPlayingCommandHandler) -> Void) {
        register(command) { handler, _ in
            action(handler)
            return .success
        }
    }

    private func register(
        _ command: RemoteCommand,
        _ action: @escaping (NowPlayingCommandHandler, RemoteCommandEvent) -> MPRemoteCommandHandlerStatus
    ) {
        commandCenter.setHandler(for: command) { [weak self] event in
            guard let handler = self?.handler else { return .noActionableNowPlayingItem }
            return action(handler, event)
        }
    }

    // MARK: - Info

    /// Publishes `item` with its position and rate, or clears the Now Playing info for nil. Setting the
    /// same item again (a metadata edit) keeps its artwork; a new item loads its own.
    func setItem(_ item: NowPlayingItem?, position: TimeInterval, isPlaying: Bool, speed: Float) {
        let previous = self.item
        self.item = item
        guard let item else {
            artworkTask?.cancel()
            artworkTask = nil
            info = nil
            lastPublish = nil
            infoCenter.nowPlayingInfo = nil
            setPlaybackState(.stopped)
            return
        }
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: item.title,
            MPNowPlayingInfoPropertyMediaType: MPNowPlayingInfoMediaType.audio.rawValue,
        ]
        info[MPMediaItemPropertyArtist] = item.artist
        info[MPMediaItemPropertyAlbumTitle] = item.album
        if item.duration > 0 { info[MPMediaItemPropertyPlaybackDuration] = item.duration }
        if previous?.id == item.id {
            info[MPMediaItemPropertyArtwork] = self.info?[MPMediaItemPropertyArtwork]
        }
        self.info = info
        writePlayback(position: position, isPlaying: isPlaying, speed: speed)

        if previous?.id != item.id {
            artworkTask?.cancel()
            let load = loadArtwork
            artworkTask = Task { [weak self] in
                guard let image = await load(item), !Task.isCancelled else { return }
                self?.applyArtwork(image, for: item.id)
            }
        }
    }

    /// Updates elapsed time and rate. Writes only when the state or speed changed, the position jumped
    /// more than `discontinuitySeconds` from where the system extrapolates it, or `driftGuardSeconds`
    /// passed, so it is safe to call on every progress tick.
    func updatePlayback(position: TimeInterval, isPlaying: Bool, speed: Float) {
        guard info != nil else { return }
        setPlaybackState(isPlaying ? .playing : .paused)
        guard shouldPublish(position: position, rate: isPlaying ? speed : 0, speed: speed) else { return }
        writePlayback(position: position, isPlaying: isPlaying, speed: speed)
    }

    private func shouldPublish(position: TimeInterval, rate: Float, speed: Float) -> Bool {
        guard let last = lastPublish else { return true }
        if last.rate != rate || last.speed != speed { return true }
        let elapsed = now().timeIntervalSince(last.at)
        if elapsed >= Self.driftGuardSeconds { return true }
        let extrapolated = last.position + elapsed * Double(last.rate)
        return abs(position - extrapolated) > Self.discontinuitySeconds
    }

    private func writePlayback(position: TimeInterval, isPlaying: Bool, speed: Float) {
        guard var info else { return }
        let rate: Float = isPlaying ? speed : 0
        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = position
        info[MPNowPlayingInfoPropertyPlaybackRate] = Double(rate)
        info[MPNowPlayingInfoPropertyDefaultPlaybackRate] = Double(speed > 0 ? speed : 1)
        self.info = info
        infoCenter.nowPlayingInfo = info
        lastPublish = (at: now(), position: position, rate: rate, speed: speed)
        setPlaybackState(isPlaying ? .playing : .paused)
    }

    private func applyArtwork(_ image: UIImage, for id: String) {
        guard item?.id == id, var info else { return }
        info[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
        self.info = info
        infoCenter.nowPlayingInfo = info
    }

    private func setPlaybackState(_ state: MPNowPlayingPlaybackState) {
        if infoCenter.playbackState != state { infoCenter.playbackState = state }
    }
}

/// The real command centre: one target per command, removed before a replacement is added.
@MainActor
final class SystemRemoteCommandCenter: RemoteCommandCenter {
    private let center: MPRemoteCommandCenter
    private var targets: [RemoteCommand: Any] = [:]

    init(center: MPRemoteCommandCenter = .shared()) {
        self.center = center
    }

    func setHandler(for command: RemoteCommand, _ handler: ((RemoteCommandEvent) -> MPRemoteCommandHandlerStatus)?) {
        let remote = self.command(command)
        if let target = targets.removeValue(forKey: command) {
            remote.removeTarget(target)
        }
        guard let handler else {
            remote.isEnabled = false
            return
        }
        remote.isEnabled = true
        targets[command] = remote.addTarget { event in
            // Command centre targets are called on the main thread.
            MainActor.assumeIsolated { handler(Self.payload(of: event)) }
        }
    }

    func setSkipIntervals(forward: TimeInterval, backward: TimeInterval) {
        center.skipForwardCommand.preferredIntervals = [NSNumber(value: forward)]
        center.skipBackwardCommand.preferredIntervals = [NSNumber(value: backward)]
    }

    private func command(_ command: RemoteCommand) -> MPRemoteCommand {
        switch command {
        case .play: center.playCommand
        case .pause: center.pauseCommand
        case .togglePlayPause: center.togglePlayPauseCommand
        case .nextTrack: center.nextTrackCommand
        case .previousTrack: center.previousTrackCommand
        case .changePlaybackPosition: center.changePlaybackPositionCommand
        case .skipForward: center.skipForwardCommand
        case .skipBackward: center.skipBackwardCommand
        }
    }

    private nonisolated static func payload(of event: MPRemoteCommandEvent) -> RemoteCommandEvent {
        if let event = event as? MPChangePlaybackPositionCommandEvent { return .position(event.positionTime) }
        if let event = event as? MPSkipIntervalCommandEvent { return .interval(event.interval) }
        return .plain
    }
}
