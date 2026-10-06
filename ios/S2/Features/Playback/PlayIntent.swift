import Foundation
import Observation
import os
import Shared

/// The player `PlayIntent` reads and commands: the Kotlin `IosPlayerController`, or a fake in tests.
protocol PlayIntentPlayer: AnyObject {
    /// The player's own intent: it plays the current item once that's ready (`playWhenReadyFlow`).
    var playsWhenReady: Bool { get }
    /// Audio is playing (`PlaybackState.Playing`).
    var isAudible: Bool { get }
    func play()
    func pause()
}

extension IosPlayerController: PlayIntentPlayer {
    var playsWhenReady: Bool { playWhenReadyFlow.value.boolValue }
    var isAudible: Bool { playbackStateFlow.value is PlaybackState.Playing }
}

/// Whether the listener wants playback running: what every play/pause control draws (the mini player, Now Playing,
/// the lock screen's rate, Home's resume card), after Shuttle Podcasts' `isPlayIntended`. The transport follows what
/// was asked for, not the pipeline, so a tap shows pause in the frame it happened, and `isLoading` spins while the
/// play is wanted but no audio is out yet: the songs being read, a server stream opening, the engine buffering.
///
/// The Kotlin player's `playWhenReady` is the intent once it holds the queue, and audio playing always counts. What it
/// can't see is a play still being prepared in Swift's hands: a `MediaAction` that reads its songs and builds the
/// queue before it loads and plays (`load` even clears `playWhenReady` first). So such a play is pending from the tap
/// (`begin`) until the player takes it up: it intends to play after the action's own result (`finished`), which for a
/// shuffle comes before its load does, or audio is out once the player has stopped for it, which also settles a play
/// whose result never comes (its screen went, cancelling it). A failed result or `timeout` ends it too. A pause clears
/// the intent at once, a pending play's included; if that play goes on to start anyway, it's paused as it does.
@MainActor
@Observable
final class PlayIntent {
    /// The listener wants playback running: a play is pending, the player intends to play, or it's playing.
    private(set) var isPlayIntended = false
    /// Play is intended but no audio is out yet, or a new play is pending over what's still playing.
    private(set) var isLoading = false
    /// The key `begin` was given for the play under way (Home's `HomeItem.key`), while it's loading; nil otherwise.
    private(set) var loadingKey: String?

    /// A play `begin` dispatched that the player hasn't taken up yet.
    private struct Pending {
        let ticket: Int
        /// The listener paused it: if it starts after all, it's paused again.
        var isPaused = false
        /// Its action came back successfully, so the player's next play is this one.
        var succeeded = false
        /// The player has been seen neither intending nor playing since the tap, so audio from here on is this play's.
        var playerStopped = false
    }

    @ObservationIgnored private let player: any PlayIntentPlayer
    @ObservationIgnored let timeout: Duration
    /// The last `begin`'s ticket: a result for any other is a superseded action's.
    @ObservationIgnored private var ticket = 0
    /// The last play or pause asked of the player, and who asked: what the `playback` log says (#897).
    @ObservationIgnored private(set) var lastCommand: Command?
    @ObservationIgnored private let log = Logger(subsystem: "com.simplecityapps.shuttle2", category: "playback")
    @ObservationIgnored private var pending: Pending?
    @ObservationIgnored private var key: String?
    @ObservationIgnored private var timeoutTask: Task<Void, Never>?
    @ObservationIgnored private var listeners: [Int: () -> Void] = [:]
    @ObservationIgnored private var nextListener = 0
    /// `nonisolated(unsafe)`: only `deinit` touches them off the main actor, once no other access can be concurrent.
    @ObservationIgnored private nonisolated(unsafe) var observers: [Task<Void, Never>] = []

    init(player: any PlayIntentPlayer, timeout: Duration = .seconds(30)) {
        self.player = player
        self.timeout = timeout
        refresh()
    }

    /// An intent over the Kotlin player, following its flows.
    convenience init(following controller: IosPlayerController, timeout: Duration = .seconds(30)) {
        self.init(player: controller, timeout: timeout)
        let state = controller.playbackStateFlow
        let playWhenReady = controller.playWhenReadyFlow
        observers = [
            Task { [weak self] in
                for await _ in state { self?.playerChanged() }
            },
            Task { [weak self] in
                for await _ in playWhenReady { self?.playerChanged() }
            },
        ]
    }

    deinit {
        observers.forEach { $0.cancel() }
    }

    /// Whether the listener wants playback running, read straight from the player rather than from
    /// `isPlayIntended`, which trails it by a hop through the flow observers: for the system surfaces (#691).
    var wantsPlayback: Bool {
        pending?.isPaused == false || player.playsWhenReady || player.isAudible
    }

    // MARK: - Commands

    /// Who asked for a play or a pause.
    enum Source: String {
        case user
        case remoteCommand = "remote command"
        case interruption
        case routeChange = "route change"
        /// Siri, Shortcuts, a widget's button or a Control Center control (#758).
        case appIntent = "app intent"
    }

    struct Command: Equatable {
        let plays: Bool
        let source: Source
    }

    /// Plays (or resumes) the current item, as the listener asked: a pending play they paused no longer stands.
    func play(from source: Source = .user) {
        record(plays: true, source)
        listenerPlayed()
        player.play()
        refresh()
    }

