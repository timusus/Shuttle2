// Copied from Shuttle Podcasts (podcasts@9ee6e0954) mobile/ios/Playback/Sources/Playback/StartupTiming.swift — see ios/Playback/README.md.
import Foundation

/// **Where the time between the tap and the first audible sample went.**
///
/// One record per `play(url:...)`, stamped at each stage of the start and folded into ONE
/// `engine: ttfa` log line when the node first renders (#193). It exists because a slow start on
/// the phone had no number attached to any of its suspects — the redirect chain, FFmpeg's
/// blocking probe, the seek a resume needs — and the rule is to measure before changing anything.
///
/// Every timestamp is `ProcessInfo.systemUptime`: monotonic, so a clock adjustment mid-start
/// cannot produce a negative stage, and the deltas are what the line reports. Stages that did not
/// happen (no HTTP on a download, no seek on a fresh start) print as `-`, never as 0, so a missing
/// stage cannot be read as a fast one.
///
/// The record is plain data with no lock of its own: ``AVAudioEnginePlaybackController`` owns it
/// under its `stateLock`, because the stamps arrive from main, the load task and the decode queue.
struct StartupTiming: Equatable {

    enum Source: String, Equatable {
        case downloaded, streamed
    }

    enum Start: Equatable {
        case fresh
        /// A start away from zero, which on a stream is a head probe AND a second range
        /// transaction at the offset.
        case resume(seconds: TimeInterval)
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
        /// The decoder's fixed probe budget (`stream_decode.c`): reported so the line says what the
        /// number above was bounded by. Neither is tunable from this package.
        static let probeSizeBytes: Int64 = 64 * 1024
        static let analyzeSeconds: Double = 1
    }

    let source: Source
    let start: Start
    let playRequestedAt: TimeInterval
    /// `AVAudioSession.setActive(true)` returned, off main. Since #225 the activation runs
    /// concurrently with the load rather than first on the tap's thread, so this is wall-clock
    /// from the tap and is only inside `total` when the load had to wait for it: see
    /// ``sessionAwaitedAt`` and ``sessionWaitMs``.
    var sessionActivatedAt: TimeInterval?
    /// The load task, its probe done, began waiting for the session to be active — the one
    /// point the activation is on the path, because the engine must not start before it.
    var sessionAwaitedAt: TimeInterval?
    /// The previous episode's player node was swapped for a fresh one, on main: attached,
    /// connected in its place and handed to the retire queue to be stopped. The stamps from here
    /// to ``teardownFinishedAt`` split the teardown that the tap pays before the new load is even
    /// queued (#193): each call in it has, on the phone, been the one that blocked main.
    var nodeRetiredAt: TimeInterval?
    /// `reader.cancel()` returned, on main: the previous episode's decoder and byte source are
    /// told to stop.
    var readerCancelledAt: TimeInterval?
    /// `skipCueMixer.reset()` returned, on main.
    var mixerResetAt: TimeInterval?
    /// The end of what the tap pays synchronously before the load is queued.
    var teardownFinishedAt: TimeInterval?
    /// The app's byte tee for the new load was built, on main. Everything ad-skip owns for the
    /// previous episode is torn down inside that call.
    var teeCreatedAt: TimeInterval?
    /// The detached load task began running, off main.
    var loadTaskStartedAt: TimeInterval?
    /// What the retired node's `stop()` took on the retire queue, stamped from there once it
    /// returned. Not a stamp pair: the stop runs off main, concurrently with the load, and is on
    /// nobody's critical path, so a delta against the main-thread stamps would say nothing about
    /// it. Nil when it had not returned by the time the line was written: a start behind a parked
    /// engine holds the stop until `engine.start()`, which is after the probe.
    var nodeStopMs: Int?
    /// The decoder's `open()` began, on the reader's queue. Everything between the tap and this —
    /// the session, the node stop, the tee, the task hop, the credential lookup, the reader's init
    /// with its sidecar reads — is ``preOpenMs``, and is NOT the probe.
    var probeStartedAt: TimeInterval?
    /// The moment the request that led to ``firstResponse`` called `resume()`, on the reader's
    /// queue. Measurement only (#193): with ``preOpenMs`` this splits what the tap paid before a
    /// byte of the response arrived into "before the decoder asked" and "waiting on the request".
    var requestIssuedAt: TimeInterval?
    var firstResponseAt: TimeInterval?
    var firstResponse: FirstResponse?
    var probeFinishedAt: TimeInterval?
    var probe: Probe?
    /// What `connectChain` took on main once the format landed, measured there rather than
    /// stamped as a pair: it is inside ``seekMs`` and used to be most of it. Zero when the chain
    /// was already wired for the episode's format; a rewire of the player node's hop onto the
    /// mixer otherwise (#248). Nil when the load never reached the engine.
    var chainMs: Int?
    /// What `engine.start()` took on main, the other half of the engine's share of ``seekMs``.
    /// Nil when the engine was already running, so a start into a running engine reads `-`.
    var engineStartMs: Int?
    /// `reader.start(at:)` returned: the decoder is positioned, which on a resume means the seek
    /// and the range transaction it opened are done.
    var readerStartedAt: TimeInterval?
    var firstBufferScheduledAt: TimeInterval?
    var firstRenderedAt: TimeInterval?
    /// The render watch gave up before the node's clock moved.
    var renderTimedOut = false
    /// Response bodies the byte source had opened when the first buffer was scheduled. A fresh
    /// stream is 1; a resume is 2 unless the head probe's window already covered the offset.
    var transactions: Int?
    /// How the footer look was answered, stamped with ``transactions``. Nil for a download.
    var tail: Tail?

