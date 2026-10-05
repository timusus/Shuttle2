import Shared
import SwiftUI

/// What the player surfaces (mini player, Now Playing, its queue) draw, as plain values. The views take this
/// and `PlayerActions`, never the shared `PlayerViewModel` itself: `PlayerBinding` at the bottom of this file maps
/// the ViewModel's state and actions onto them.
struct NowPlayingState: Equatable {
    /// The current song's id, for Song Info.
    var songID: Int64?
    var title: String?
    var artist: String?
    var album: String?
    var artwork: ArtworkSource?
    /// The current song's format, for the quality line under its title: what the server sends while it transcodes it.
    var quality: AudioQuality?
    /// Play is intended (`PlayIntent`): the transport shows pause from the tap, not once audio starts.
    var isPlaying = false
    /// Play is intended but no audio is out yet: the play button spins.
    var isLoading = false
    var positionMs = 0
    var durationMs = 0
    var queue: [NowPlayingQueueRow] = []
    /// What the queue was started from (#909), the album, artist, playlist or genre its "Playing from" line opens.
    var queueSource: HomeItem?
    var shuffleOn = false
    var repeatMode: NowPlayingRepeat = .off
    /// 1 being normal speed.
    var playbackSpeed: Float = 1
    var sleepTimerActive = false
    /// Whether Start also waits for the current track to end (the last choice made).
    var sleepTimerPlayToEnd = false
    /// Whether the current song is a favourite.
    var isFavourite = false
    /// The song actions the current song's menu offers, in the shared ViewModel's order.
    var songActions: [NowPlayingSongAction] = []
    /// The playlists Add to Playlist offers.
    var playlists: [PlaylistOption] = []

    /// The quality line, "FLAC 24/96" or "MP3 320"; nil when the format is unknown.
    var qualityBadge: String? { quality?.badge() }

    /// The quality line as VoiceOver says it: "FLAC, 24 bit, 96 kilohertz".
    var spokenQualityBadge: String? { quality?.spokenBadge() }

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
    /// The song's length in milliseconds; 0 when unknown.
    let durationMs: Int

    init(id: Int64, title: String, artist: String?, artwork: ArtworkSource? = nil, isCurrent: Bool, durationMs: Int = 0) {
        self.id = id
        self.title = title
        self.artist = artist
        self.artwork = artwork
        self.isCurrent = isCurrent
        self.durationMs = durationMs
    }
}

/// The shared song actions Now Playing's menu offers, of those the shared ViewModel allows the playing song
/// (`PlayerViewModel.songActions`). Edit Tags has no iOS screen yet, so it is left out. Song Info opens its sheet from the view, not the ViewModel.
enum NowPlayingSongAction: Equatable, CaseIterable {
    case addToPlaylist
    case goToAlbum
    case goToArtist
    case songInfo
    case exclude

    init?(_ type: MediaActionType) {
        switch type {
        case .addToPlaylist: self = .addToPlaylist
        case .goToAlbum: self = .goToAlbum
        case .goToArtist: self = .goToArtist
        case .songInfo: self = .songInfo
        case .exclude: self = .exclude
        default: return nil
        }
    }

    var title: String {
        switch self {
        case .addToPlaylist: "Add to Playlist"
        case .goToAlbum: "Go to Album"
        case .goToArtist: "Go to Artist"
        case .songInfo: "Song Info"
        case .exclude: "Exclude"
        }
    }

    var systemImage: String {
        switch self {
        case .addToPlaylist: "text.badge.plus"
        case .goToAlbum: "square.stack"
        case .goToArtist: "music.microphone"
        case .songInfo: "info.circle"
        case .exclude: "nosign"
        }
    }
}

/// A playlist Add to Playlist can add to.
struct PlaylistOption: Identifiable, Equatable {
    let id: Int64
    let name: String
}

/// Where Add to Playlist puts the song: a new playlist by that name, Favorites, or an existing playlist.
enum PlaylistChoice: Equatable {
    case new(name: String)
    case favourites
    case playlist(id: Int64)

