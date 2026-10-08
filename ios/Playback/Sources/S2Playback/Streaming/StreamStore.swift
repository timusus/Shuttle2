import Foundation
import PlaybackStreaming

/// Where streamed tracks download to, and the ones played through from the start are kept for the next play: S2's own
/// store under Caches, so the system may purge it and no other owner of a `GrowingFileStore` can evict it.
public enum StreamStore {
    public static let budgetBytes: Int64 = 512 * 1024 * 1024

    /// Its first use deletes the downloads a killed app left half done: before any source exists, since a partial
    /// is only a live download's once one does.
    static let shared: GrowingFileStore = {
        let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
        let store = GrowingFileStore(directory: caches.appendingPathComponent("streamed", isDirectory: true), budgetBytes: budgetBytes)
        store.sweepPartials()
        return store
    }()

    /// Once at launch, off the main thread: sweeps ``shared`` and deletes the run caches the previous byte source kept
    /// (`streamed-runs` under Caches and Application Support).
    public static func prepareAtLaunch() {
        _ = shared
        let files = FileManager.default
        for directory in [files.urls(for: .cachesDirectory, in: .userDomainMask), files.urls(for: .applicationSupportDirectory, in: .userDomainMask)] {
            directory.first.map(GrowingFileStore.removeRetiredRunCache(applicationSupport:))
        }
    }
}
