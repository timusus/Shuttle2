import Foundation
import Shared

/// When the library imports from its servers on iOS: once at launch and on the Library's pull-to-refresh
/// (`ios-port/phase-5-ios-app.md` section 3; a `BGAppRefreshTask` is phase 9, where Android uses WorkManager).
/// `MediaSources.scan` outlives the screen that asked and is a no-op while an import runs, or with no server
/// signed in.
@MainActor
enum LibraryImport {
    static func atLaunch(graph: IosAppGraph = AppGraph.shared) {
        graph.mediaSources.scan()
    }

    static func refresh(graph: IosAppGraph = AppGraph.shared) {
        graph.mediaSources.scan()
    }
}
