import Foundation
import Shared

/// When the library imports from its servers on iOS: after a sign-in (`SourcesViewModel`), on the Library's
/// pull-to-refresh and Sources' Rescan, and at launch only until an import has finished once, as Android's
/// `scanIfNeverScanned` (`ios-port/phase-5-ios-app.md` section 3). The library lives in Room, so a relaunch shows it
/// straight away; a re-import on every launch blanked the lists for minutes (#623). A periodic refresh is a
/// `BGAppRefreshTask` in phase 9, where Android uses WorkManager. `MediaSources.scan` outlives the screen that asked
/// and is a no-op while an import runs, or with no server signed in.
@MainActor
enum LibraryImport {
    static func atLaunch(graph: IosAppGraph = AppGraph.shared) {
        atLaunch(hasScanned: graph.mediaSources.hasScanned, scan: graph.mediaSources.scan)
    }

    /// Interrupted before it finished (the app killed mid-import), the first import runs again.
    static func atLaunch(hasScanned: Bool, scan: () -> Void) {
        if !hasScanned { scan() }
    }

    static func refresh(graph: IosAppGraph = AppGraph.shared) {
        graph.mediaSources.scan()
    }
}