    /// Above this the line is repeated at `.error`, so a slow start is one grep away.
    static let slowThresholdMs = 3000

    /// Monotonic seconds. The only clock the record should ever be stamped with.
    static func now() -> TimeInterval { ProcessInfo.processInfo.systemUptime }

    init(source: Source, start: Start, playRequestedAt: TimeInterval = StartupTiming.now()) {
        self.source = source
        self.start = start
        self.playRequestedAt = playRequestedAt
    }

    // MARK: - Deltas

    var totalMs: Int? { delta(playRequestedAt, firstRenderedAt) }
    /// Tap → the audio session active. Concurrent with everything below since #225: a big
    /// number here costs the start nothing unless ``sessionWaitMs`` is also nonzero.
    var sessionMs: Int? { delta(playRequestedAt, sessionActivatedAt) }
    /// How long the probed load sat waiting for the session: zero when the activation had
    /// already returned by the time the probe finished, which is the whole point of running it
    /// concurrently. Nil until the load reaches the wait.
    var sessionWaitMs: Int? { delta(sessionAwaitedAt, sessionActivatedAt).map { max($0, 0) } }
    /// Tap → the player node swapped for a fresh one.
    var swapMs: Int? { delta(playRequestedAt, nodeRetiredAt) }
    /// Node swapped → the previous reader cancelled.
    var cancelMs: Int? { delta(nodeRetiredAt, readerCancelledAt) }
    /// Reader cancelled → the skip-cue mixer reset.
    var mixerMs: Int? { delta(readerCancelledAt, mixerResetAt) }
    /// Tap → the load about to be queued: the whole synchronous teardown, ``swapMs`` through
    /// ``mixerMs``. ``nodeStopMs`` is NOT inside it: on the phone the node stop was 11–48 s of
    /// main-thread time when it ran here (#193), and it now runs on the retire queue, so a
    /// teardown of more than a few ms is main blocked in something else. Nor is the session
    /// activation, since #225.
    var teardownMs: Int? { delta(playRequestedAt, teardownFinishedAt) }
    /// Teardown done → the app's tee built: the skip layer's own teardown for the old episode.
    var teeMs: Int? { delta(teardownFinishedAt, teeCreatedAt) }
    /// Tee built → the load task running: the hop off main.
    var hopMs: Int? { delta(teeCreatedAt, loadTaskStartedAt) }
    /// Tap → `open()` began: everything the start paid before the decoder saw a byte.
    var preOpenMs: Int? { delta(playRequestedAt, probeStartedAt) }
    /// Tap → the request that led to ``firstResponse`` called `resume()`. Nil for a download.
    /// Chronologically after ``preOpenMs`` — `open()` is what triggers the first fetch — so the
    /// gap between the two is FFmpeg's own work before it ever touches the byte source.
    var preRequestMs: Int? { delta(playRequestedAt, requestIssuedAt) }
    /// Tap → first response. Nil for a download, which never opens a transaction.
    var firstResponseMs: Int? { delta(playRequestedAt, firstResponseAt) }
    /// Joins this line's `first-response` instant to the `engine: ttfa-net` line
    /// ``HTTPRangeByteSource`` logs once its ``NetMetrics`` land — see that type's note on why
    /// they are never on this line. Nil for a download, or a line with no first response at all.
    var netKey: Int? { firstResponseAt.map { Int(($0 * 1000).rounded()) } }
    /// First response → `open()` returned; with no response (a download, or a stream served from
    /// the run on disk) from the moment `open()` began, so a disk-served probe is the probe alone
    /// and not the tap-to-open path in front of it. The tap is the fallback for a record with no
    /// open stamp at all.
    var probeMs: Int? { delta(firstResponseAt ?? probeStartedAt ?? playRequestedAt, probeFinishedAt) }
    /// `open()` returned → the decoder positioned. Part of ``firstBufferMs``, separated because it
    /// is the resume-only cost. Not only the decoder's seek: the chain wiring and the engine
    /// start sit on main between the probe landing and the producer being queued, and are
    /// broken out as ``chainMs`` and ``engineStartMs`` so a slow `seek` can be read.
    var seekMs: Int? { delta(probeFinishedAt, readerStartedAt) }
    /// `open()` returned → first PCM buffer at the node. Includes the seek and the first decode.
    var firstBufferMs: Int? { delta(probeFinishedAt, firstBufferScheduledAt) }
    /// First buffer scheduled → the node's clock first advanced.
    var renderMs: Int? { delta(firstBufferScheduledAt, firstRenderedAt) }

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
            "session=\(ms(sessionMs))",
            "session-wait=\(ms(sessionWaitMs))",
            "teardown=\(ms(teardownMs))",
            "swap=\(ms(swapMs))",
            "cancel=\(ms(cancelMs))",
            "mixer=\(ms(mixerMs))",
            "node-stop=\(ms(nodeStopMs))",
            "tee=\(ms(teeMs))",
            "hop=\(ms(hopMs))",
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
        }
        fields.append("pre-request=\(ms(preRequestMs))")
        fields.append("first-response=\(ms(firstResponseMs))")
        if source == .streamed {
            fields.append("net-key=\(netKey.map(String.init) ?? "-")")
        }
        fields.append("probe=\(ms(probeMs))")
        fields.append("probe-bytes=\(probe.map { String($0.bytes) } ?? "-")")
        fields.append("probe-budget=\(Probe.probeSizeBytes)B/\(Int(Probe.analyzeSeconds))s")
        fields.append("codec=\(probe?.codec ?? "-")")
        fields.append("container=\(probe?.container ?? "-")")
        fields.append("chain=\(ms(chainMs))")
        fields.append("engine-start=\(ms(engineStartMs))")
        fields.append("seek=\(ms(seekMs))")
        fields.append("first-buffer=\(ms(firstBufferMs))")
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

    /// `Nms`, or `-` for a stage that did not happen. Shared with the seek path's node-stop
    /// field, so a `restart decode` line reads like the `ttfa` line it sits next to.
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
