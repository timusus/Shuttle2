import Foundation
import Observation
import os
import PlaybackPlayer
import Shared

/// The `Player` as the lock screen, Control Center and CarPlay drive it (AudioPlaybackKit's `NowPlayingSession`): every
/// remote command goes through the Kotlin `IosPlayerController` and `PlayIntent`, so it meets the same policy as the
/// app's own buttons (restart-vs-previous, next wrapping the queue); the Player then hears it from Kotlin. What it shows
/// is the Player's, except whether it plays, which is the listener's intent, so the button follows a tap at once.
@MainActor
@Observable
final class NowPlayingPlayer: ForwardingNowPlayingControllable {
    typealias Item = KitAudioPlayer.QueueKey

    /// Audiobook and podcast songs skip by these (seconds) instead of between songs, as on Android.
    static let skipIntervals = SkipIntervals(forward: 30, backward: 10)

    @ObservationIgnored let player: Player<KitAudioPlayer.QueueKey>
    @ObservationIgnored private let playback: IosPlayerController
    @ObservationIgnored private let intent: PlayIntent
    /// Remote skips and seeks; plays and pauses are logged by `PlayIntent` with their source (#715, #897).
    @ObservationIgnored private let log = Logger(subsystem: "com.simplecityapps.shuttle2", category: "session")

    init(player: Player<KitAudioPlayer.QueueKey>, playback: IosPlayerController, intent: PlayIntent) {
        self.player = player
        self.playback = playback
        self.intent = intent
    }

    /// The song queued under `key`, while the queue still has it.
    func song(for key: KitAudioPlayer.QueueKey) -> Song? {
        playback.queueOperations.queueStateFlow.value.items.first { $0.uid == key.uid }?.song
    }

    var playWhenReady: Bool { intent.isPlayIntended }

    /// The song's, as the in-app player shows it; the decoder's for a song without one.
    var duration: TimeInterval? {
        guard let key = player.currentItem, let song = song(for: key), song.duration > 0 else { return player.duration }
        return TimeInterval(song.duration) / 1000
    }

    // Kotlin's next wraps past the end of the queue, so it stays available where the Player's would not.
    var availableCommands: PlayerCommands {
        player.currentItem == nil ? player.availableCommands : player.availableCommands.union(.seekToNext)
    }

    var skipIntervals: SkipIntervals? {
        guard let key = player.currentItem, let song = song(for: key), song.type != .audio else { return nil }
        return Self.skipIntervals
    }

    var rate: Double {
        get { player.rate }
        set { playback.setPlaybackSpeed(multiplier: Float(newValue)) }
    }

    func play() {
        intent.play(from: .remoteCommand)
    }

    func pause() {
        intent.pause(from: .remoteCommand)
    }

    func seekToNext() {
        log.notice("remote skipToNext")
        intent.listenerPlayed(from: .remoteCommand)
        playback.skipToNext(ignoreRepeat: true, completion: nil)
    }

    func seekToPrevious() {
        log.notice("remote skipToPrevious")
        intent.listenerPlayed(from: .remoteCommand)
        playback.skipToPrev(force: false, completion: nil)
    }

    func seek(to position: TimeInterval) {
        log.notice("remote seek to \(Int(position)) s")
        playback.seekTo(position: Int32(max(0, position) * 1000))
    }

    func seek(by delta: TimeInterval) {
        let now = TimeInterval(playback.getProgress()?.int32Value ?? 0) / 1000
        seek(to: now + delta)
    }
}
