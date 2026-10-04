import BackgroundTasks
import Foundation
import os
import Shared

/// Sends queued Last.fm scrobbles (#503) when the app can't do it in-process, where Android uses WorkManager. While
/// the app runs, `InProcessScrobbleFlushScheduler` sends each scrobble as it's queued and on every return to the app;
/// this `BGAppRefreshTask`, asked for each time the app leaves the foreground, sends what's still queued after the
/// app is suspended or ended. The scheduler never runs two sends at once, whichever of these starts them.
@MainActor
enum ScrobbleFlush {
    /// Listed in Info.plist's `BGTaskSchedulerPermittedIdentifiers` (`project.yml`).
    static let identifier = "com.simplecityapps.shuttle.scrobble"

    private static let log = Logger(subsystem: "com.simplecityapps.shuttle2", category: "ScrobbleFlush")

    /// Asks for a run no sooner than an hour from now, replacing a request already pending.
    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: identifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 60 * 60)
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            // Background App Refresh turned off, or a simulator: the send on each return to the app still runs
            log.info("Scrobble send not scheduled: \(error.localizedDescription, privacy: .public)")
        }
    }

    /// The task: sends the queue, and asks again if Last.fm couldn't be reached. A queue held for the user (signed
    /// out, or a rejected session) waits for the app. SwiftUI cancels it when the system ends the task's time.
    static func run(graph: IosAppGraph = AppGraph.shared) async {
        do {
            if try await graph.scrobbleFlushScheduler.flush() == .retry { schedule() }
        } catch {
            schedule()
            log.info("Scrobble send ended early: \(error.localizedDescription, privacy: .public)")
        }
    }
}
