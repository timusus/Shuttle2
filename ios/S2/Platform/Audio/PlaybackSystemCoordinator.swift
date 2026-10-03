import Foundation
import os
import Shared

/// Connects the system's playback surfaces to the Kotlin player (phase 6, #588): the audio session
/// (interruptions, unplugged headphones, a media-services reset) and Now Playing (the lock screen and
/// Control Center's metadata, position and transport commands). It keeps no playback state of its own:
/// it follows the Kotlin flows and calls `PlaybackOperations`, so every command goes through the same
/// policy as the app's own buttons.
///
/// Whether it plays, for Now Playing's rate and an interruption's resume, is the listener's intent (`PlayIntent`), so
/// the lock screen shows pause from the tap; the transport commands and the session's pauses go through the intent too.
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
    private let player: EngineAudioPlayer
    private let session: AudioSessionController
    private let nowPlaying: NowPlayingController
    /// A new engine, for a media-services reset; nil if one can't be built.
    private let makeEngine: () -> AudioEngine?
    private var observers: [Task<Void, Never>] = []
    private var intentListener: Int?
    /// Where each pause and resume came from: the session or a remote command (#715).
    private let log = Logger(subsystem: "com.simplecityapps.shuttle2", category: "AudioSession")

    init(
        playback: IosPlayerController,
        intent: PlayIntent,
        player: EngineAudioPlayer,
        session: AudioSessionController,
        nowPlaying: NowPlayingController,
        makeEngine: @escaping () -> AudioEngine?
    ) {
        self.playback = playback
        self.intent = intent
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
        session.onPause = { [weak self] reason in
            self?.log.notice("pause from the session: \(String(describing: reason), privacy: .public)")
            self?.intent.pause()
        }
        session.onResume = { [weak self] in
            self?.log.notice("resume from the session")
            self?.intent.resume()
        }
        session.onMediaServicesReset = { [weak self] in self?.rebuildEngine() }
        player.onWillPlay = { [weak session] in
            MainActor.assumeIsolated {
                do {
                    try session?.activate()
                    return true
                } catch {
                    NSLog("S2: audio session activate failed, not playing: \(error)")
                    return false
                }
            }
        }
        player.onPaused = { [weak session] in MainActor.assumeIsolated { session?.playbackPaused() } }
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
        player.onWillPlay = { true }
        player.onPaused = {}
    }

    /// The listener's intent, read straight from the player (`PlayIntent.wantsPlayback`), not `isPlaying`, which lags
    /// it by a hop through an observer task. Playing, loading or being prepared with the intent to play. An idle, ended
    /// or failed track is paused and must not advertise playback — including to an interruption, which resumes only
    /// when this was true.
    private var playerIsPlaying: Bool {
        intent.wantsPlayback
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
    /// `force` writes the item again, artwork included, instead of `updatePlayback`'s throttled position update.
    private func publish(force: Bool = false) {
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
        if !force, item == nowPlaying.item {
            nowPlaying.updatePlayback(position: position, isPlaying: isPlaying, speed: speed)
        } else {
            nowPlaying.setItem(item, position: position, isPlaying: isPlaying, speed: speed)
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

    /// Every audio object died with the media server: a new engine, and the current item loaded into it
    /// where it was. Playing — or still loading with the intent to play — resumes; paused stays paused.
    /// Now Playing is written in full even when the item and state didn't change: the system copy was wiped
    /// with the server, and `updatePlayback` would throttle an unchanged paused or loading item.
    private func rebuildEngine() {
        guard let engine = makeEngine() else { return }
        let resume = playerIsPlaying
        let position = playback.getProgress()
        player.replaceEngine(engine)
        playback.load(seekPosition: position, skipUnloadable: false) { [weak self] _ in
            MainActor.assumeIsolated { self?.publish(force: true) }
        }
        if resume { intent.resume() }
        publish(force: true)
    }

    // MARK: - NowPlayingCommandHandler

    func play() {
        log.notice("remote play")
        intent.play()
    }

    func pause() {
        log.notice("remote pause")
        intent.pause()
    }

    func togglePlayPause() {
        log.notice("remote togglePlayPause, playing \(self.playerIsPlaying)")
        intent.toggle()
    }

    func skipToNext() {
        intent.listenerPlayed()
        playback.skipToNext(ignoreRepeat: true, completion: nil)
    }

    func skipToPrevious() {
        intent.listenerPlayed()
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