    /// A new playlist's name without surrounding whitespace, or nil when that leaves nothing.
    static func trimmedName(_ name: String) -> String? {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}

/// What the mini player draws: only the fields it shows, so reading it doesn't subscribe the bar to
/// `PlayerBinding`'s progress ticks or queue changes (`@Observable` tracks each property a body reads).
struct MiniPlayerState: Equatable {
    var title: String?
    var artist: String?
    var artwork: ArtworkSource?
    /// Play is intended (`PlayIntent`): the button shows pause from the tap, not once audio starts.
    var isPlaying = false
    /// Play is intended but no audio is out yet: the button spins.
    var isLoading = false
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
    /// Skips to the queue row with this id.
    var selectQueueItem: (Int64) -> Void = { _ in }
    /// Moves the queue row with the first id to just after the one with the second, or to the top when nil.
    var moveQueueItem: (Int64, Int64?) -> Void = { _, _ in }
    /// Removes the queue row with this id.
    var removeQueueItem: (Int64) -> Void = { _ in }
    /// Moves the queue row with this id to play after the current one.
    var playNext: (Int64) -> Void = { _ in }
    /// Excludes the song of the queue row with this id from the library, which also takes it out of the queue.
    var excludeQueueItem: (Int64) -> Void = { _ in }
    var clearQueue: () -> Void = {}
    /// Opens a screen, closing Now Playing first (the queue's "Playing from" line).
    var openRoute: (Route) -> Void = { _ in }
    var toggleFavourite: () -> Void = {}
    /// Runs a song action on the current song; Add to Playlist goes through `addToPlaylist`.
    var songAction: (NowPlayingSongAction) -> Void = { _ in }
    /// Adds the current song to a playlist.
    var addToPlaylist: (PlaylistChoice) -> Void = { _ in }
    var toggleShuffle: () -> Void = {}
    /// Steps repeat through off, all, one.
    var toggleRepeat: () -> Void = {}
    /// Sets the playback speed, 1 being normal.
    var setSpeed: (Float) -> Void = { _ in }
    /// Starts the sleep timer, pausing playback after this many minutes, and, when `playToEnd`, once the current
    /// track has ended too (0 minutes: at the track's end).
    var startSleepTimer: (_ minutes: Int, _ playToEnd: Bool) -> Void = { _, _ in }
    var stopSleepTimer: () -> Void = {}
    /// The time left in milliseconds, ticking while collected: nil when off, 0 while it waits for the track to end.
    var sleepTimerRemaining: () -> AsyncStream<Int?> = { AsyncStream { $0.finish() } }

    /// Does nothing: previews and tests.
    static let none = PlayerActions()
}

// MARK: - The binding

/// The mini player's and Now Playing's state and commands, off the shared `PlayerViewModel` (#586, #587, #621): its
/// `uiState` mapped onto `MiniPlayerState` and `NowPlayingState`, its actions onto `PlayerActions`, and its one-shot
/// events handed to Now Playing as `events`, which `outcome(for:)` turns into a notice or a route to open. Whether it
/// plays is the listener's intent (`PlayIntent`), not the player's state, and play/pause goes through the intent.
///
/// Each state is replaced only when it changes, so the mini player, which reads `miniPlayer` alone, isn't
/// redrawn on every progress tick. The displayed position holds a seek's target until the player catches up
/// with it (`SeekHold`).
@MainActor
@Observable
final class PlayerBinding {
    private let viewModel: PlayerViewModel
    private let intent: PlayIntent
    /// `nonisolated(unsafe)`: only `deinit` touches them off the main actor, and by then no other access
    /// can be concurrent (deinit runs once the last reference is gone).
    @ObservationIgnored private nonisolated(unsafe) var observer: Task<Void, Never>?
    @ObservationIgnored private nonisolated(unsafe) var playlistObserver: Task<Void, Never>?
    @ObservationIgnored private nonisolated(unsafe) var songActionsObserver: Task<Void, Never>?

    private(set) var miniPlayer = MiniPlayerState()
    /// Whether there is a current song, playing or paused: the mini player shows only then. Its own property, so the
    /// hosts that attach or hide the bar re-render on this alone, not on every title or play/pause change.
    private(set) var isMiniPlayerVisible = false
    private(set) var nowPlaying = NowPlayingState.idle
    /// The ViewModel's pending one-shot events; hand each id back through `eventHandled` once consumed.
    private(set) var events: [PendingEvent<any PlayerUiEvent>] = []

