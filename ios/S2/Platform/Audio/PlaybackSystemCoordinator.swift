import Foundation
import Shared

/// Connects the system's playback surfaces to the Kotlin player (phase 6, #588): the audio session
/// (interruptions, unplugged headphones, a media-services reset) and Now Playing (the lock screen and
/// Control Center's metadata, position and transport commands). It keeps no playback state of its own:
/// it follows the Kotlin flows and calls `PlaybackOperations`, so every command goes through the same
/// policy as the app's own buttons.
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

    /// As last published to Now Playing.
    private var isPlaying = false
    private var positionMs: Int32 = 0
    private var speed: Float = 1
    private var item: NowPlayingItem?

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
        // TODO(#588): the output sample rate reaches the EQ once the equalizer is shared
        // (`onOutputSampleRateChanged` recomputes its coefficients); nothing consumes it yet.
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
                for await state in playback.playbackStateFlow {
                    self?.playbackStateChanged(state)
                }
            },
            Task { [weak self] in
                for await progress in playback.progressFlow {
                    self?.progressChanged(progress)
                }
            },
            Task { [weak self] in
                for await speed in playback.playbackSpeedFlow {
                    self?.speedChanged(speed.floatValue)
                }
            },
            Task { [weak self] in
                for await queue in playback.queueOperations.queueStateFlow {
                    self?.queueChanged(queue)
                }
            },
        ]
    }

    // MARK: - Following the player

    private func playbackStateChanged(_ state: PlaybackState) {
        isPlaying = state is PlaybackState.Playing
        publishPlayback()
    }

    private func progressChanged(_ progress: PlaybackProgress?) {
        positionMs = progress?.position ?? 0
        publishPlayback()
    }

    private func speedChanged(_ speed: Float) {
        self.speed = speed
        publishPlayback()
    }

    private func queueChanged(_ queue: QueueState) {
        guard let current = queue.currentItem else {
            guard item != nil else { return }
            // The queue emptied: nothing is playing, so give the session back to other apps.
            item = nil
            nowPlaying.setItem(nil, position: 0, isPlaying: false, speed: speed)
            session.deactivate()
            return
        }
        let song = current.song
        let newItem = NowPlayingItem(
            id: String(current.uid),
            title: song.name ?? "",
            artist: song.friendlyArtistName,
            album: song.album,
            duration: TimeInterval(song.duration) / 1000
        )
        nowPlaying.setSkipMode(
            song.type == .audio ? .tracks : .interval(forward: Self.skipForwardSeconds, backward: Self.skipBackwardSeconds)
        )
        guard newItem != item else { return }
        item = newItem
        nowPlaying.setItem(newItem, position: seconds(positionMs), isPlaying: isPlaying, speed: speed)
    }

    private func publishPlayback() {
        nowPlaying.updatePlayback(position: seconds(positionMs), isPlaying: isPlaying, speed: speed)
    }

    private func seconds(_ ms: Int32) -> TimeInterval {
        TimeInterval(ms) / 1000
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
        let now = TimeInterval(playback.getProgress()?.int32Value ?? positionMs) / 1000
        seek(to: now + interval)
    }
}
