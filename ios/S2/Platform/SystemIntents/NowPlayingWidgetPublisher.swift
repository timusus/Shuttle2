import OSLog
import UIKit
import WidgetKit

/// Keeps the widgets' copy of Now Playing (#758): the current song, whether it plays and its cover, written to the App
/// Group (`NowPlayingStore`) for the widget extension, which can't reach the player. `PlaybackSystemCoordinator` calls
/// `update` whenever it publishes to the system's Now Playing; only a change of song or play state writes, and each
/// write asks WidgetKit to reload the widgets and the Control Center control.
@MainActor
final class NowPlayingWidgetPublisher {
    /// The longest side the cover is stored at, in pixels: a medium widget's artwork at 3x, and small enough to read
    /// within the extension's memory limit.
    static let artworkPixels = 300

    private let store: NowPlayingStore
    /// The current item's cover; replaceable for tests.
    var loadArtwork: @MainActor (NowPlayingItem) async -> UIImage?
    /// Tells the widgets and the control to read the store again; replaceable for tests.
    var reload: @MainActor () -> Void
    private(set) var snapshot: NowPlayingSnapshot?
    private var artworkTask: Task<Void, Never>?
    private let log = Logger(subsystem: "com.simplecityapps.shuttle2", category: "widgets")

    init(store: NowPlayingStore = NowPlayingStore(), artworkLoader: ArtworkLoader = .shared) {
        self.store = store
        loadArtwork = { item in
            guard let source = item.artwork else { return nil }
            return await artworkLoader.image(for: source, maxPixelSize: Self.artworkPixels)
        }
        reload = {
            WidgetCenter.shared.reloadTimelines(ofKind: NowPlayingStore.nowPlayingWidgetKind)
            if #available(iOS 18.0, *) {
                ControlCenter.shared.reloadControls(ofKind: NowPlayingStore.playbackControlKind)
            }
        }
    }

    /// The player now has `item` (nil: the queue emptied), playing when `isPlaying`.
    func update(item: NowPlayingItem?, isPlaying: Bool) {
        guard let item else {
            guard snapshot != nil else { return }
            artworkTask?.cancel()
            snapshot = nil
            write(nil)
            return
        }
        let songChanged = snapshot?.itemID != item.id
        let next = NowPlayingSnapshot(
            itemID: item.id,
            title: item.title,
            artist: item.artist,
            album: item.album,
            isPlaying: isPlaying,
            hasArtwork: !songChanged && snapshot?.hasArtwork == true
        )
        guard next != snapshot else { return }
        snapshot = next
        write(next, keepArtwork: !songChanged)
        guard songChanged else { return }
        artworkTask?.cancel()
        let load = loadArtwork
        artworkTask = Task { [weak self] in
            guard let image = await load(item), !Task.isCancelled,
                  let data = image.jpegData(compressionQuality: 0.8) else { return }
            self?.applyArtwork(data, for: item.id)
        }
    }

    /// The cover for `itemID` loaded; dropped if the song changed meanwhile.
    private func applyArtwork(_ data: Data, for itemID: String) {
        guard var current = snapshot, current.itemID == itemID else { return }
        current.hasArtwork = true
        snapshot = current
        write(current, artwork: data)
    }

    private func write(_ snapshot: NowPlayingSnapshot?, artwork: Data? = nil, keepArtwork: Bool = false) {
        do {
            try store.write(snapshot, artwork: artwork, keepArtwork: keepArtwork)
        } catch {
            log.error("widget snapshot not written: \(error.localizedDescription, privacy: .public)")
        }
        reload()
    }
}
