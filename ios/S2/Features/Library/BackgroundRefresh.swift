import BackgroundTasks
import Foundation
import os
import Shared

/// The system runs this about daily; a source synced in the last few minutes is skipped.
@MainActor
enum BackgroundRefresh {
    /// Listed in Info.plist's `BGTaskSchedulerPermittedIdentifiers` (`project.yml`).
    static let identifier = "com.simplecityapps.shuttle.sync"

    private static let log = Logger(subsystem: "com.simplecityapps.shuttle2", category: "BackgroundRefresh")

    /// Asks for the next run no sooner than a day from now, replacing a request already pending.
    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: identifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 24 * 60 * 60)
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            // Background App Refresh turned off, or a simulator: the sync on each return to the app still runs
            log.info("Background sync not scheduled: \(error.localizedDescription, privacy: .public)")
        }
    }

    /// The task: asks for the next run, then syncs. SwiftUI cancels it when the system ends the task's time, which
    /// cancels the sync.
    static func run(graph: IosAppGraph) async {
        schedule()
        do {
            try await graph.backgroundSync.run()
        } catch {
            log.info("Background sync ended early: \(error.localizedDescription, privacy: .public)")
        }
    }
}
