import Shared
import SwiftUI

/// What the player surfaces (mini player, Now Playing, its queue) draw, as plain values. The views take this
/// and `PlayerActions`, never the shared `PlayerViewModel` itself: `PlayerBinding` at the bottom of this file maps
/// the ViewModel's state and actions onto them.
struct NowPlayingState: Equatable {
    var title: String?
    var artist: String?
    var album: String?
    var artwork: ArtworkSource?
    var isPlaying = false
    var positionMs = 0
    var durationMs = 0
    var queue: [NowPlayingQueueRow] = []
    var shuffleOn = false
    var repeatMode: NowPlayingRepeat = .off
    /// 1 being normal speed.
    var playbackSpeed: Float = 1
    var sleepTimerActive = false

    /// Nothing queued.
    static let idle = NowPlayingState()
}

/// One row in the queue list.
struct NowPlayingQueueRow: Identifiable, Equatable {
    let id: Int64
    let title: String
    let artist: String?
    let artwork: ArtworkSource?
    let isCurrent: Bool

    init(id: Int64, title: String, artist: String?, artwork: ArtworkSource? = nil, isCurrent: Bool) {
        self.id = id
        self.title = title
        self.artist = artist
        self.artwork = artwork
        self.isCurrent = isCurrent
    }
}

/// What the mini player draws: only the fields it shows, so reading it doesn't subscribe the bar to
/// `PlayerBinding`'s progress ticks or queue changes (`@Observable` tracks each property a body reads).
struct MiniPlayerState: Equatable {
    var title: String?
    var artist: String?
    var artwork: ArtworkSource?
    var isPlaying = false
}

/// Holds a seek's target as the displayed position until the player reports a position near it, so the
/// scrubber doesn't jump back to the stale position between releasing it and the next progress tick. Gives
/// up after `timeout`, in case the seek never lands (a failed or clamped seek).
struct SeekHold: Equatable {
    /// How close a reported position must come to the target to count as the seek having landed.
    static let toleranceMs = 1500
    static let timeout: TimeInterval = 2

    private(set) var targetMs: Int?
    private var deadline: Date?

    mutating func begin(_ targetMs: Int, now: Date = Date()) {
        self.targetMs = targetMs
        deadline = now.addingTimeInterval(Self.timeout)
    }

    /// The position to show for a `reportedMs` from the player; releases the hold once the report has
    /// caught up with the target or the timeout has passed.
    mutating func displayed(reportedMs: Int, now: Date = Date()) -> Int {
        guard let targetMs, let deadline else { return reportedMs }
        if abs(reportedMs - targetMs) <= Self.toleranceMs || now >= deadline {
            self.targetMs = nil
            self.deadline = nil
            return reportedMs
        }
        return targetMs
    }
}

/// The queue's repeat mode, as the repeat button cycles it.
enum NowPlayingRepeat: Equatable {
    case off
    case all
    case one

    init(_ mode: S2RepeatMode) {
        switch mode {
        case .one: self = .one
        case .all: self = .all
        default: self = .off
        }
    }
}

/// The commands the player surfaces send.
struct PlayerActions {
    var playPause: () -> Void = {}
    var next: () -> Void = {}
    var previous: () -> Void = {}
    /// Seeks the current song to a position in milliseconds.
    var seek: (Int) -> Void = { _ in }
    /// Skips to the queue row at this index.
    var selectQueueItem: (Int) -> Void = { _ in }
    var toggleShuffle: () -> Void = {}
    /// Steps repeat through off, all, one.
    var toggleRepeat: () -> Void = {}
    /// Sets the playback speed, 1 being normal.
    var setSpeed: (Float) -> Void = { _ in }
    /// Starts the sleep timer, pausing playback after this many minutes.
    var startSleepTimer: (Int) -> Void = { _ in }
    var stopSleepTimer: () -> Void = {}

    /// Does nothing: previews and tests.
    static let none = PlayerActions()
}

// MARK: - The binding

