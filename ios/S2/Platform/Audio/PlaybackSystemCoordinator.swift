import Foundation
import MediaPlayer
import PlaybackNowPlaying
import PlaybackPlayer
import Shared
import UIKit

/// Connects the system's playback surfaces to the player (phase 6, #588): Now Playing (the lock screen, Control Center
/// and CarPlay's metadata, position and transport commands) through AudioPlaybackKit's `NowPlayingSession` over a
/// `NowPlayingPlayer`, whose commands go through Kotlin, and the widgets' copy of it. The audio session is the
/// `Player`'s (`KitAudioPlayer`).
@MainActor
final class PlaybackSystemCoordinator {
    typealias QueueKey = KitAudioPlayer.QueueKey
    /// Builds the session over the player with the coordinator's metadata and artwork; tests publish to fakes.
    typealias MakeSession = @MainActor (
        _ player: NowPlayingPlayer,
        _ metadata: @escaping @MainActor (QueueKey) -> NowPlayingMetadata,
        _ artwork: @escaping @MainActor (QueueKey) async -> MPMediaItemArtwork?
    ) -> NowPlayingSession<QueueKey>

    /// The rates the system's rate command offers: the Audio sheet's presets, rounded back from Float (0.8 is
    /// 0.800000011920929 as a Double).
    static let supportedRates = NowPlayingAudioSheet.presets.map { (Double($0) * 100).rounded() / 100 }
    /// The longest side the system artwork is decoded at, in pixels: enough for the lock screen's full-width cover
    /// without holding a full-size server image.
    static let artworkPixels = 1000

    private let playback: IosPlayerController
    private let intent: PlayIntent
    private let nowPlayingPlayer: NowPlayingPlayer
    private let makeSession: MakeSession
    private let artworkLoader: ArtworkLoader
    /// The widgets' copy of Now Playing (#758); nil in tests that don't look at it.
    private let widgets: NowPlayingWidgetPublisher?
    private(set) var session: NowPlayingSession<QueueKey>?
    private var observer: Task<Void, Never>?
    private var intentListener: Int?

    init(
        playback: IosPlayerController,
        intent: PlayIntent,
        player: Player<QueueKey>,
        widgets: NowPlayingWidgetPublisher? = nil,
        artworkLoader: ArtworkLoader = .shared,
        makeSession: @escaping MakeSession = { player, metadata, artwork in
            NowPlayingSession(player: player, supportedRates: supportedRates, metadata: metadata, artwork: artwork)
        }
    ) {
        self.playback = playback
        self.intent = intent
        nowPlayingPlayer = NowPlayingPlayer(player: player, playback: playback, intent: intent)
        self.widgets = widgets
        self.artworkLoader = artworkLoader
        self.makeSession = makeSession
    }

    /// Puts the player on the lock screen, takes the remote commands and starts the widgets following it.
    func start() {
        let player = nowPlayingPlayer
        let artworkLoader = artworkLoader
        session = makeSession(
            player,
            { key in Self.metadata(player.song(for: key)) },
            { key in
                guard let song = player.song(for: key),
                      let image = await artworkLoader.image(for: .song(song), maxPixelSize: Self.artworkPixels) else { return nil }
                return Self.artwork(image)
            }
        )
        intentListener = intent.addListener { [weak self] in self?.publishWidgets() }
        let queue = playback.queueOperations.queueStateFlow
        observer = Task { [weak self] in
            for await _ in queue {
                self?.publishWidgets()
            }
        }
    }

    /// Releases the remote commands, clears Now Playing and stops the widgets following the player.
    func stop() {
        session?.release()
        session = nil
        observer?.cancel()
        observer = nil
        if let intentListener { intent.removeListener(intentListener) }
        intentListener = nil
    }

    /// What Now Playing shows for `song`; its duration is `NowPlayingPlayer`'s.
    static func metadata(_ song: Song?) -> NowPlayingMetadata {
        NowPlayingMetadata(title: song?.name ?? "", artist: song?.friendlyArtistName, album: song?.album)
    }

    /// Nonisolated: the system calls the request handler off the main thread.
    private nonisolated static func artwork(_ image: UIImage) -> MPMediaItemArtwork {
        MPMediaItemArtwork(boundsSize: image.size) { _ in image }
    }

    private func publishWidgets() {
        guard let current = playback.queueOperations.queueStateFlow.value.currentItem else {
            widgets?.update(item: nil, isPlaying: false)
            return
        }
        let song = current.song
        widgets?.update(
            item: NowPlayingItem(
                id: String(current.uid), title: song.name ?? "", artist: song.friendlyArtistName, album: song.album,
                artwork: .song(song)
            ),
            // Read straight from the player, not `isPlayIntended`, which lags it by a hop through an observer task.
            isPlaying: intent.wantsPlayback
        )
    }
}
