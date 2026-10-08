import Foundation
import Shared

/// When the library imports on iOS: after a sign-in (`SourcesViewModel`), on the Library's pull-to-refresh and
/// Sources' Rescan, and at launch only until an import has finished once, as Android's `scanIfNeverScanned`
/// The library lives in Room, so a relaunch shows it straight away; a
/// re-import on every launch blanked the lists for minutes (#623). This device's files are the exception: at launch
/// and on every return to the foreground they're listed again, and the library imports when one was added, removed or
/// changed since the last import (#590), as a file copied in through Files or Finder should show up without a pull to
/// refresh. The servers are synced quietly, for what changed on them, at launch and on every return to the foreground
/// (`syncIfStale`), and daily in the background (`BackgroundRefresh`), as on Android (#771).
/// `MediaSources.scan` outlives the screen that asked and is a no-op while an import runs, or with no source.
@MainActor
enum LibraryImport {
    static func atLaunch(graph: IosAppGraph = AppGraph.shared) {
        atLaunch(
            hasScanned: graph.mediaSources.hasScanned,
            songTagsOutdated: graph.mediaSources.songTagsOutdated,
            scan: { graph.mediaSources.scan(foldersChanged: false) }
        )
    }

    /// Interrupted before it finished (the app killed mid-import), the first import runs again. A library imported
    /// before this build's tags (`MediaImporter.SONG_TAGS_VERSION`) imports once more, updating its songs in place.
    static func atLaunch(hasScanned: Bool, songTagsOutdated: Bool, scan: () -> Void) {
        if !hasScanned || songTagsOutdated { scan() }
    }

    static func whenLocalFilesChange(
        graph: IosAppGraph = AppGraph.shared,
        localLibrary: LocalLibrary = AppGraph.dependencies.localLibrary
    ) async {
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
    static func syncIfStale(graph: IosAppGraph = AppGraph.shared) {
        graph.mediaSources.syncIfStale()
    }

    static func refresh(graph: IosAppGraph = AppGraph.shared) {
        graph.mediaSources.scan(foldersChanged: false)
    }
}
