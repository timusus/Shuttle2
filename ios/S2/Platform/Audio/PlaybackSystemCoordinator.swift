import Foundation
import os
import Shared

/// Connects the system's playback surfaces to the Kotlin player (phase 6, #588): Now Playing (the lock screen and
/// Control Center's metadata, position and transport commands) and the widgets' copy of it. The audio session is the
/// `Player`'s (`KitAudioPlayer`). It keeps no playback state of its own: it follows the Kotlin flows and calls
/// `PlaybackOperations`, so every command goes through the same policy as the app's own buttons.
///
/// Whether it plays, for Now Playing's rate, is the listener's intent (`PlayIntent`), so the lock screen shows pause
/// from the tap; the transport commands go through the intent too.
///
/// Any of the flows changing republishes Now Playing from all of their current values. Each flow is
/// followed by its own task, and the tasks resume in no particular order: a copy of each flow's last
/// value would pair a new song with the last one's position on a track change (#691).
@MainActor
final class PlaybackSystemCoordinator: NowPlayingCommandHandler {
    /// Audiobook and podcast songs skip by these (seconds) instead of between songs, as on Android.
    static let skipForwardSeconds: TimeInterval = 30
    static let skipBackwardSeconds: TimeInterval = 10

    private let playback: IosPlayerController
    private let intent: PlayIntent
    private let nowPlaying: NowPlayingController
    /// The widgets' copy of Now Playing (#758); nil in tests that don't look at it.
    private let widgets: NowPlayingWidgetPublisher?
    private var observers: [Task<Void, Never>] = []
    private var intentListener: Int?
    /// Remote skips and seeks; plays and pauses are logged by `PlayIntent` with their source (#715, #897).
    private let log = Logger(subsystem: "com.simplecityapps.shuttle2", category: "session")

    init(
        playback: IosPlayerController,
        intent: PlayIntent,
        nowPlaying: NowPlayingController,
        widgets: NowPlayingWidgetPublisher? = nil
    ) {
        self.playback = playback
        self.intent = intent
        self.nowPlaying = nowPlaying
        self.widgets = widgets
    }

    /// Registers the remote commands and starts following the player.
    func start() {
        nowPlaying.start(handler: self)
        intentListener = intent.addListener { [weak self] in self?.publish() }
        observe()
    }

    /// Stops following the player and clears Now Playing.
    func stop() {
        observers.forEach { $0.cancel() }
        observers = []
        if let intentListener { intent.removeListener(intentListener) }
        intentListener = nil
        nowPlaying.stop()
    }

    private func observe() {
        let playback = playback
        observers = [
            Task { [weak self] in
                for await _ in playback.playbackStateFlow {
                    self?.publish()
                }
            },
            Task { [weak self] in
                for await _ in playback.playWhenReadyFlow {
                    self?.publish()
                }
            },
            Task { [weak self] in
                for await _ in playback.progressFlow {
                    self?.publish()
                }
            },
            Task { [weak self] in
                for await _ in playback.bufferingFlow {
                    self?.publish()
                }
            },
            Task { [weak self] in
                for await _ in playback.playbackSpeedFlow {
                    self?.publish()
                }
            },
            Task { [weak self] in
                for await _ in playback.queueOperations.queueStateFlow {
                    self?.publish()
                }
            },
        ]
    }

    // MARK: - Following the player

    /// Publishes the player as it is now: the current song, its position and whether it plays.
    private func publish() {
        guard let current = playback.queueOperations.queueStateFlow.value.currentItem else {
            widgets?.update(item: nil, isPlaying: false)
            guard nowPlaying.item != nil else { return }
            nowPlaying.setItem(nil, position: 0, isPlaying: false, speed: 1)
            return
        }
        let progress = playback.progressFlow.value
        let item = Self.nowPlayingItem(current, progress: progress)
        let position = TimeInterval(progress?.position ?? 0) / 1000
        // Read straight from the player, not `isPlayIntended`, which lags it by a hop through an observer task.
        let isPlaying = intent.wantsPlayback
        // A stalled stream stops the lock screen's clock; a load's doesn't, so a skip holds its pause button (#691).
        let isBuffering = playback.bufferingFlow.value.boolValue
        let speed = playback.playbackSpeedFlow.value.floatValue
        widgets?.update(item: item, isPlaying: isPlaying)
        nowPlaying.setSkipMode(
            current.song.type == .audio
                ? .tracks : .interval(forward: Self.skipForwardSeconds, backward: Self.skipBackwardSeconds)
        )
        if item == nowPlaying.item {
            nowPlaying.updatePlayback(position: position, isPlaying: isPlaying, isBuffering: isBuffering, speed: speed)
        } else {
            nowPlaying.setItem(item, position: position, isPlaying: isPlaying, isBuffering: isBuffering, speed: speed)
        }
    }

    /// `current` as Now Playing shows it. Its duration is the song's, or the player's for a song without
    /// one, as the in-app player has it.
    static func nowPlayingItem(_ current: QueueItem, progress: PlaybackProgress?) -> NowPlayingItem {
        let song = current.song
        let durationMs = song.duration > 0 ? song.duration : (progress?.duration ?? 0)
        return NowPlayingItem(
            id: String(current.uid),
            title: song.name ?? "",
            artist: song.friendlyArtistName,
            album: song.album,
            duration: TimeInterval(max(0, durationMs)) / 1000,
            artwork: .song(song)
        )
    }

    // MARK: - NowPlayingCommandHandler

    func play() {
        intent.play(from: .remoteCommand)
    }

    func pause() {
        intent.pause(from: .remoteCommand)
    }

    func togglePlayPause() {
        intent.toggle(from: .remoteCommand)
    }

    func skipToNext() {
        log.notice("remote skipToNext")
        intent.listenerPlayed(from: .remoteCommand)
        playback.skipToNext(ignoreRepeat: true, completion: nil)
    }

    func skipToPrevious() {
        log.notice("remote skipToPrevious")
        intent.listenerPlayed(from: .remoteCommand)
        playback.skipToPrev(force: false, completion: nil)
    }

    func seek(to position: TimeInterval) {
        log.notice("remote seek to \(Int(position)) s")
        playback.seekTo(position: Int32(max(0, position) * 1000))
    }

    func skip(by interval: TimeInterval) {
        let now = TimeInterval(playback.getProgress()?.int32Value ?? 0) / 1000
        seek(to: now + interval)
    }
}
