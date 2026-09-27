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

/// The commands the player surfaces send.
struct PlayerActions {
    var playPause: () -> Void = {}
    var next: () -> Void = {}
    var previous: () -> Void = {}
    /// Seeks the current song to a position in milliseconds.
    var seek: (Int) -> Void = { _ in }
    /// Skips to the queue row at this index.
    var selectQueueItem: (Int) -> Void = { _ in }

    /// Does nothing: previews and tests.
    static let none = PlayerActions()
}

// MARK: - The binding

extension PlayerModel {
    var nowPlayingState: NowPlayingState {
        NowPlayingState(
            title: title,
            artist: artist,
            album: album,
            artwork: artwork,
            isPlaying: isPlaying,
            positionMs: positionMs,
            durationMs: durationMs,
            queue: queue
        )
    }

    var playerActions: PlayerActions {
        PlayerActions(
            playPause: togglePlayPause,
            next: next,
            previous: previous,
            seek: seek(toMs:),
            selectQueueItem: play(at:)
        )
    }
}
