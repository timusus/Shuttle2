// Adapted from Shuttle Podcasts (podcasts@9ee6e0954) mobile/ios/Playback/Sources/Playback/StartupTiming.swift — see ios/Playback/README.md.
import Foundation

/// **Where the time between a play and the first audible sample went.**
///
/// S2: one record per start of ``MusicPlaybackController`` — a load that plays, or a play of a
/// paused track — stamped on the engine queue at each stage and folded into ONE `engine: ttfa` log
/// line when the player node first renders (#687, after Podcasts' #193). It exists so a slow start
/// on the phone has a number attached to each suspect: the server's first response, FFmpeg's
/// blocking probe, the seek a resume needs, the audio session, the engine start.
///
/// Every timestamp is `ProcessInfo.systemUptime`: monotonic, so a clock adjustment mid-start
/// cannot produce a negative stage, and the deltas are what the line reports. Stages that did not
/// happen (no HTTP on a file, no seek on a fresh start, no open on a pre-opened track) print as
/// `-`, never as 0, so a missing stage cannot be read as a fast one.
///
/// The record is plain data with no lock of its own: the controller owns it on its engine queue.
struct StartupTiming: Equatable {

    enum Source: String, Equatable {
        case file, streamed
    }

    enum Start: Equatable {
        case fresh
        /// A start away from zero, which on a stream is a head probe AND a second range
        /// transaction at the offset.
        case resume(seconds: TimeInterval)
    }

    /// Whether this start paid for the open.
    enum Open: String, Equatable {
        /// Opened for this start.
        case opened
        /// Already open, or opening, when the start was asked for: a pre-opened next track a skip
        /// landed on, or a paused track being played. Its open stages print as `-`.
        case preopened
    }

    /// What a source's open learned and cost, read off it by the controller (S2).
    struct OpenStats: Equatable {
        /// The decoder's `open()` began.
        var startedAt: TimeInterval
        var finishedAt: TimeInterval?
        var probe: Probe?
        var requestIssuedAt: TimeInterval?
        var firstResponseAt: TimeInterval?
        var firstResponse: FirstResponse?
        var transactions: Int?
        var tail: Tail?
    }

    /// The first HTTP response body of the source: what the tap paid before a byte of media arrived.
    struct FirstResponse: Equatable {
        let status: Int
        /// `3xx` hops followed before this response.
        let redirects: Int
        /// Each hop's destination host, in order. Empty when there was no redirect.
        let hosts: [String]
        /// The transaction opened at a chain end remembered from an earlier play
        /// (``ResolvedURLCache``), so `redirects` is 0 because none were needed, not because the
        /// enclosure has none.
        let remembered: Bool

        init(status: Int, redirects: Int, hosts: [String], remembered: Bool = false) {
            self.status = status
            self.redirects = redirects
            self.hosts = hosts
            self.remembered = remembered
        }
    }

    /// How the mp3 footer look (`ff_id3v1_read`: seek to `size - 128`, read, seek back) was
    /// answered by the byte source. It is never waited on; `late` says the probe went without it.
    enum Tail: String, Equatable {
        /// No side fetch was needed: not an HTTP source, no total, or the first body reached the end.
        case none = "-"
        /// Served from the run's sidecar, kept by an earlier play. No network.
        case sidecar
        /// The side fetch was still in flight when the first buffer was scheduled and the decoder
        /// never looked.
        case pending
        /// The side fetch landed before the decoder looked.
        case fetched
        /// The decoder looked before the side fetch landed and was given end-of-stream instead.
        case late
        /// The side fetch failed; a look would take the ordinary route, a transaction at the tail.
        case dropped
    }

    /// What `URLSessionTaskMetrics` reported for the transaction that produced ``FirstResponse``:
    /// how the phone's 3–5 s `first-response` window splits into dns/connect/tls/server, so a slow
    /// start can be blamed on a stage instead of guessed at (#193).
    ///
    /// Metrics land only when the task itself finishes, and the first transaction is a bounded
    /// read-ahead range that keeps streaming well past the node's first render — so these never
    /// make it into the `ttfa` line, which has already gone out by then. ``HTTPRangeByteSource``
    /// logs them on their own `engine: ttfa-net` line instead, joined to the `ttfa` line by
    /// ``netKey``.
    struct NetMetrics: Equatable {
        let dnsMs: Int?
        let connectMs: Int?
        let tlsMs: Int?
        let serverMs: Int?
        let reusedConnection: Bool
        let networkProtocol: String
        /// `fetchStartDate` minus the task's own start: time spent queued inside `URLSession`
        /// before the transaction began, ahead of anything DNS/connect/server can explain.
        let queuedMs: Int?
    }

