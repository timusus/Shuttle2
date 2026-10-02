import Foundation
import Observation
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
/// (`begin`) until the action's own result comes back (`finished`), when the player has either taken it up or the
/// play failed; `timeout` is the fallback if no result does. A pause clears the intent at once, a pending play's
/// included; if that play goes on to start anyway, it's paused as it does.
@MainActor
@Observable
final class PlayIntent {
    /// The listener wants playback running: a play is pending, the player intends to play, or it's playing.
    private(set) var isPlayIntended = false
    /// Play is intended but no audio is out yet, or a new play is pending over what's still playing.
    private(set) var isLoading = false
    /// The key `begin` was given for the play under way (Home's `HomeItem.key`), while it's loading; nil otherwise.
    private(set) var loadingKey: String?

    @ObservationIgnored private let player: any PlayIntentPlayer
    @ObservationIgnored let timeout: Duration
    /// The last `begin`'s ticket: a result for any other is a superseded action's.
    @ObservationIgnored private var ticket = 0
    /// The ticket of the play that's pending; nil when none is.
    @ObservationIgnored private var pending: Int?
    /// The ticket of a pending play the listener paused: if it starts after all, it's paused again.
    @ObservationIgnored private var cancelled: Int?
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
        pending != nil || player.playsWhenReady || player.isAudible
    }

    // MARK: - Commands

    /// Plays (or resumes) the current item.
    func play() {
        cancelled = nil
        player.play()
        refresh()
    }

    /// Pauses, cancelling a pending play.
    func pause() {
        if let pending {
            cancelled = pending
            endPending()
        }
        player.pause()
        refresh()
    }

    /// Pauses when play is intended, else plays: a play/pause button, whose second tap during a load pauses it.
    func toggle() {
        if wantsPlayback { pause() } else { play() }
    }

    /// A play is about to be dispatched that Swift prepares before the player has it (a `MediaAction`); pass the
    /// returned ticket to `finished` with its result. `key` names what it plays, for `loadingKey`.
    @discardableResult
    func begin(key: String? = nil) -> Int {
        ticket += 1
        pending = ticket
        cancelled = nil
        self.key = key
        timeoutTask?.cancel()
        timeoutTask = Task { [weak self, ticket, timeout] in
            try? await Task.sleep(for: timeout)
            guard !Task.isCancelled, let self, pending == ticket else { return }
            endPending()
            refresh()
        }
        refresh()
        return ticket
    }

    /// The action `begin` handed out `ticket` for came back with `result`: the player has the play now, or it failed.
    func finished(_ ticket: Int, result: any MediaActionResult) {
        if ticket == cancelled {
            // Paused while pending, and started anyway: the pause stands.
            cancelled = nil
            if !Self.isFailure(result) { player.pause() }
        } else if ticket == pending {
            endPending()
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

    private func refresh() {
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
    /// Dispatches `action`. One that plays (`PlayIntent.plays`) is the listener's intent from this call, until its own
    /// result (`PlayIntent.begin`); `key` names what it plays, for a spinner on that item.
    @MainActor
    func send(_ action: any MediaAction, key: String? = nil, intent: PlayIntent? = nil) {
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
