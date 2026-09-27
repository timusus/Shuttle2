import Shared
import SwiftUI

/// What the player surfaces (mini player, Now Playing, its queue) draw, as plain values. The views take this
/// and `PlayerActions`, never `PlayerModel` itself, so when the shared `PlayerViewModel` is ported (phase 4
/// wave 5, docs/architecture/ios-port/phase-5-ios-app.md) the re-wire is the `PlayerModel` extension at the
/// bottom of this file and nothing in the views.
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
/// `PlayerModel`'s progress ticks or queue changes (`@Observable` tracks each property a body reads).
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

    init(_ mode: RepeatMode) {
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

    /// Does nothing: previews and tests.
    static let none = PlayerActions()
}

// MARK: - The binding

extension PlayerModel {
    var miniPlayerState: MiniPlayerState {
        MiniPlayerState(title: title, artist: artist, artwork: artwork, isPlaying: isPlaying)
    }

    var nowPlayingState: NowPlayingState {
        NowPlayingState(
            title: title,
            artist: artist,
            album: album,
            artwork: artwork,
            isPlaying: isPlaying,
            positionMs: positionMs,
            durationMs: durationMs,
            queue: queue,
            shuffleOn: shuffleOn,
            repeatMode: repeatMode
        )
    }

    var playerActions: PlayerActions {
        PlayerActions(
            playPause: togglePlayPause,
            next: next,
            previous: previous,
            seek: seek(toMs:),
            selectQueueItem: play(at:),
            toggleShuffle: toggleShuffle,
            toggleRepeat: toggleRepeat
        )
    }
}