    /// What FFmpeg's `open()` learned and what it cost.
    struct Probe: Equatable {
        let codec: String
        let container: String
        /// Source bytes the decoder had consumed when `open()` returned: the probe's real cost,
        /// bounded by `probesize` only after any ID3 tag has been stepped over.
        let bytes: Int64
    }

    let source: Source
    let start: Start
    let open: Open
    let playRequestedAt: TimeInterval
    /// A paused load's start: the play arrived this long after the load was ready, and the record
    /// is still the load's, timed from the load. Nil when the start played as it loaded.
    var playAfterReadyMs: Int?
    /// `AVAudioSession.setActive(true)` returned, off main and concurrently with the open: only
    /// inside `total` when the engine start had to wait for it (``sessionWaitMs``).
    var sessionActivatedAt: TimeInterval?
    /// The engine start began waiting for the session to be active.
    var sessionAwaitedAt: TimeInterval?
    /// The decoder's `open()` began. Everything between the play and this is ``preOpenMs``: the
    /// engine queue's own backlog and the previous track's teardown.
    var probeStartedAt: TimeInterval?
    /// When the request that led to ``firstResponse`` called `resume()`.
    var requestIssuedAt: TimeInterval?
    var firstResponseAt: TimeInterval?
    var firstResponse: FirstResponse?
    var probeFinishedAt: TimeInterval?
    var probe: Probe?
    /// The source is positioned at the start frame: on a resume, its seek is done.
    var positionedAt: TimeInterval?
    var firstBufferScheduledAt: TimeInterval?
    /// What `engine.start()` took. Nil when the engine was already running.
    var engineStartMs: Int?
    /// The player node was told to play.
    var nodePlayedAt: TimeInterval?
    var firstRenderedAt: TimeInterval?
    /// The render watch gave up before the node's clock moved.
    var renderTimedOut = false
    /// Response bodies the byte source had opened when the first buffer was scheduled. A fresh
    /// stream is 1; a resume is 2 unless the head probe's window already covered the offset.
    var transactions: Int?
    /// How the footer look was answered, stamped with ``transactions``. Nil for a file.
    var tail: Tail?

    /// Above this the line is repeated at `.error`, so a slow start is one grep away.
    static let slowThresholdMs = 3000

    /// Monotonic seconds. The only clock the record should ever be stamped with.
    static func now() -> TimeInterval { ProcessInfo.processInfo.systemUptime }

    init(source: Source, start: Start, open: Open, playRequestedAt: TimeInterval = StartupTiming.now()) {
        self.source = source
        self.start = start
        self.open = open
        self.playRequestedAt = playRequestedAt
    }

    /// Takes the open's stages from `stats`, unless the open began before this start: a pre-opened
    /// track's open was paid ahead, and its stamps would read as negative stages.
    mutating func apply(_ stats: OpenStats) {
        probe = stats.probe
        transactions = stats.transactions
        tail = stats.tail
        guard open == .opened, stats.startedAt >= playRequestedAt else { return }
        probeStartedAt = stats.startedAt
        probeFinishedAt = stats.finishedAt
        requestIssuedAt = stats.requestIssuedAt
        firstResponseAt = stats.firstResponseAt
        firstResponse = stats.firstResponse
    }

    // MARK: - Deltas

    var totalMs: Int? { delta(playRequestedAt, firstRenderedAt) }
    /// Play → the audio session active. Concurrent with the open: a big number here costs the
    /// start nothing unless ``sessionWaitMs`` is also nonzero.
    var sessionMs: Int? { delta(playRequestedAt, sessionActivatedAt) }
    /// How long the engine start sat waiting for the session: zero when the activation had already
    /// returned, which is the whole point of running it concurrently.
    var sessionWaitMs: Int? { delta(sessionAwaitedAt, sessionActivatedAt).map { max($0, 0) } }
    /// Play → `open()` began.
    var preOpenMs: Int? { delta(playRequestedAt, probeStartedAt) }
    /// Play → the request that led to ``firstResponse`` called `resume()`. Nil for a file.
    var preRequestMs: Int? { delta(playRequestedAt, requestIssuedAt) }
    /// Play → first response. Nil for a file, which never opens a transaction.
    var firstResponseMs: Int? { delta(playRequestedAt, firstResponseAt) }
    /// Joins this line's `first-response` instant to the `engine: ttfa-net` line
    /// ``HTTPRangeByteSource`` logs once its ``NetMetrics`` land. Nil for a file, or a line with
    /// no first response at all.
    var netKey: Int? { firstResponseAt.map { Int(($0 * 1000).rounded()) } }
    /// First response → `open()` returned; with no response (a file, or a stream served from the
    /// run on disk) from the moment `open()` began.
    var probeMs: Int? {
        guard let probeFinishedAt else { return nil }
        return delta(firstResponseAt ?? probeStartedAt, probeFinishedAt)
    }
    /// `open()` returned (or, pre-opened, the play) → the source positioned: the resume's seek.
    var seekMs: Int? { delta(probeFinishedAt ?? playRequestedAt, positionedAt) }
    /// Positioned → the first PCM buffer at the node: the first decode and processing.
    var firstBufferMs: Int? { delta(positionedAt, firstBufferScheduledAt) }
    /// Play → the node told to play.
    var playMs: Int? { delta(playRequestedAt, nodePlayedAt) }
    /// The node told to play → its clock first advanced.
    var renderMs: Int? { delta(nodePlayedAt, firstRenderedAt) }

