import Foundation
import Network

/// The clock and the one-shot timer ``HTTPRangeByteSource``'s recovery runs on: the stall watchdog's
/// check and the retry's backoff (#896). Behind a protocol so a test steps time instead of sleeping
/// through five seconds of a stalled body.
protocol RecoveryScheduler: AnyObject {
    /// Seconds on a monotonic clock. Only differences are meaningful.
    func now() -> TimeInterval
    /// Run `work` on `queue` once `seconds` have passed.
    func schedule(after seconds: TimeInterval, on queue: DispatchQueue, _ work: @escaping () -> Void)
}

/// The real one: uptime, and `asyncAfter` on the queue asked for.
final class DispatchRecoveryScheduler: RecoveryScheduler {
    static let shared = DispatchRecoveryScheduler()

    func now() -> TimeInterval { ProcessInfo.processInfo.systemUptime }

    func schedule(after seconds: TimeInterval, on queue: DispatchQueue, _ work: @escaping () -> Void) {
        queue.asyncAfter(deadline: .now() + seconds, execute: work)
    }
}

/// Tells a byte source that the network it is talking over has changed under it (#896).
///
/// At a Wi-Fi to cellular handoff iOS aborts every connection on the old interface, but a body that
/// was already trickling can sit on a dead socket until the request times out. A source told of the
/// new path drops its transaction and asks again at once, over the path that works.
protocol NetworkPathMonitoring: AnyObject {
    /// `handler` runs, on an arbitrary queue, each time the path becomes usable again or moves to
    /// another interface — never for the path as it already was. Until ``removeObserver(_:)``.
    func addObserver(_ handler: @escaping () -> Void) -> UUID
    func removeObserver(_ id: UUID)
}

/// One `NWPathMonitor` for the process, fanned out to every live source.
final class SystemNetworkPathMonitor: NetworkPathMonitoring {
    static let shared = SystemNetworkPathMonitor()

    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "audio.network-path", qos: .utility)
    private let lock = NSLock()
    private var observers: [UUID: () -> Void] = [:]
    /// The last path seen, as the change test needs it; nil until the monitor's first report, which
    /// is the path as it already was and is never passed on. On `queue`.
    private var last: (satisfied: Bool, interface: String?)?

    private init() {
        monitor.pathUpdateHandler = { [weak self] path in self?.pathDidUpdate(path) }
        monitor.start(queue: queue)
    }

    func addObserver(_ handler: @escaping () -> Void) -> UUID {
        let id = UUID()
        lock.withLock { observers[id] = handler }
        return id
    }

    func removeObserver(_ id: UUID) {
        lock.withLock { observers[id] = nil }
    }

    private func pathDidUpdate(_ path: NWPath) {
        let current = (satisfied: path.status == .satisfied, interface: path.availableInterfaces.first?.name)
        let previous = last
        last = current
        guard let previous, Self.isReconnect(from: previous, to: current) else { return }
        let handlers = lock.withLock { Array(observers.values) }
        handlers.forEach { $0() }
    }

    /// A satisfied path that was not satisfied before, or that now leads out of another interface.
    /// A path going away is not one: there is nothing to reconnect over, and the transaction's own
    /// error and retry cover it.
    static func isReconnect(from previous: (satisfied: Bool, interface: String?), to current: (satisfied: Bool, interface: String?)) -> Bool {
        current.satisfied && (!previous.satisfied || previous.interface != current.interface)
    }
}
