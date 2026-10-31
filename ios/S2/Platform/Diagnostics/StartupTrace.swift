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
    private nonisolated static let logger = Logger(log)
    private nonisolated static let signposter = OSSignposter(logHandle: log)

    private static var seen = Set<Milestone>()
    /// The app-wide milestones logged, by their name's address: a literal's text is in the binary, so a repeat looks it up
    /// without allocating a `String`.
    private static var appMilestones = Set<UInt>()

    /// The monotonic clock every duration here is read from; tests replace it.
    static var now: () -> ContinuousClock.Instant = { .now }

    /// When the kernel started this process, the earliest point there is: what runs before `S2App.init` (dyld, the
    /// static initialisers, UIKit's launch) counts too. The kernel records it on the wall clock, so that is read once,
    /// to place it on the monotonic one.
    private static let processStart: ContinuousClock.Instant = {
        let current = now()
        var info = kinfo_proc()
        var size = MemoryLayout<kinfo_proc>.stride
        var mib: [Int32] = [CTL_KERN, KERN_PROC, KERN_PROC_PID, getpid()]
        guard sysctl(&mib, 4, &info, &size, nil, 0) == 0 else { return current }
        let start = info.kp_proc.p_starttime
        var wall = timeval()
        gettimeofday(&wall, nil)
        let sinceStart = Double(wall.tv_sec - start.tv_sec) + Double(wall.tv_usec - Int32(start.tv_usec)) / 1_000_000
        return current - .seconds(sinceStart)
    }()

    private static func milliseconds(_ duration: Duration) -> Double {
        Double(duration.components.seconds) * 1000 + Double(duration.components.attoseconds) / 1e15
    }

    private static var sinceProcessStart: Int {
        Int(milliseconds(now() - processStart))
    }

    /// An app-wide milestone (the app's init, the first frame), logged the first time only.
    static func mark(_ name: StaticString) {
        let key = name.hasPointerRepresentation ? UInt(bitPattern: name.utf8Start) : UInt(name.unicodeScalar.value)
        guard appMilestones.insert(key).inserted else { return }
        signposter.emitEvent(name)
        logger.notice("\(name.description, privacy: .public) at \(sinceProcessStart) ms")
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
        let started = now()
        defer {
            signposter.endInterval(name, state)
            let elapsed = milliseconds(now() - started)
            logger.notice("step \(name.description, privacy: .public) took \(elapsed, format: .fixed(precision: 1)) ms")
        }
        return try body()
    }
}