    /// The player state the rest of `nowPlaying` was last built from: a progress tick keeps the same instance,
    /// so the queue rows are only rebuilt when the player state itself changes.
    @ObservationIgnored private var lastPlayer: PlayerUiState?
    @ObservationIgnored private var reportedPositionMs = 0
    @ObservationIgnored private var seekHold = SeekHold()
    /// The song whose actions `songActionsObserver` follows, by id.
    @ObservationIgnored private var songActionsFor: Int64?
    @ObservationIgnored private var playlistsById: [Int64: Playlist] = [:]

    init(viewModel: PlayerViewModel, intent: PlayIntent) {
        self.viewModel = viewModel
        self.intent = intent
        update(viewModel.uiState.value)
        observe()
        intent.addListener { [weak self] in self?.updateIntent() }
    }

    deinit {
        observer?.cancel()
        playlistObserver?.cancel()
        songActionsObserver?.cancel()
    }

    private func observe() {
        let uiState = viewModel.uiState
        observer = Task { [weak self] in
            for await state in uiState {
                self?.update(state)
            }
        }
        let playlists = viewModel.playlists()
        playlistObserver = Task { [weak self] in
            for await list in playlists {
                self?.updatePlaylists(list)
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
            isPlaying: intent.isPlayIntended,
            isLoading: intent.isLoading
        )
        if mini != miniPlayer { miniPlayer = mini }
        if (current != nil) != isMiniPlayerVisible { isMiniPlayerVisible = current != nil }

        var next = nowPlaying
        if player !== lastPlayer {
            lastPlayer = player
            next.songID = current?.song.id
            next.title = current?.song.name
            next.artist = current?.artist
            next.album = current?.album
            next.artwork = artwork
            // A transcode's badge is what the server sends, not the file it holds (#902).
            next.quality = current.map { playing in player.delivered.map(AudioQuality.init(delivered:)) ?? AudioQuality(song: playing.song) }
            next.queueSource = player.queueSource
            next.queue = player.items.map { item in
                NowPlayingQueueRow(
                    id: item.uid,
                    title: item.title.isEmpty ? "Unknown" : item.title,
                    artist: item.artist,
                    artwork: .song(item.song),
                    isCurrent: item.position == .current,
                    durationMs: Int(item.song.duration)
                )
            }
            next.shuffleOn = player.shuffle
            next.repeatMode = NowPlayingRepeat(player.repeatMode)
            next.playbackSpeed = player.playbackSpeed
            next.sleepTimerActive = player.sleepTimerActive
            next.sleepTimerPlayToEnd = player.sleepTimerPlayToEnd
            next.isFavourite = player.favourite
            if current?.song.id != songActionsFor { next.songActions = [] }
            observeSongActions(for: current?.song)
        }
        reportedPositionMs = Int(state.progress.positionMs)
        next.positionMs = seekHold.displayed(reportedMs: reportedPositionMs)
        next.durationMs = Int(state.progress.durationMs)
        next.isPlaying = intent.isPlayIntended
        next.isLoading = intent.isLoading
        if next != nowPlaying { nowPlaying = next }

        if state.events.map(\.id) != events.map(\.id) { events = state.events }
    }

    /// The intent changed: only the play state of each surface moves.
    private func updateIntent() {
        if miniPlayer.isPlaying != intent.isPlayIntended || miniPlayer.isLoading != intent.isLoading {
            miniPlayer.isPlaying = intent.isPlayIntended
            miniPlayer.isLoading = intent.isLoading
        }
        if nowPlaying.isPlaying != intent.isPlayIntended || nowPlaying.isLoading != intent.isLoading {
            nowPlaying.isPlaying = intent.isPlayIntended
            nowPlaying.isLoading = intent.isLoading
        }
    }

    /// Follows the actions the shared ViewModel allows `song`, restarting when the current song changes.
    private func observeSongActions(for song: Song?) {
        guard song?.id != songActionsFor else { return }
        songActionsFor = song?.id
        songActionsObserver?.cancel()
        guard let song else { return }
        let actions = viewModel.songActions(song: song)
        songActionsObserver = Task { [weak self] in
            for await types in actions {
                guard let self, !Task.isCancelled else { return }
                let mapped = types.compactMap(NowPlayingSongAction.init)
                if mapped != nowPlaying.songActions { nowPlaying.songActions = mapped }
            }
        }
    }

    private func updatePlaylists(_ list: [Playlist]) {
        playlistsById = Dictionary(list.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let options = list.map { PlaylistOption(id: $0.id, name: $0.name) }
        if options != nowPlaying.playlists { nowPlaying.playlists = options }
    }

    var actions: PlayerActions {
        let viewModel = viewModel
        let intent = intent
        return PlayerActions(
            playPause: { intent.toggle() },
            next: {
                intent.listenerPlayed()
                viewModel.skipToNext()
            },
            previous: {
                intent.listenerPlayed()
                viewModel.skipToPrevious()
            },
            seek: { [weak self] ms in self?.seek(toMs: ms) },
            selectQueueItem: { uid in
                intent.listenerPlayed()
                viewModel.skipToQueueItem(uid: uid)
            },
            moveQueueItem: { uid, afterUid in
                viewModel.moveQueueItem(uid: uid, afterUid: afterUid.map { KotlinLong(value: $0) })
            },
            removeQueueItem: { uid in viewModel.removeQueueItem(uid: uid) },
            playNext: { uid in viewModel.playNext(uid: uid) },
            excludeQueueItem: { [weak self] uid in self?.excludeQueueItem(uid) },
            clearQueue: { viewModel.clearQueue() },
            toggleFavourite: { viewModel.toggleFavourite() },
            songAction: { [weak self] action in self?.perform(action) },
            addToPlaylist: { [weak self] choice in self?.addToPlaylist(choice) },
            toggleShuffle: { viewModel.toggleShuffle() },
            toggleRepeat: { viewModel.cycleRepeatMode() },
            setSpeed: { speed in viewModel.setPlaybackSpeed(speed: speed) },
            startSleepTimer: { minutes, playToEnd in
                viewModel.startSleepTimer(durationMs: Int64(minutes) * 60_000, playToEnd: playToEnd)
            },
            stopSleepTimer: { viewModel.stopSleepTimer() },
            sleepTimerRemaining: {
                AsyncStream { continuation in
                    let task = Task {
                        for await ms in viewModel.sleepTimerRemaining() { continuation.yield(ms?.intValue) }
                        continuation.finish()
                    }
                    continuation.onTermination = { _ in task.cancel() }
                }
            }
        )
    }

    /// The current song, as a one-song selection for the shared actions.
    private var currentSelection: (any MediaSelection)? {
        lastPlayer?.current.map { MediaSelectionSongs(song: $0.song) }
    }

    private func perform(_ action: NowPlayingSongAction) {
        guard let selection = currentSelection else { return }
        let mediaAction: (any MediaAction)? = switch action {
        case .addToPlaylist, .songInfo: nil
        case .goToAlbum: MediaActionGoToAlbum(selection: selection)
        case .goToArtist: MediaActionGoToArtist(selection: selection)
        case .exclude: MediaActionExclude(selection: selection)
        }
        if let mediaAction { viewModel.onMediaAction(action: mediaAction) }
    }

    /// Excludes a queued song through the shared action, as the playing song's menu does; the song comes from the
    /// player state the queue rows were built from, so the row needs no query of its own.
    private func excludeQueueItem(_ uid: Int64) {
        guard let song = lastPlayer?.items.first(where: { $0.uid == uid })?.song else { return }
        viewModel.onMediaAction(action: MediaActionExclude(selection: MediaSelectionSongs(song: song)))
    }

    private func addToPlaylist(_ choice: PlaylistChoice) {
        guard let selection = currentSelection else { return }
        let action: (any MediaAction)? = switch choice {
        case .new(let name):
            PlaylistChoice.trimmedName(name).map { MediaActionCreatePlaylist(selection: selection, name: $0) }
        case .favourites:
            MediaActionFavourite(selection: selection, favourite: true)
        case .playlist(let id):
            playlistsById[id].map { MediaActionAddToPlaylist(selection: selection, playlist: $0, ignoreDuplicates: false) }
        }
        if let action { viewModel.onMediaAction(action: action) }
    }

    /// Tells the ViewModel the event with this id has been consumed.
    func eventHandled(_ id: Int64) {
        viewModel.onEventHandled(id: id)
    }

    /// What Now Playing does with one of the ViewModel's events; its Undo and Add Anyway buttons go back to the
    /// ViewModel.
    func outcome(for event: any PlayerUiEvent) -> PlayerEventOutcome? {
        let viewModel = viewModel
        return PlayerEventOutcome.resolve(
            event,
            undoClearQueue: { viewModel.undoClearQueue() },
            undoRemoveQueueItem: { viewModel.undoRemoveQueueItem() },
            send: { viewModel.onMediaAction(action: $0) }
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
}