    /// Resumes for the system (an interruption ending), not the listener: a pending play they paused stays paused.
    func resume(from source: Source) {
        guard pending?.isPaused != true else {
            log.notice("resume from \(source.rawValue, privacy: .public) skipped: the listener paused")
            return
        }
        record(plays: true, source)
        player.play()
        refresh()
    }

    /// Pauses, cancelling a pending play.
    func pause(from source: Source = .user) {
        record(plays: false, source)
        pending?.isPaused = true
        player.pause()
        refresh()
    }

    /// Pauses when play is intended, else plays: a play/pause button, whose second tap during a load pauses it.
    func toggle(from source: Source = .user) {
        if wantsPlayback { pause(from: source) } else { play(from: source) }
    }

    private func record(plays: Bool, _ source: Source) {
        lastCommand = Command(plays: plays, source: source)
        log.notice("\(plays ? "play" : "pause", privacy: .public) from \(source.rawValue, privacy: .public)")
    }

    /// The listener started a play some other way (a skip, a queue row): a pending play they paused, should it start
    /// later, is no longer paused over this one.
    func listenerPlayed() {
        guard pending?.isPaused == true else { return }
        endPending()
        refresh()
    }

    /// A play is about to be dispatched that Swift prepares before the player has it (a `MediaAction`); pass the
    /// returned ticket to `finished` with its result. `key` names what it plays, for `loadingKey`.
    @discardableResult
    func begin(key: String? = nil) -> Int {
        ticket += 1
        pending = Pending(ticket: ticket)
        self.key = key
        timeoutTask?.cancel()
        timeoutTask = Task { [weak self, ticket, timeout] in
            try? await Task.sleep(for: timeout)
            guard !Task.isCancelled, let self, pending?.ticket == ticket else { return }
            endPending()
            refresh()
        }
        refresh()
        return ticket
    }

    /// The action `begin` handed out `ticket` for came back with `result`: the player has the play, or will once it's
    /// loaded (a shuffle), or the play failed.
    func finished(_ ticket: Int, result: any MediaActionResult) {
        guard pending?.ticket == ticket else { return }
        if Self.isFailure(result) {
            endPending()
        } else {
            pending?.succeeded = true
        }
        refresh()
    }

    /// The player's state changed.
    func playerChanged() {
        refresh()
    }

    /// Calls `onChange` whenever the intent or its loading changes; `removeListener` with the token stops it.
    @discardableResult
    func addListener(_ onChange: @escaping () -> Void) -> Int {
        nextListener += 1
        listeners[nextListener] = onChange
        return nextListener
    }

    func removeListener(_ token: Int) {
        listeners[token] = nil
    }

    // MARK: - Helpers

    /// Whether `action` plays: Play, Shuffle and Resume.
    static func plays(_ action: any MediaAction) -> Bool {
        action is MediaActionPlay || action is MediaActionShuffle || action is MediaActionResume
    }

    /// Whether `result` of an action is a play that didn't happen. Anything else (a notice that something was queued,
    /// a navigation) says nothing about the pending play.
    static func isFailure(_ result: any MediaActionResult) -> Bool {
        guard let message = (result as? MediaActionResultMessage)?.message else { return false }
        return message is MediaActionMessagePlaybackFailed || message is MediaActionMessageNoSongs
    }

    private func endPending() {
        pending = nil
        timeoutTask?.cancel()
        timeoutTask = nil
    }

    /// Ends a pending play the player has taken up: it intends to play after the action's result, or audio is out
    /// after it stopped for the play. One the listener paused is paused again as it starts.
    private func settlePending() {
        guard var pending else { return }
        let starting = player.playsWhenReady || player.isAudible
        if !starting { pending.playerStopped = true }
        self.pending = pending
        let takenUp = pending.isPaused
            ? starting && (pending.succeeded || pending.playerStopped)
            : (starting && pending.succeeded) || (player.isAudible && pending.playerStopped)
        guard takenUp else { return }
        endPending()
        if pending.isPaused { player.pause() }
    }

    private func refresh() {
        settlePending()
        let intended = wantsPlayback
        let loading = intended && (pending != nil || !player.isAudible)
        if !loading { key = nil }
        let loadingKey = loading ? key : nil
        guard intended != isPlayIntended || loading != isLoading || loadingKey != self.loadingKey else { return }
        isPlayIntended = intended
        isLoading = loading
        self.loadingKey = loadingKey
        listeners.values.forEach { $0() }
    }
}

extension MediaActionsViewModel {
    /// Dispatches `action`. One that plays (`PlayIntent.plays`) is the listener's intent from this call, until the
    /// player takes it up (`PlayIntent.begin`); `key` names what it plays, for a spinner on that item.
    @MainActor
    func send(_ action: any MediaAction, key: String? = nil, intent: PlayIntent? = nil) {
        SiriDonation.donate(action)
        guard PlayIntent.plays(action) else {
            dispatch(action: action)
            return
        }
        let intent = intent ?? AppGraph.dependencies.playIntent
        let ticket = intent.begin(key: key)
        // Kotlin hands the result back on the main thread.
        dispatch(action: action) { result in
            MainActor.assumeIsolated { intent.finished(ticket, result: result) }
        }
    }
}
