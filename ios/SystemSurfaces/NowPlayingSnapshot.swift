import Foundation

/// What the widgets show (#758): the current song and whether it plays. The app writes it to the App Group container
/// as it changes (`NowPlayingWidgetPublisher`), the widget extension reads it; compiled into both.
struct NowPlayingSnapshot: Codable, Equatable {
    /// The queue item's id: a new one means new artwork.
    var itemID: String
    var title: String
    var artist: String?
    var album: String?
    /// The listener wants playback running (`PlayIntent.wantsPlayback`), so the button shows pause from the tap.
    var isPlaying: Bool
    /// Whether `NowPlayingStore.artworkURL` holds this item's cover.
    var hasArtwork: Bool
}

/// The snapshot and its cover in the App Group container, shared by the app and the widget extension.
struct NowPlayingStore {
    /// The widgets' kinds, for `WidgetCenter.reloadTimelines(ofKind:)` and `ControlCenter.reloadControls(ofKind:)`.
    static let nowPlayingWidgetKind = "com.simplecityapps.shuttle.now-playing"
    static let playbackControlKind = "com.simplecityapps.shuttle.playback-control"

    /// project.yml's `S2_APP_GROUP` (a Debug group beside the store app's), through each target's Info.plist.
    static var appGroup: String? {
        Bundle.main.object(forInfoDictionaryKey: "S2AppGroup") as? String
    }

    /// The App Group container's folder for these files; nil when the group isn't available (no entitlement).
    let directory: URL?

    init(directory: URL?) {
        self.directory = directory
    }

    /// The App Group's store.
    init() {
        let container = Self.appGroup.flatMap {
            FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: $0)
        }
        self.init(directory: container?.appendingPathComponent("NowPlaying", isDirectory: true))
    }

    private var snapshotURL: URL? { directory?.appendingPathComponent("snapshot.json") }

    /// The current item's cover, a JPEG, when the snapshot says it has one.
    var artworkURL: URL? { directory?.appendingPathComponent("artwork.jpg") }

    /// The last snapshot written; nil before anything played, or once the queue emptied.
    func read() -> NowPlayingSnapshot? {
        guard let url = snapshotURL, let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(NowPlayingSnapshot.self, from: data)
    }

    /// The cover, if the snapshot has one.
    func readArtwork() -> Data? {
        guard read()?.hasArtwork == true, let url = artworkURL else { return nil }
        return try? Data(contentsOf: url)
    }

    /// Writes `snapshot` (nil clears it). `artwork` replaces the cover when given; `keepArtwork` leaves the one there
    /// (a play state change on the same song), otherwise it's removed.
    func write(_ snapshot: NowPlayingSnapshot?, artwork: Data? = nil, keepArtwork: Bool = false) throws {
        guard let directory, let snapshotURL, let artworkURL else { return }
        let files = FileManager.default
        try files.createDirectory(at: directory, withIntermediateDirectories: true)
        if let artwork {
            try artwork.write(to: artworkURL, options: .atomic)
        } else if !keepArtwork, files.fileExists(atPath: artworkURL.path) {
            try files.removeItem(at: artworkURL)
        }
        if let snapshot {
            try JSONEncoder().encode(snapshot).write(to: snapshotURL, options: .atomic)
        } else if files.fileExists(atPath: snapshotURL.path) {
            try files.removeItem(at: snapshotURL)
        }
    }
}
