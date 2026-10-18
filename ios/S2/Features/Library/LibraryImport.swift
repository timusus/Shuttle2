import Foundation
import Shared

/// Launch imports only until the first import finishes, so a relaunch shows the Room library at once.
@MainActor
enum LibraryImport {
    static func atLaunch(graph: IosAppGraph) {
        atLaunch(
            hasScanned: graph.mediaSources.hasScanned,
            songTagsOutdated: graph.mediaSources.songTagsOutdated,
            scan: { graph.mediaSources.scan(foldersChanged: false) }
        )
    }

    /// Interrupted before it finished (the app killed mid-import), the first import runs again. A library imported
    /// before this build's tags (`SONG_TAGS_VERSION`) imports once more, updating its songs in place.
    static func atLaunch(hasScanned: Bool, songTagsOutdated: Bool, scan: () -> Void) {
        if !hasScanned || songTagsOutdated { scan() }
    }

    static func whenLocalFilesChange(graph: IosAppGraph, localLibrary: LocalLibrary) async {
        await whenLocalFilesChange(
            deviceEnabled: graph.mediaSources.enabledTypes.value.contains(.shuttle),
            changed: { await Task.detached(priority: .utility) { localLibrary.changedSinceLastImport() }.value },
            scan: { graph.mediaSources.scan(foldersChanged: false) }
        )
    }

    /// With this device a source, lists its files (off the main thread) and imports when they changed.
    static func whenLocalFilesChange(deviceEnabled: Bool, changed: () async -> Bool, scan: () -> Void) async {
        guard deviceEnabled, await changed() else { return }
        scan()
    }

    /// At most every few minutes per server (`SyncPolicy`), and a no-op while an import runs.
    static func syncIfStale(graph: IosAppGraph) {
        graph.mediaSources.syncIfStale()
    }

    static func refresh(graph: IosAppGraph) {
        graph.mediaSources.scan(foldersChanged: false)
    }
}
