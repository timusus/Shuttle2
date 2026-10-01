import Foundation
import Shared

/// Connects the system's playback surfaces to the Kotlin player (phase 6, #588): the audio session
/// (interruptions, unplugged headphones, a media-services reset) and Now Playing (the lock screen and
/// Control Center's metadata, position and transport commands). It keeps no playback state of its own:
/// it follows the Kotlin flows and calls `PlaybackOperations`, so every command goes through the same
/// policy as the app's own buttons.
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
    private let player: EngineAudioPlayer
    private let session: AudioSessionController
    private let nowPlaying: NowPlayingController
    /// A new engine, for a media-services reset; nil if one can't be built.
    private let makeEngine: () -> AudioEngine?
    private var observers: [Task<Void, Never>] = []

    init(
        playback: IosPlayerController,
        player: EngineAudioPlayer,
        session: AudioSessionController,
        nowPlaying: NowPlayingController,
        makeEngine: @escaping () -> AudioEngine?
    ) {
        self.playback = playback
        self.player = player
        self.session = session
        self.nowPlaying = nowPlaying
        self.makeEngine = makeEngine
    }

    /// Configures the session, registers the remote commands and starts following the player.
    func start() {
        do {
            try session.configure()
        } catch {
            NSLog("S2: audio session configure failed: \(error)")
        }
        session.isPlaying = { [weak self] in self?.playerIsPlaying ?? false }
        session.onPause = { [weak self] _ in self?.playback.pause() }
        session.onResume = { [weak self] in self?.playback.play() }
        session.onMediaServicesReset = { [weak self] in self?.rebuildEngine() }
        player.onWillPlay = { [weak session] in
            MainActor.assumeIsolated {
                do {
                    try session?.activate()
                } catch {
                    NSLog("S2: audio session activate failed: \(error)")
                }
            }
        }
        player.onPaused = { [weak session] in MainActor.assumeIsolated { session?.playbackPaused() } }
        nowPlaying.start(handler: self)
        observe()
    }

    /// Stops following the player and clears Now Playing.
    func stop() {
        observers.forEach { $0.cancel() }
        observers = []
        nowPlaying.stop()
        player.onWillPlay = {}
        player.onPaused = {}
    }

    /// Read from the flow's value, not `isPlaying`, which lags it by a hop through the observer task.
    private var playerIsPlaying: Bool {
        playback.playbackStateFlow.value is PlaybackState.Playing
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
                for await _ in playback.progressFlow {
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
            guard nowPlaying.item != nil else { return }
            // The queue emptied: nothing is playing, so give the session back to other apps.
            nowPlaying.setItem(nil, position: 0, isPlaying: false, speed: 1)
            session.deactivate()
            return
        }
        let progress = playback.progressFlow.value
        let item = Self.nowPlayingItem(current, progress: progress)
        let position = TimeInterval(progress?.position ?? 0) / 1000
        let isPlaying = playerIsPlaying
        let speed = playback.playbackSpeedFlow.value.floatValue
        nowPlaying.setSkipMode(
            current.song.type == .audio
                ? .tracks : .interval(forward: Self.skipForwardSeconds, backward: Self.skipBackwardSeconds)
        )
        if item == nowPlaying.item {
            nowPlaying.updatePlayback(position: position, isPlaying: isPlaying, speed: speed)
        } else {
            nowPlaying.setItem(item, position: position, isPlaying: isPlaying, speed: speed)
        }
    }

    /// `current` as Now Playing shows it.
    static func nowPlayingItem(_ current: QueueItem, progress: PlaybackProgress?) -> NowPlayingItem {
        let song = current.song
        return NowPlayingItem(
            id: String(current.uid),
            title: song.name ?? "",
            artist: song.friendlyArtistName,
            album: song.album,
            duration: TimeInterval(song.duration) / 1000,
            artwork: .song(song)
        )
    }

    /// Every audio object died with the media server: a new engine, and the current item loaded into it
    /// where it was, playing on if it was.
    private func rebuildEngine() {
        guard let engine = makeEngine() else { return }
        let wasPlaying = playerIsPlaying
        let position = playback.getProgress()
        player.replaceEngine(engine)
        playback.load(seekPosition: position, skipUnloadable: false) { _ in }
        if wasPlaying { playback.play() }
    }

    // MARK: - NowPlayingCommandHandler

    func play() {
        playback.play()
    }

    func pause() {
        playback.pause()
    }

    func togglePlayPause() {
        playback.togglePlayback()
    }

    func skipToNext() {
        playback.skipToNext(ignoreRepeat: true, completion: nil)
    }

    func skipToPrevious() {
        playback.skipToPrev(force: false, completion: nil)
    }

    func seek(to position: TimeInterval) {
        playback.seekTo(position: Int32(max(0, position) * 1000))
    }

    func skip(by interval: TimeInterval) {
        let now = TimeInterval(playback.getProgress()?.int32Value ?? 0) / 1000
        seek(to: now + interval)
    }
}
