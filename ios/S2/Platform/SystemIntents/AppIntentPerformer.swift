import Foundation
import Shared

/// Where the library intents' playlists and plays come from: Kotlin's `AppIntentLibrary`, or a fake in tests.
@MainActor
protocol IntentLibrary {
    func playlists() async throws -> [PlaylistEntity]
    func shuffleAll() -> any MediaAction
    /// Nil when there's no playlist with `id` any more.
    func playPlaylist(id: Int64, shuffled: Bool) async throws -> (any MediaAction)?
}

/// :shared's `AppIntentLibrary`.
struct KotlinIntentLibrary: IntentLibrary {
    let library: AppIntentLibrary

    func playlists() async throws -> [PlaylistEntity] {
        try await library.playlists().map(PlaylistEntity.init)
    }

    func shuffleAll() -> any MediaAction {
        library.shuffleAll()
    }

    func playPlaylist(id: Int64, shuffled: Bool) async throws -> (any MediaAction)? {
        try await library.playPlaylist(id: id, shuffled: shuffled)
    }
}

extension PlaylistEntity {
    init(_ playlist: Playlist) {
        self.init(id: Int(playlist.id), name: playlist.name, songCount: Int(playlist.songCount))
    }
}

/// Carries out the App Intents (#758) in the app, through the same paths as its own buttons: plays and pauses through
/// `PlayIntent`, so every play/pause control shows the change from the tap, and library plays as the shared
/// `MediaAction`s, dispatched as a screen's are (`PlayIntent.begin`, then `finished` with the result). Registered in
/// `IntentPerformers` at launch.
@MainActor
final class AppIntentPerformer: PlaybackIntentPerforming, LibraryIntentPerforming {
    private let intent: PlayIntent
    /// Whether the queue has a current item to play.
    private let hasQueue: () -> Bool
    private let skip: () -> Void
    private let library: any IntentLibrary
    /// Runs a `MediaAction` and hands back its result.
    private let dispatch: @MainActor (any MediaAction) async -> any MediaActionResult

    init(
        intent: PlayIntent,
        hasQueue: @escaping () -> Bool,
        skipToNext: @escaping () -> Void,
        library: any IntentLibrary,
        dispatch: @escaping @MainActor (any MediaAction) async -> any MediaActionResult
    ) {
        self.intent = intent
        self.hasQueue = hasQueue
        skip = skipToNext
        self.library = library
        self.dispatch = dispatch
    }

    /// The performer over the app's graph.
    convenience init(graph: IosAppGraph, intent: PlayIntent) {
        let player = graph.playerController
        self.init(
            intent: intent,
            hasQueue: { player.queueOperations.queueStateFlow.value.currentItem != nil },
            skipToNext: { player.skipToNext(ignoreRepeat: true, completion: nil) },
            library: KotlinIntentLibrary(library: graph.appIntentLibrary),
            dispatch: { action in await Self.dispatch(action, graph: graph) }
        )
    }

    /// Dispatches `action` on a `MediaActionsViewModel` of its own, cleared once the result is in: nothing shows the
    /// results an intent posts, so a long-lived one would only collect them.
    private static func dispatch(_ action: any MediaAction, graph: IosAppGraph) async -> any MediaActionResult {
        let actions = graph.mediaActionsViewModel
        let result: any MediaActionResult = await withCheckedContinuation { continuation in
            // Kotlin hands the result back on the main thread.
            actions.dispatch(action: action) { result in
                continuation.resume(returning: result)
            }
        }
        actions.clear()
        return result
    }

    // MARK: - PlaybackIntentPerforming

    func togglePlayback() async throws {
        if intent.wantsPlayback {
            intent.pause(from: .appIntent)
        } else {
            try await playOrShuffle()
        }
    }

    func setPlaying(_ playing: Bool) async throws {
        if playing {
            try await playOrShuffle()
        } else {
            intent.pause(from: .appIntent)
        }
    }

    func skipToNext() async throws {
        intent.listenerPlayed()
        skip()
    }

    /// Resumes the queue, or with nothing queued (a first launch, a cleared queue), shuffles the library: "play Shuttle
    /// Music" always plays something.
    private func playOrShuffle() async throws {
        if hasQueue() {
            intent.play(from: .appIntent)
        } else {
            try await shuffleLibrary()
        }
    }

    // MARK: - LibraryIntentPerforming

    func shuffleLibrary() async throws {
        try await play(library.shuffleAll())
    }

    func playPlaylist(id: Int64, shuffled: Bool) async throws {
        guard let action = try await library.playPlaylist(id: id, shuffled: shuffled) else {
            throw ShuttleIntentError.playlistNotFound
        }
        try await play(action)
    }

    func playlists() async throws -> [PlaylistEntity] {
        try await library.playlists()
    }

    /// Dispatches a play as a screen does: the intent is the listener's from now until the player takes it up.
    private func play(_ action: any MediaAction) async throws {
        let ticket = intent.begin()
        let result = await dispatch(action)
        intent.finished(ticket, result: result)
        guard let message = (result as? MediaActionResultMessage)?.message else { return }
        switch message {
        case is MediaActionMessageNoSongs: throw ShuttleIntentError.noSongs
        case is MediaActionMessagePlaybackFailed: throw ShuttleIntentError.playbackFailed
        default: break
        }
    }
}
