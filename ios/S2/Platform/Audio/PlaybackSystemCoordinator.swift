import Foundation
import MediaPlayer
import PlaybackNowPlaying
import PlaybackPlayer
import Shared
import UIKit

/// Drives Now Playing and the widgets from the player; the `Player` owns the audio session.
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
    private let text: IosSystemSurfaceText
    private let makeSession: MakeSession
    private let artworkLoader: ArtworkLoader
    /// The widgets' copy of Now Playing; nil in tests that don't look at it.
    private let widgets: NowPlayingWidgetPublisher?
    private(set) var session: NowPlayingSession<QueueKey>?
    private var observer: Task<Void, Never>?
    private var intentListener: Int?
    private var settingObserver: Task<Void, Never>?
    /// The text last handed to the session for the current item (nil text: the item had none), to refresh only when the
    /// queue or the setting changes what it says.
    private var shown: (uid: Int64, text: SystemSurfaceText?)?

    init(
        playback: IosPlayerController,
        intent: PlayIntent,
        player: Player<QueueKey>,
        text: IosSystemSurfaceText,
        widgets: NowPlayingWidgetPublisher? = nil,
        artworkLoader: ArtworkLoader = .shared,
        makeSession: @escaping MakeSession = { player, metadata, artwork in
            NowPlayingSession(player: player, supportedRates: supportedRates, metadata: metadata, artwork: artwork)
        }
    ) {
        self.playback = playback
        self.intent = intent
        self.text = text
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
            { [weak self] key in
                let shown = self?.text.text(uid: key.uid)
                self?.noteShown(shown, uid: key.uid)
                return Self.metadata(shown)
            },
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
                self?.refreshText()
            }
        }
        let setting = text.classicalDisplay
        settingObserver = Task { [weak self] in
            for await _ in setting {
                self?.publishWidgets()
                self?.refreshText()
            }
        }
    }

    /// The session reads the text once per item, so a queue edit around the playing item or the Classical display
    /// setting changing under it has to ask again.
    private func refreshText() {
        guard let uid = playback.queueOperations.queueStateFlow.value.currentItem?.uid,
              let shown, shown.uid == uid else { return }
        let current = text.text(uid: uid)
        guard current != shown.text else { return }
        self.shown = (uid, current)
        session?.refreshMetadata()
    }

    /// Only the current item's text counts: the session also asks about the item it prefetches.
    private func noteShown(_ text: SystemSurfaceText?, uid: Int64) {
        guard uid == playback.queueOperations.queueStateFlow.value.currentItem?.uid else { return }
        shown = (uid, text)
    }

    /// Releases the remote commands, clears Now Playing and stops the widgets following the player.
    func stop() {
        session?.release()
        session = nil
        observer?.cancel()
        observer = nil
        settingObserver?.cancel()
        settingObserver = nil
        shown = nil
        if let intentListener { intent.removeListener(intentListener) }
        intentListener = nil
    }

    /// What Now Playing shows for `text`; its duration is `NowPlayingPlayer`'s.
    static func metadata(_ text: SystemSurfaceText?) -> NowPlayingMetadata {
        NowPlayingMetadata(title: text?.title ?? "", artist: text?.artist, album: text?.album)
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
        let shown = text.text(uid: current.uid)
        widgets?.update(
            item: NowPlayingItem(
                id: String(current.uid), title: shown?.title ?? "", artist: shown?.artist, album: shown?.album,
                artwork: .song(song)
            ),
            // Read straight from the player, not `isPlayIntended`, which lags it by a hop through an observer task.
            isPlaying: intent.wantsPlayback
        )
    }
}