    var isSlow: Bool { (totalMs ?? 0) > Self.slowThresholdMs }

    private func delta(_ from: TimeInterval?, _ to: TimeInterval?) -> Int? {
        guard let from, let to else { return nil }
        return Int(((to - from) * 1000).rounded())
    }

    // MARK: - The line

    /// The one line a device log shows per start. Field order is fixed so the phone's lines can
    /// be diffed against each other; a stage that did not happen is `-`.
    var logLine: String {
        var fields: [String] = [
            "engine: ttfa total=\(ms(totalMs))",
            "source=\(source.rawValue)",
            "start=\(startDescription)",
            "open=\(open.rawValue)",
            "play-after-ready=\(ms(playAfterReadyMs))",
            "session=\(ms(sessionMs))",
            "session-wait=\(ms(sessionWaitMs))",
            "pre-open=\(ms(preOpenMs))",
        ]
        if source == .streamed {
            fields.append("redirects=\(firstResponse.map { String($0.redirects) } ?? "-")")
            let hosts = firstResponse?.hosts ?? []
            fields.append("via=\(hosts.isEmpty ? "-" : hosts.joined(separator: ","))")
            fields.append("status=\(firstResponse.map { String($0.status) } ?? "-")")
            // How the first transaction found the bytes: `remembered` skipped the chain, `chain`
            // walked it, `direct` had none to walk.
            let resolved: String
            switch firstResponse {
            case nil: resolved = "-"
            case let response? where response.remembered: resolved = "remembered"
            case let response? where response.redirects > 0: resolved = "chain"
            default: resolved = "direct"
            }
            fields.append("resolved=\(resolved)")
            fields.append("pre-request=\(ms(preRequestMs))")
            fields.append("first-response=\(ms(firstResponseMs))")
            fields.append("net-key=\(netKey.map(String.init) ?? "-")")
        }
        fields.append("probe=\(ms(probeMs))")
        fields.append("probe-bytes=\(probe.map { String($0.bytes) } ?? "-")")
        fields.append("codec=\(probe?.codec ?? "-")")
        fields.append("container=\(probe?.container ?? "-")")
        fields.append("seek=\(ms(seekMs))")
        fields.append("first-buffer=\(ms(firstBufferMs))")
        fields.append("engine-start=\(ms(engineStartMs))")
        fields.append("play=\(ms(playMs))")
        fields.append("render=\(renderTimedOut ? "timeout" : ms(renderMs))")
        if source == .streamed {
            fields.append("transactions=\(transactions.map(String.init) ?? "-")")
            fields.append("tail=\(tail?.rawValue ?? "-")")
        }
        return fields.joined(separator: " ")
    }

    private var startDescription: String {
        switch start {
        case .fresh: return "fresh"
        case let .resume(seconds): return "resume(\(Int(seconds.rounded()))s)"
        }
    }

    private func ms(_ value: Int?) -> String { Self.formatMs(value) }

    /// `Nms`, or `-` for a stage that did not happen.
    static func formatMs(_ value: Int?) -> String {
        value.map { "\($0)ms" } ?? "-"
    }

    // MARK: - The net line

    /// The `engine: ttfa-net` line ``HTTPRangeByteSource`` logs once ``NetMetrics`` land for a
    /// source's first transaction. `key` is the `ttfa` line's ``netKey`` for the same play, so a
    /// device log can join the two even though the net line always comes second.
    static func netLogLine(key: Int, host: String, status: Int, metrics: NetMetrics) -> String {
        """
        engine: ttfa-net key=\(key) host=\(host) status=\(status) dns=\(formatMs(metrics.dnsMs)) \
        connect=\(formatMs(metrics.connectMs)) tls=\(formatMs(metrics.tlsMs)) server=\(formatMs(metrics.serverMs)) \
        reused=\(metrics.reusedConnection) proto=\(metrics.networkProtocol) queued=\(formatMs(metrics.queuedMs))
        """
    }
}