/// The mini player's and Now Playing's state and commands, off the shared `PlayerViewModel` (#586, #587): its
/// `uiState` mapped onto `MiniPlayerState` and `NowPlayingState`, its actions onto `PlayerActions`.
///
/// Each state is replaced only when it changes, so the mini player, which reads `miniPlayer` alone, isn't
/// redrawn on every progress tick. The displayed position holds a seek's target until the player catches up
/// with it (`SeekHold`).
@MainActor
@Observable
final class PlayerBinding {
    private let viewModel: PlayerViewModel
    /// `nonisolated(unsafe)`: only `deinit` touches it off the main actor, and by then no other access
    /// can be concurrent (deinit runs once the last reference is gone).
    @ObservationIgnored private nonisolated(unsafe) var observer: Task<Void, Never>?

    private(set) var miniPlayer = MiniPlayerState()
    private(set) var nowPlaying = NowPlayingState.idle

    /// The player state the rest of `nowPlaying` was last built from: a progress tick keeps the same instance,
    /// so the queue rows are only rebuilt when the player state itself changes.
    @ObservationIgnored private var lastPlayer: PlayerUiState?
    @ObservationIgnored private var reportedPositionMs = 0
    @ObservationIgnored private var seekHold = SeekHold()

    init(viewModel: PlayerViewModel) {
        self.viewModel = viewModel
        update(viewModel.uiState.value)
        observe()
    }

    deinit {
        observer?.cancel()
    }

    private func observe() {
        let uiState = viewModel.uiState
        observer = Task { [weak self] in
            for await state in uiState {
                self?.update(state)
            }
        }
    }

    private func update(_ state: PlayerScreenState) {
        let player = state.player
        let current = player.current
        let artwork = current.map { ArtworkSource.song($0.song) }

        let mini = MiniPlayerState(
            title: current?.song.name,
            artist: current?.artist,
            artwork: artwork,
            isPlaying: player.playing
        )
        if mini != miniPlayer { miniPlayer = mini }

        var next = nowPlaying
        if player !== lastPlayer {
            lastPlayer = player
            next.title = current?.song.name
            next.artist = current?.artist
            next.album = current?.album
            next.artwork = artwork
            next.isPlaying = player.playing
            next.queue = player.items.map { item in
                NowPlayingQueueRow(
                    id: item.uid,
                    title: item.title.isEmpty ? "Unknown" : item.title,
                    artist: item.artist,
                    artwork: .song(item.song),
                    isCurrent: item.position == .current
                )
            }
            next.shuffleOn = player.shuffle
            next.repeatMode = NowPlayingRepeat(player.repeatMode)
            next.playbackSpeed = player.playbackSpeed
            next.sleepTimerActive = player.sleepTimerActive
        }
        reportedPositionMs = Int(state.progress.positionMs)
        next.positionMs = seekHold.displayed(reportedMs: reportedPositionMs)
        next.durationMs = Int(state.progress.durationMs)
        if next != nowPlaying { nowPlaying = next }
    }

    var actions: PlayerActions {
        let viewModel = viewModel
        return PlayerActions(
            playPause: { viewModel.togglePlayback() },
            next: { viewModel.skipToNext() },
            previous: { viewModel.skipToPrevious() },
            seek: { [weak self] ms in self?.seek(toMs: ms) },
            selectQueueItem: { [weak self] index in self?.selectQueueItem(at: index) },
            toggleShuffle: { viewModel.toggleShuffle() },
            toggleRepeat: { viewModel.cycleRepeatMode() },
            setSpeed: { speed in viewModel.setPlaybackSpeed(speed: speed) },
            startSleepTimer: { minutes in
                viewModel.startSleepTimer(durationMs: Int64(minutes) * 60_000, playToEnd: false)
            },
            stopSleepTimer: { viewModel.stopSleepTimer() }
        )
    }

    /// Seeks, showing the target straight away and holding it until the player reports a position near it.
    func seek(toMs ms: Int) {
        seekHold.begin(ms)
        nowPlaying.positionMs = ms
        viewModel.seekTo(positionMs: Int64(ms))
        // Releases the hold if no progress tick arrives to (paused, or the seek failed).
        Task { [weak self] in
            try? await Task.sleep(for: .seconds(SeekHold.timeout))
            guard let self else { return }
            let shown = seekHold.displayed(reportedMs: reportedPositionMs)
            if shown != nowPlaying.positionMs { nowPlaying.positionMs = shown }
        }
    }

    /// Skips to the queue row at `index`, as tapping it does.
    func selectQueueItem(at index: Int) {
        guard nowPlaying.queue.indices.contains(index) else { return }
        viewModel.skipToQueueItem(uid: nowPlaying.queue[index].id)
    }
}
