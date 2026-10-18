import OSLog
import UIKit
import WidgetKit

/// The song the widgets show.
struct NowPlayingItem: Equatable {
    /// Identifies the queue item, so a late artwork load can't land on the next song.
    var id: String
    var title: String
    var artist: String?
    var album: String?
    /// Where the song's cover may be, as the in-app artwork views have it; nil for none.
    var artwork: ArtworkSource?
}

/// Writes the widgets' copy of Now Playing to the App Group, since the widget extension can't reach the player.
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
