import Darwin
import Foundation
import os

/// Cold-start milestones, in Release too (`docs/performance/ios-startup.md`): signposts for Instruments, and a notice
/// with the milestone's time since the process started for `log show`, all under the app's subsystem in the "Startup"
/// category, where shared code's `Logger.tagged("Startup")` logs too. Each screen's milestones log the first time
/// only, so a body re-evaluating costs a set lookup.
@MainActor
enum StartupTrace {
    enum Screen: String {
        case home, library, songs, albums, albumArtists, playlists, genres, search
    }

    enum Phase: String {
        /// The screen's body first ran.
        case body
        /// It first appeared.
        case appear
        /// It first had something other than its loading state to show.
        case content
    }

    private struct Milestone: Hashable {
        let screen: Screen
        let phase: Phase
    }

    nonisolated static let log = OSLog(subsystem: "com.simplecityapps.shuttle", category: "Startup")
    nonisolated private static let logger = Logger(log)
    nonisolated private static let signposter = OSSignposter(logHandle: log)

    private static var seen = Set<Milestone>()
    private static var appMilestones = Set<String>()

    /// When the kernel started this process, the earliest point there is: what runs before `S2App.init` (dyld, the
    /// static initialisers, UIKit's launch) counts too.
    private static let processStart: TimeInterval = {
        var info = kinfo_proc()
        var size = MemoryLayout<kinfo_proc>.stride
        var mib: [Int32] = [CTL_KERN, KERN_PROC, KERN_PROC_PID, getpid()]
        guard sysctl(&mib, 4, &info, &size, nil, 0) == 0 else { return Date().timeIntervalSince1970 }
        let start = info.kp_proc.p_starttime
        return TimeInterval(start.tv_sec) + TimeInterval(start.tv_usec) / 1_000_000
    }()

    private static var sinceProcessStart: Int {
        Int((Date().timeIntervalSince1970 - processStart) * 1000)
    }

    /// An app-wide milestone (the app's init, the first frame), logged the first time only.
    static func mark(_ name: StaticString) {
        let key = name.description
        guard appMilestones.insert(key).inserted else { return }
        signposter.emitEvent(name)
        logger.notice("\(key, privacy: .public) at \(sinceProcessStart) ms")
    }

    /// One of [screen]'s milestones, logged the first time only. Returns nothing a view builder would show, so a body
    /// can call it with `let _ =`.
    static func mark(_ screen: Screen, _ phase: Phase) {
        guard seen.insert(Milestone(screen: screen, phase: phase)).inserted else { return }
        signposter.emitEvent("screen", "\(screen.rawValue, privacy: .public) \(phase.rawValue, privacy: .public)")
        logger.notice("\(screen.rawValue, privacy: .public) \(phase.rawValue, privacy: .public) at \(sinceProcessStart) ms")
    }

    /// [screen]'s first content, once it's [loaded].
    static func content(_ screen: Screen, loaded: Bool) {
        if loaded { mark(screen, .content) }
    }

    /// Runs one launch step as a signposted interval, logging how long it took.
    static func step<T>(_ name: StaticString, _ body: () throws -> T) rethrows -> T {
        let state = signposter.beginInterval(name)
        let started = DispatchTime.now().uptimeNanoseconds
        defer {
            signposter.endInterval(name, state)
            let elapsed = Double(DispatchTime.now().uptimeNanoseconds - started) / 1_000_000
            logger.notice("step \(name.description, privacy: .public) took \(elapsed, format: .fixed(precision: 1)) ms")
        }
        return try body()
    }
}
