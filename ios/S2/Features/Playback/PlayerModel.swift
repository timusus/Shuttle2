import Foundation
import Shared

/// The mini player and Now Playing screens' state: current song, transport state and the queue, read
/// off the Kotlin `IosPlayerController`'s flows the same way `PlaybackSystemCoordinator` follows them
/// for Now Playing/lock screen, plus the commands the UI sends back through it. Stands in for the
/// shared `PlayerViewModel`, not yet ported (phase 4 wave 5,
/// docs/architecture/ios-port/phase-5-ios-app.md).
@MainActor
@Observable
final class PlayerModel {
    /// One row in the queue list, as `NowPlayingView` shows it.
    struct QueueRow: Identifiable, Equatable {
        let id: Int64
        let title: String
        let artist: String?
        let isCurrent: Bool
    }

    private let playback: IosPlayerController
    private var observers: [Task<Void, Never>] = []

    private(set) var title: String?
    private(set) var artist: String?
    private(set) var album: String?
    private(set) var isPlaying = false
    private(set) var isLoading = false
    private(set) var positionMs: Int = 0
    private(set) var durationMs: Int = 0
    private(set) var queue: [QueueRow] = []
    /// The current item's index into `queue`, for the queue list's tap-to-skip.
    private(set) var queuePosition: Int?

    init(playback: IosPlayerController) {
        self.playback = playback
        observe()
    }

    isolated deinit {
        observers.forEach { $0.cancel() }
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
                for await queue in playback.queueOperations.queueStateFlow {
                    self?.queueChanged(queue)
                }
            },
        ]
    }

    private func playbackStateChanged(_ state: PlaybackState) {
        isPlaying = state is PlaybackState.Playing
        isLoading = state is PlaybackState.Loading
    }

    private func progressChanged(_ progress: PlaybackProgress?) {
        positionMs = Int(progress?.position ?? 0)
        durationMs = Int(progress?.duration ?? 0)
    }

    private func queueChanged(_ state: QueueState) {
        let song = state.currentItem?.song
        title = song?.name
        artist = song?.friendlyArtistName
        album = song?.album
        queuePosition = state.currentPosition.map { Int($0.intValue) }
        queue = state.items.map { item in
            QueueRow(
                id: item.uid,
                title: item.song.name ?? "Unknown",
                artist: item.song.friendlyArtistName,
                isCurrent: item.isCurrent
            )
        }
    }

    // MARK: - Commands

    func togglePlayPause() {
        playback.togglePlayback()
    }

    func next() {
        playback.skipToNext(ignoreRepeat: true, completion: nil)
    }

    func previous() {
        playback.skipToPrev(force: false, completion: nil)
    }

    func seek(toMs ms: Int) {
        playback.seekTo(position: Int32(ms))
    }

    /// Skips to the queue item at `position` (`QueueRow` index), as tapping a queue row does.
    func play(at position: Int) {
        playback.skipTo(position: Int32(position))
    }
}

extension PlayerModel {
    /// The app's single `PlayerModel` (`ViewModelCache`, keyed as a screen view model would be), so
    /// `MiniPlayerView` and `NowPlayingView` observe the same state instead of each restarting its own
    /// flows (`.claude/rules/ios.md`). Nonisolated so it can sit in a default parameter expression, which
    /// evaluates outside the initializer's own isolation; SwiftUI only builds view structs on the main
    /// thread, so the actor assumption holds, the same bridge `PlaybackSystemCoordinator` uses for its
    /// engine callbacks.
    nonisolated static var shared: PlayerModel {
        MainActor.assumeIsolated {
            ViewModelCache.shared.viewModel("player") {
                PlayerModel(playback: AppGraph.shared.playerController)
            }
        }
    }
}
