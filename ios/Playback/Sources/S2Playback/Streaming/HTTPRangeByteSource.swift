// Copied from Shuttle Podcasts (podcasts@9ee6e0954) mobile/ios/Playback/Sources/Playback/Streaming/HTTPRangeByteSource.swift — see ios/Playback/README.md.
import Foundation
import OSLog

/// Same category as the engine controller's and ``StreamingPCMReader``'s, with a `bytes:` prefix, so
/// one `log stream --predicate 'category == "audio-engine"'` shows the fetch and the decode of a
/// stall together — which side ran dry is the first question every streaming stall asks.
private let engineLog = Logger(subsystem: "com.simplecityapps.shuttle2", category: "network")

/// **The byte layer of the streaming player: one HTTP transaction, read blocking, teed to the spine.**
///
/// Plan: `mobile/ios/docs/plans/2026-09-09-streaming-audio-pipeline.md` §1 (the picture), §4
/// (bandwidth) and §5 items 2 and 8. FFmpeg drives a custom `AVIOContext` whose read and seek
/// callbacks land on ``StreamByteReader``; this is the HTTP implementation of it, and the same bytes
/// leave through ``AudioByteTee`` for the ad-skip scanner. There is no second fetch. What was
/// fetched is kept in ``CachedRunStore`` as one contiguous run, and replayed in a later session
/// ONLY across a seam the host's own bytes have validated: hosts re-stitch ads behind stable
/// URLs, so every transaction that would extend the run asks for `overlapBytes` before its start
/// and compares them with the run's tail (design: `docs/plans/2026-09-16-streaming-playback-cache.md`).
/// A match means the two bodies agree at the seam and the new bytes are appended; anything else
/// — a differing byte, a `200` instead of a `206`, an overlap too silent to tell — drops the run
/// and the live transaction becomes it. Bytes the run already holds are pumped into the window
/// from disk exactly as a body would be, teed and all, so nothing above this class can tell.
///
/// The transaction, throttle and read-ahead rules are lifted from ``SpineByteTeeResourceLoader``,
/// which earned each of them on device. The differences are all consequences of who is reading:
///
///  - **The read position IS the playhead.** The loader had to ask `AVPlayer` where the listener
///    was, because `AVPlayer` buffers minutes ahead of what it plays. The decoder above this class
///    pulls only a second or so ahead of the renderer, so ``position`` is the playhead proxy the
///    ceiling is measured from — the quantity the loader spent two device gates trying to obtain.
///  - **A `Range` is always bounded.** `bytes=X-` lets the host push the whole file before any
///    client-side `suspend()` can take hold, which on the 2026-09-03 gate raced the fetch 25 minutes
///    past the playhead and evicted bytes the scan had never read. The end byte is this
///    transaction's own ceiling, so there is nothing past the window in the body to arrive.
///  - **A body that ends short is a CONTINUATION.** A bounded range running out, an idle connection
///    the host dropped, a retried transport failure: the stream is picked up at the frontier, the
///    tee is told `isContinuation: true`, and nothing is paired with a position.
///  - **A seek inside the window opens nothing.** It moves ``position``; the back window
///    (`ReadAheadPolicy.backWindowBytes`) is kept for exactly that. Only a byte outside the window
///    cancels the task, drops the buffer and opens a new transaction.
///  - **Redirects are followed once, not once per transaction.** An enclosure URL is routinely two
///    `302`s from its CDN (feed host → tracking prefix → CDN), each hop a fresh TLS connection.
///    Every transaction used to start from the ORIGINAL URL and pay the whole chain again — on
///    the phone's 2026-09-15 log, five transactions before the first note, ~20 s from the tap.
///    The final URL is recorded from the redirect delegate and reused for every later transaction
///    of this source — and, through ``ResolvedURLCache``, by the NEXT play of the same enclosure,
///    whose first transaction then opens at the chain's end. The remembered end is only a hint:
///    anything but a `2xx` from it, or a failure to connect, sends this one transaction back to
///    the original URL (once) and drops the entry (#193: 5 hops cost 5.3 s before the first byte).
///  - **The file's last bytes are fetched beside the first, not after them.** FFmpeg's mp3 open
///    looks for an ID3v1 footer: a seek to `size - 128`, a 128-byte read, a seek back. Over HTTP
///    that was two extra transactions in SERIES on the critical path — cancel the head, connect
///    for the tail, cancel the tail, connect for the head again — ~2.5 s of the 8 s the phone
///    measured on The Daily (#193). Now the first `206` fires one side request for the tail, the
///    decoder's seek into it opens nothing and leaves the window alone, and the tail is kept in
///    the run's sidecar so the next play never asks for it. It is never counted as a transaction
///    and never teed: 128 bytes of footer are not audio. It is also excluded from ``inFlightBytes``
///    below, for the same reason (#261). **The side fetch is never waited on.**
///    A look that comes before it lands is answered with end-of-stream, which `ff_id3v1_read`
///    treats as "no footer" and seeks back from; the app takes its metadata from the feed, so
///    nothing is lost, and the tail still lands in the sidecar for the next play. Waiting was
///    the whole probe on a cache-served resume: the head came off disk in milliseconds and the
///    footer look then sat on a full network round trip — chain and all, since a disk head
///    remembers no URL — for 4–10 s, with no transaction counted and no first response charged.
///
/// Threading: `queue` owns every transaction decision and runs the body of every `URLSession`
/// delegate call. The session is shared (``sharedSession``, #225) and delivers on ONE serial queue
/// for every source in the process; each callback hops onto `queue` with `async` and returns at
/// once, so a source whose queue is busy — a run write, a seam compare, a sniff — never holds the
/// delegate queue and with it the OTHER source's bytes. Two sources are live on every seek and
/// every episode switch (the old reader is cancelled and the new one built before the old task's
/// close has fired), so a `sync` hop there coupled the new play's first byte to the old body's
/// last write. Per-source order survives the hop: the delegate queue is serial and `queue` is
/// serial, so what was enqueued in order runs in order, and the session hands over no body byte
/// before the response's disposition has been answered from `queue`. What the hop gives up is
/// the session's own back-pressure — it no longer waits for the delegate to return — so the bytes
/// on the hop are metered: see ``inFlightBytes``. The window itself is behind an `NSCondition`
/// because the caller is FFmpeg's read callback on the decoder's own thread and it BLOCKS — an
/// actor cannot express that. ``cancel()`` is the one call from another thread and it wakes the
/// blocked reader.
///
/// **``cancel()`` must be called.** Each task retains its delegate (this object, via
/// `URLSessionTask.delegate`) until the task completes, so an open transaction holds this object
/// alive; `cancel()` is what ends it. This mirrors `SpineByteTeeResourceLoader.invalidate()`.
final class HTTPRangeByteSource: NSObject, StreamByteReader {

    /// How often the throttle is re-asked with nothing else happening. A suspended body produces no
    /// delegate callbacks, so something has to notice that the ceiling moved.
    private static let throttleTickSeconds: Double = 0.5

    /// How many times a transport failure is retried before a read is failed. With
    /// ``retryBackoff(attempt:)`` that is ~19 s of waiting: a Wi-Fi to cellular handoff aborts every
    /// connection and takes seconds to settle, and the old budget (3 tries, ~0.7 s) was spent
    /// before the new path was up (#896). A stall spends it as an error does; only a byte past
    /// ``progressMark`` gives it back, so neither a hung host nor a range-ignoring one's prefix,
    /// re-sent on every reopen, can keep a read waiting for ever.
    static let maxRetryAttempts = 9
    private static let retryBaseSeconds: Double = 0.25
    private static let retryCapSeconds: Double = 3

    /// The wait before retry `attempt` (from 1): 0.25 s doubling, capped at 3 s.
    static func retryBackoff(attempt: Int) -> TimeInterval {
        min(retryBaseSeconds * pow(2, Double(max(attempt, 1) - 1)), retryCapSeconds)
    }

    /// How long a body that should be arriving may go without a byte before it is dropped and asked
    /// for again at the frontier (#896). A trickling connection on weak Wi-Fi never errors; it just
    /// stops, and the request timeout was all that would ever notice.
    static let stallTimeoutSeconds: Double = 5
    /// How long a body must have brought nothing for a path change to drop it. `NWPath` reports a
    /// change whenever its preferred interface does — Wi-Fi coming back, a VPN coming up — not
    /// only when the one the body is on goes, so a body still bringing bytes is left alone and the
    /// watchdog judges it if it stops (#896).
    static let pathQuietSeconds: Double = 1
    /// How often the stall watchdog looks.
    private static let watchdogTickSeconds: Double = 1
    /// The streaming session's `timeoutIntervalForRequest`: the longest a request waits between
    /// packets before it fails into the retry (#896). The default is 60 s. A suspended task is not
    /// subject to it, so the throttle's hold never times out.
    static let requestTimeoutSeconds: Double = 10

    /// The safety net on a blocked read's wait, not its pacing. Every event that can end the wait —
    /// a chunk, a completion, a failure, a cancel, a learned length, a throttle change — broadcasts
    /// the condition, so a timeout this coarse is only ever reached when something went unsignalled.
    private static let blockedWaitSeconds: Double = 1

    /// Trimming copies, so the dead prefix is allowed to grow to this before it is dropped.
    private static let trimSlackBytes: Int64 = 64 * 1024

    /// How many bytes before a seam are re-requested and compared with the run's tail. Android's
    /// `SeamValidatingDataSource` uses the same N.
    static let overlapBytes = 64 * 1024
    /// The silence guard: an overlap with fewer distinct 4 KiB blocks than this is treated as a
    /// mismatch, because digital-silence frames are bit-identical across stitches.
    static let minDistinctOverlapBlocks = 4
    static let overlapBlockBytes = 4 * 1024
    /// One pump of the run file into the window.
    private static let diskChunkBytes = 256 * 1024
    /// The ID3v1 footer FFmpeg reads on every mp3 open: exactly the last 128 bytes.
    static let tailBytes = 128

    /// How many of a held response's first bytes ``looksLikeMedia`` needs to judge it (#226).
    private static let sniffMinBytes = 12

    // MARK: - Immutable collaborators

    /// The URL the caller gave. Requests go to ``resolvedURL`` when there is one; the auth-header
    /// scope and every log line are this.
    private let url: URL
    /// Already resolved by `FeedRequestAuthorizing` before construction: a private feed's audio
    /// needs its `Authorization` header, and resolving it is an `async` lookup the decoder's
    /// blocking read cannot make. Values never reach a log — host and header count only.
    private let authHeaders: [String: String]
    /// The server's custom headers and pinned certificate (#921); nil in tests that want the system's trust. Header values
    /// are secrets and, like ``authHeaders``, go on the request only.
    private let serverPolicy: ServerConnectionPolicy?
    private let policy: ReadAheadPolicy
    /// Held strongly: the spine capture's lifetime is this playback, and a weak tee that died
    /// mid-episode would silently stop teeing while playback continued.
    private let tee: AudioByteTee?
    /// Where fetched bytes are kept between sessions. Nil in tests that want the network only.
    private let runStore: CachedRunStore?
    /// The run's key: the URL as given, so a later play of the same enclosure finds it.
    /// S2: less its per-play session id and token (``StreamCacheKey``), which change on every play (#822).
    private let runKey: String
    /// S2: what ``resolvedURLs`` is keyed by, the URL less its per-play parameters, as ``runKey`` is.
    private let resolvedKey: URL
    /// Where the chain's end is kept between plays. Nil in tests that want every hop walked.
    private let resolvedURLs: ResolvedURLCache?

    private let queue = DispatchQueue(label: "audio.http-range-byte-source", qos: .userInitiated)
    /// Shared across sources; see ``sharedSession``. Tasks are addressed to this object with
    /// `URLSessionTask.delegate`, so the session itself has no delegate and no source sees another
    /// source's callbacks.
    private let session: URLSession
    /// The stall watchdog's and the retry backoff's clock and timer.
    private let scheduler: RecoveryScheduler
    /// Tells this source when the network moved under it; observed from construction to ``cancel()``.
    private let pathMonitor: NetworkPathMonitoring?
    private var pathObserver: UUID?

    /// The one session every source in the process uses (#225).
    ///
    /// A `URLSession` owns its connection pool. With a session per source, the second play from a
    /// host paid DNS, TCP and TLS again — a cold handshake on every tap — and the `ttfa-net` line
    /// never once said `reused=true`. One session keeps the host's connection warm between plays
    /// and across the retire-and-restart the engine does on a seek.
    static let sharedSession: URLSession = makeSession(configuration: .default)

    /// A session shaped for this class. `configuration` is edited: URLCache is not the cache — it
    /// validates on headers, and a host's re-stitched ad break arrives with the same
    /// Content-Length and a fresh ETag every time. The run store validates on bytes. The request
    /// timeout is ``requestTimeoutSeconds``; connectivity waiting is left as the caller set it.
    /// The delegate queue is serial, as `URLSession` requires for ordered delivery; every callback
    /// for every task hops from it onto its source's own `queue` and returns at once (see the
    /// type's threading note), so no source's work is ever done on it.
    static func makeSession(configuration: URLSessionConfiguration) -> URLSession {
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        configuration.urlCache = nil
        configuration.timeoutIntervalForRequest = requestTimeoutSeconds
        let operationQueue = OperationQueue()
        operationQueue.maxConcurrentOperationCount = 1
        operationQueue.underlyingQueue = sessionDelegateQueue
        return URLSession(configuration: configuration, delegate: nil, delegateQueue: operationQueue)
    }

    /// `OperationQueue.underlyingQueue` is `unowned(unsafe)`: a queue made inline there is gone
    /// the moment the statement ends. Held here for the life of the process instead.
    private static let sessionDelegateQueue = DispatchQueue(label: "audio.http-range-session", qos: .userInitiated)

    // MARK: - Window state, behind `condition`

    private let condition = NSCondition()
    private var window: [UInt8] = []
    /// Absolute offset of `window[0]`.
    private var windowStart: Int64 = 0
    /// One past the last byte held: `windowStart + window.count`.
    private var frontier: Int64 = 0
    /// Bumped on every ``resetWindowLocked(at:)``: the identity of the window a transaction's
    /// verdict was computed against, so a later reset can be told apart from one that coincidentally
    /// lands the frontier back on the same value. See ``urlSession(_:task:didCompleteWithError:)``.
    private var windowGeneration: UInt64 = 0
    private var positionValue: Int64 = 0
    private var totalLengthValue: Int64?
    private var durationHintValue: TimeInterval?
    private var failureMessage: String?
    private var isCancelled = false
    /// This source holds ``runStore``'s run open (``CachedRunStore/retain(_:)``). Under `condition`.
    private var runRetained = false
    /// A blocked read has been asked to come back so the caller can seek. Unlike ``isCancelled``
    /// this is cleared — by ``clearInterrupt()``, which the decoder calls as part of that seek —
    /// and the transaction it was waiting on is left alone, because the seek that follows will
    /// either be served from the window or open its own.
    private var isInterrupted = false
    /// Which seek the transaction opened next belongs to. The engine controller's seek generation,
    /// carried through so the tee can tell an anchor that is for THIS seek from one a later seek
    /// has already superseded.
    private var seekGenerationValue = 0
    /// No more bytes will ever arrive: the resource was read to its end.
    private var streamEnded = false
    /// The resource's last ``tailBytes``, once fetched or loaded from the sidecar; `tailStart` is
    /// the absolute offset of `tail[0]`. Served by ``read(into:maxLength:)`` when the position is
    /// inside it, without touching the window.
    private var tail: Data?
    private var tailStart: Int64 = 0
    /// The side request for the tail is in flight. A seek into the tail's range opens nothing
    /// while it is; a read there before it lands is answered with end-of-stream, never a wait.
    private var tailPending = false
    /// How the footer look went, for the `ttfa` line. `late` is sticky: once the decoder looked
    /// before the side fetch landed, a later arrival does not make the probe's answer any better.
    private var tailStatusValue: StartupTiming.Tail = .none
    /// The read position was put inside the tail by a seek. While set the window is not trimmed
    /// (the position is far ahead of it and would otherwise drop every byte) and the throttle is
    /// measured from the window's start: the decoder is about to seek straight back.
    private var readingTail = false
    private var bytesFetchedValue: Int64 = 0
    private var transactionCountValue: Int = 0
    /// `3xx` hops followed so far, and each one's destination host. With the resolved URL reused
    /// these only grow on the first transaction, which is the one the start timing charges.
    private var redirectHopsValue: Int = 0
    private var redirectHostsValue: [String] = []
    /// The first response this source received, stamped when it landed. Measurement only (#193).
    private var firstResponseValue: StartupTiming.FirstResponse?
    private var firstResponseAtValue: TimeInterval?
    /// The identity of the task that produced ``firstResponseValue``, so the metrics delegate
    /// (which can fire for a retried or superseded task too) knows which one to keep. Measurement
    /// only (#193).
    private var firstResponseTaskIdentifier: ObjectIdentifier?
    /// When `resume()` was called on the request that led to ``firstResponseValue``. Reset by
    /// ``fallBackFromRememberedURL(reason:)`` along with the redirect counters, for the same
    /// reason: a remembered URL's failed attempt is not the request the timing charges.
    private var requestIssuedAtValue: TimeInterval?
    /// What `URLSessionTaskMetrics` reported for ``firstResponseTaskIdentifier``'s task. Measurement
    /// only (#193); see ``StartupTiming/NetMetrics``.
    private var netMetricsValue: StartupTiming.NetMetrics?

    // MARK: - Pacing the body, behind `condition`

    /// `task`, mirrored where the delegate queue can see it: the one task whose `suspend()` and
    /// `resume()` the two pause reasons below are applied to. Set when a transaction opens,
    /// cleared when its task closes or is cancelled, always under `condition`.
    private var pacedTask: URLSessionDataTask?
    /// The read-ahead throttle wants the body held: the frontier has reached the ceiling. Decided
    /// on `queue` by ``applyThrottle()``.
    private var pausedForThrottle = false
    /// The hop wants the body held: more than ``inFlightHighWaterBytes`` are queued for `queue`
    /// and not yet handled. Raised on the delegate queue as the chunk that crossed the mark is
    /// enqueued, lowered on `queue` as the backlog drains past ``inFlightLowWaterBytes``.
    private var pausedForInbound = false
    /// What `pacedTask` has actually been told. `suspend()` and `resume()` are called from exactly
    /// one place, ``syncPacingLocked()``, on the OR of the two reasons, so neither reason can undo
    /// the other's hold and neither depends on whether the task counts its suspends.
    private var taskIsSuspended = false
    /// Body bytes handed over by the session and enqueued for `queue`, not yet handled there.
    /// The bound the async hop needs: a `Range` is always bounded, so for an honouring host the
    /// backlog can never exceed one window anyway, but a host that answers `200` to a `Range`
    /// sends the whole episode, and before the hop the only thing holding it at the ceiling was
    /// the session waiting on the delegate. Now this is: past the high-water mark the task is
    /// suspended from the delegate queue itself, without waiting for `queue` to get a turn.
    /// `suspend()` stops the socket being read, not the delivery of what CFNetwork had already
    /// read — on loopback that is up to ~2 MiB more after the mark — so the worst case is the
    /// mark plus CFNetwork's own read-ahead, which is a constant, not the episode.
    ///
    /// Excludes the tail fetch's chunks (see ``tailTaskMirror``): they are ``tailBytes`` (128)
    /// total, too small to matter against the 512 KiB mark on their own, and counting them broke
    /// the bound instead of protecting it — the gate below only suspends ``pacedTask``, which the
    /// tail task is never, so a tail chunk that happened to cross the mark left the counter over
    /// it with nothing suspended until the paced task's own next chunk arrived (#261).
    private var inFlightBytes = 0
    private var peakInFlightBytes = 0
    private var inboundPauseCount = 0
    /// Tail-fetch bytes handed over and left out of ``inFlightBytes`` (#261). Written on every
    /// build by ``inboundDidEnqueue(_:for:)``, so it lives outside the tests-only block: declared
    /// inside it, every Release build failed to compile.
    private var tailBytesEnqueued = 0
    private static let inFlightHighWaterBytes = 512 * 1024
    private static let inFlightLowWaterBytes = 128 * 1024
    /// ``tailTask``, mirrored where the delegate queue can see it: `tailTask` itself is on `queue`
    /// only, but ``inboundDidEnqueue(_:for:)`` runs on the delegate queue and needs to tell the
    /// tail task's chunks apart from the paced task's before they are counted. Set when the side
    /// request opens, cleared when it closes, always under `condition`.
    private var tailTaskMirror: URLSessionDataTask?

    // MARK: - Transaction state, on `queue`

    private var task: URLSessionDataTask?
    private var invalidated = false
    private var ticker: DispatchSourceTimer?
    /// Where the CURRENT transaction's body begins. A continuation does not move it further than
    /// the frontier it resumed at; a 200 that ignored the `Range` resets it to 0.
    private var transactionStart: Int64 = 0
    /// The last byte the CURRENT transaction asked for. Decides whether the tail needs its own
    /// request or is coming in this body anyway.
    private var transactionEndByte: Int64 = 0
    /// When each transaction was opened (on `queue`), for its close line's duration and throughput.
    private var taskOpenedAt: [ObjectIdentifier: TimeInterval] = [:]
    private var openIsContinuation = false
    /// The side request for the resource's last bytes, if one is in flight. Routed apart from
    /// `task` in every delegate callback; never counted, never teed, never retried.
    private var tailTask: URLSessionDataTask?
    private var tailBuffer = Data()
    /// ``seekGeneration`` as it stood when the CURRENT transaction was opened. Captured here rather
    /// than read when the response lands: a second seek can bump the generation while the first
    /// request is still in flight, and the tee has to be able to see that the anchor it is holding
    /// belongs to the newer one.
    private var transactionSeekGeneration = 0
    /// ``windowGeneration`` of the window the CURRENT transaction fills. A seek resets the window
    /// on the caller's thread and queues its own transaction behind whatever `URLSession` has
    /// already handed this queue, so a chunk — or a close — of the body that seek superseded can
    /// run while `task` is still that body's task. Identity cannot tell them apart; this can:
    /// every delegate callback that would touch the window checks it and drops out on a mismatch,
    /// leaving the seek's queued `startTransaction` to open the window it reset (#224).
    private var transactionWindowGeneration: UInt64 = 0
    /// A remembered URL's `200` whose headers say nothing about what the body is, held with its
    /// bytes so far — accumulated across chunks, because a chunk boundary is not a media boundary
    /// and the first one alone can land under ``sniffMinBytes`` — until enough of them do:
    /// accepted, with every buffered byte handed on, if they look like audio; one fallback to the
    /// original URL if they look like a page or the transaction closes too short (#226).
    private var unsniffedResponse: (task: URLSessionDataTask, http: HTTPURLResponse, response: URLResponse, buffer: Data)?
    private var hasOpened = false
    private var wantsResume = false
    private var retryAttempts = 0
    /// The window a retry reopens, while its backoff is running: nothing is open, and something
    /// will be. Cleared, and ``retryGeneration`` bumped, by every transaction opened meanwhile, so a
    /// backoff that a seek, a path change or a newer retry has already acted on does nothing when
    /// it fires.
    private var pendingRetryWindow: UInt64?
    private var retryGeneration = 0
    /// When the CURRENT transaction last showed progress — opened, answered, delivered a chunk — or
    /// last had no reason to (held by the throttle, nothing open). On ``scheduler``'s clock. A
    /// range-ignoring host's prefix counts: the connection is alive, and a prefix longer than the
    /// stall timeout would otherwise be dropped and asked for from 0 again until the budget ran out.
    private var lastProgressAt: TimeInterval = 0
    /// When the CURRENT transaction last answered or brought a byte; never, until it has. What a
    /// path change asks: is this body still flowing? On ``scheduler``'s clock.
    private var lastByteAt: TimeInterval = -.infinity
    /// The furthest the window has been filled since the last seek. Only a chunk that takes the
    /// frontier past it is progress that resets ``retryAttempts``: a reopen on a range-ignoring
    /// host starts again from 0, and its prefix moves the frontier without bringing anything new.
    private var progressMark: Int64 = 0
    /// The CURRENT transaction was opened for a seek (or the first read), not to carry a body on.
    /// ``openIsContinuation`` is the tee's view, which an unvalidated seam also clears.
    private var transactionIsSeek = false
    private var watchdogArmed = false
    private var appetiteHolders = 0
    /// This host answered a `Range` with a whole-body `200`. Latched, so the next transaction is
    /// opened as what it will actually be — a stream from 0 — instead of being re-declared
    /// mid-flight, and so the warning is logged once per source rather than once per body.
    private var hostIgnoresRange = false
    private var ensureFetchingCallsValue = 0
    /// Where the original URL's redirect chain ends, once followed — or, from construction, where
    /// ``ResolvedURLCache`` says it ended last time. Nil until a redirect has been seen. On `queue`.
    private var resolvedURL: URL?
    /// ``resolvedURL`` came from the cache and has not yet been proven by a `2xx` this play. While
    /// true, a bad answer from it means "go back to the original URL", not "retry". On `queue`.
    private var resolutionUnproven = false
    /// The chain's end has been written to the cache this play. On `queue`.
    private var resolutionRecorded = false

    /// What the CURRENT transaction's bytes do to the run on disk. Decided once at open from where
    /// the transaction starts relative to the run, and advanced as the body arrives.
    private enum RunWrite {
        /// Nothing is written: the body is somewhere the run is not (a seek past its end).
        case none
        /// The body starts at the run's end and has been validated (or there was no run): every
        /// chunk goes on the end of the run.
        case append
        /// The first chunk drops the run and begins a new one at `at`. Deferred to arrival so a
        /// request that dies leaves the old run intact.
        case replaceOnArrival(at: Int64)
        /// The body was asked for `expected.count` bytes before the seam `at`; they are compared
        /// with `expected` and discarded, and the run is appended to (match) or replaced (not).
        case overlap(expected: Data, matched: Int, mismatched: Bool, at: Int64)
        /// The body starts before the run: when the window has the run's first bytes, compare them
        /// and either hand over to the disk or replace the run with the live body.
        case watchRunStart(runStart: Int64)
    }
    private var runWrite: RunWrite = .none
    /// Non-nil while the window is being filled from the run file rather than a body: the next
    /// byte to pump. On `queue`.
    private var diskCursor: Int64?
    /// A `watchRunStart` match: hand over to the disk once the cancelled body has closed.
    private var switchToDiskAtClose = false

    /// - Parameters:
    ///   - authHeaders: resolved by `FeedRequestAuthorizing` at the call site — see
    ///     `AVAudioEnginePlaybackController.play()`. Empty for a public feed.
    ///   - session: the process-wide session, ``sharedSession``; a test hands in its own from
    ///     ``makeSession(configuration:)`` so its connections do not outlive it.
    ///   - tee: where the raw bytes go for ad-skip. Nil when the spine is off.
    ///   - runStore: where the fetched bytes are kept and read back from. Nil only in tests.
    ///   - resolvedURLs: where the redirect chain's end is kept between plays. Nil only in tests.
    ///   - scheduler: the clock and timer of the stall watchdog and the retry backoff; a test's steps.
    ///   - pathMonitor: network path changes, which reopen the transaction at once. Nil to ignore them.
    init(
        url: URL,
        authHeaders: [String: String],
        readAhead: ReadAheadPolicy = .default,
        session: URLSession = HTTPRangeByteSource.sharedSession,
        tee: AudioByteTee? = nil,
        runStore: CachedRunStore? = .shared,
        resolvedURLs: ResolvedURLCache? = .shared,
        scheduler: RecoveryScheduler = DispatchRecoveryScheduler.shared,
        pathMonitor: NetworkPathMonitoring? = SystemNetworkPathMonitor.shared,
        serverPolicy: ServerConnectionPolicy? = ServerConnections.policy
    ) {
        self.url = url
        self.serverPolicy = serverPolicy
        self.authHeaders = authHeaders
        self.policy = readAhead
        self.tee = tee
        self.runStore = runStore
        self.resolvedKey = StreamCacheKey.stableURL(for: url)
        self.runKey = resolvedKey.absoluteString
        self.resolvedURLs = resolvedURLs
        // Before any transaction: the first one is the whole point of remembering.
        if let remembered = resolvedURLs?.resolved(for: resolvedKey) {
            resolvedURL = remembered
            resolutionUnproven = true
        }
        self.session = session
        self.scheduler = scheduler
        self.pathMonitor = pathMonitor
        super.init()
        // Weak: the monitor outlives every source. ``cancel()`` removes the entry, and `deinit` for
        // a source that was never cancelled.
        pathObserver = pathMonitor?.addObserver { [weak self] in
            guard let self else { return }
            self.queue.async { self.pathDidChange() }
        }
        runStore?.retain(runKey)
        runRetained = true
        runStore?.touch(runKey)
        let run = runStore?.run(for: runKey)
        // The sidecar's total lets FFmpeg's `AVSEEK_SIZE` be answered before any response has.
        if let total = run?.totalLength {
            totalLengthValue = total
            if let kept = runStore?.tail(for: runKey), kept.count == Self.tailBytes, total >= Int64(kept.count) {
                tail = kept
                tailStart = total - Int64(kept.count)
                tailStatusValue = .sidecar
            }
        }
        engineLog.info(
            """
            bytes: source host=\(url.host ?? "?", privacy: .public) authHeaders=\(authHeaders.count) \
            window=\(readAhead.windowBytes) run=\(run?.start ?? -1)..<\(run?.end ?? -1)
            """
        )
    }

    deinit {
        if let pathObserver { pathMonitor?.removeObserver(pathObserver) }
        releaseRun()
    }

    /// Let eviction have the run again, once: on ``cancel()``, or `deinit` for a source never cancelled.
    private func releaseRun() {
        condition.lock()
        let held = runRetained
        runRetained = false
        condition.unlock()
        if held { runStore?.release(runKey) }
    }

    #if DEBUG
    /// Where requests are going right now. Tests only.
    var resolvedURLForTesting: URL? { queue.sync { resolvedURL } }
    /// The session this source's tasks go through. Tests only.
    var sessionForTesting: URLSession { session }
    /// Body bytes on the hop right now, the most there have ever been, and how many times the
    /// budget has suspended the task. Tests only.
    var inFlightBytesForTesting: Int { withLock { inFlightBytes } }
    var peakInFlightBytesForTesting: Int { withLock { peakInFlightBytes } }
    var inboundPauseCountForTesting: Int { withLock { inboundPauseCount } }
    static var inFlightHighWaterBytesForTesting: Int { inFlightHighWaterBytes }
    /// Returns once everything queued for `queue` so far has run — a `cancel()` included. Tests only.
    func drainQueueForTesting() { queue.sync {} }
    /// A retry's backoff is running. Tests only.
    var retryPendingForTesting: Bool { queue.sync { pendingRetryWindow != nil } }
    /// Retries spent since the last progress. Tests only.
    var retryAttemptsForTesting: Int { queue.sync { retryAttempts } }
    /// Queue `work` on `queue`, behind everything already there. Tests only.
    func enqueueForTesting(_ work: @escaping () -> Void) { queue.async(execute: work) }
    /// Tail-fetch bytes the delegate has handed over, which the budget leaves out (#261). Tests only.
    var tailBytesEnqueuedForTesting: Int { withLock { tailBytesEnqueued } }
    /// Run once, on `queue`, at the door of the next body chunk — before the chunk has looked at
    /// the window. A test that blocks in it holds the chunk while it resets the window from
    /// another thread: the ordering #224 is about, which loopback closes too fast to reach on
    /// its own. Consumed by the first chunk that finds it.
    var testBeforeNextChunk: (() -> Void)?
    /// Run on `queue` at the door of every transaction, with the offset it opens at. A test that
    /// blocks in it holds the queue after everything queued ahead of the transaction has run and
    /// before the transaction resets the window: the gap a stale verdict lives in (#315). Set it
    /// before the first transaction.
    var testBeforeTransaction: ((Int64) -> Void)?
    #endif

    // MARK: - Diagnostics

    /// Total bytes handed over by `URLSession` across every transaction.
    var bytesFetched: Int64 { withLock { bytesFetchedValue } }
    /// How many response bodies have been opened. One per play and per outside-the-window seek,
    /// plus one per continuation.
    var transactionCount: Int { withLock { transactionCountValue } }
    /// Redirects followed across the source's life — in practice, the first transaction's chain.
    var redirectHops: Int { withLock { redirectHopsValue } }
    /// The first response's status and redirect chain, or nil until one has landed.
    var firstResponse: StartupTiming.FirstResponse? { withLock { firstResponseValue } }
    /// When ``firstResponse`` landed, on ``StartupTiming/now()``'s clock.
    var firstResponseAt: TimeInterval? { withLock { firstResponseAtValue } }
    /// When `resume()` was called on the request that led to ``firstResponse``.
    var requestIssuedAt: TimeInterval? { withLock { requestIssuedAtValue } }
    /// The `URLSessionTaskMetrics` split for the transaction that produced ``firstResponse``, or
    /// nil until the task finishes. Measurement only (#193).
    var netMetrics: StartupTiming.NetMetrics? { withLock { netMetricsValue } }
    /// How the footer look was answered: from the sidecar, from a side fetch that had landed,
    /// with end-of-stream because it had not (`late`), or not at all.
    var tailStatus: StartupTiming.Tail { withLock { tailStatusValue } }

    /// How many times a blocked read has re-armed the fetch. **A test-only counter, and the only
    /// way to see the difference between a read that waits and a read that spins**: both deliver
    /// the same bytes, and the old 50 ms poll re-armed this for the whole duration of a stall.
    var ensureFetchingCalls: Int { withLock { ensureFetchingCallsValue } }

    /// Bytes fetched but not yet read by the decoder. Half of the engine controller's estimate of
    /// how much audio is buffered ahead of the listener: the other half is what is already
    /// scheduled at the player node, and neither alone is the answer.
    var bufferedAheadBytes: Int64 { withLock { max(frontier - positionValue, 0) } }

    /// Called on the reading thread each time ``read(into:maxLength:)`` has waited ``blockedWaitSeconds`` for
    /// bytes. Set before the first read.
    var onReadWaiting: (() -> Void)? {
        get { withLock { onReadWaitingValue } }
        set { withLock { onReadWaitingValue = newValue } }
    }

    private var onReadWaitingValue: (() -> Void)?

    /// The first byte the window does not hold yet: where a continuation opens. Distinct from the
    /// end of the range the origin was asked for, which a throttled body has not reached.
    var fetchFrontier: Int64 { withLock { frontier } }

    /// Media duration in seconds when the caller knows it, so the seconds-based read-ahead window
    /// can be turned into bytes (`totalBytes / duration`). Without it the policy's byte default is
    /// used, exactly as the loader did before its time base resolved.
    var durationHint: TimeInterval? {
        get { withLock { durationHintValue } }
        set {
            withLock {
                durationHintValue = newValue
                // The ceiling moved, which is one of the things a blocked read is waiting on.
                condition.broadcast()
            }
            queue.async { self.applyThrottle() }
        }
    }

    // MARK: - StreamByteReader

    var totalLength: Int64? { withLock { totalLengthValue } }

    var position: Int64 { withLock { positionValue } }

    func read(into buffer: UnsafeMutableRawPointer, maxLength: Int) throws -> Int {
        guard maxLength > 0 else { return 0 }
        var armedFetch = false
        condition.lock()
        while true {
            if isCancelled {
                condition.unlock()
                throw StreamByteReaderError.cancelled
            }
            if isInterrupted {
                condition.unlock()
                throw StreamByteReaderError.interrupted
            }
            if let failureMessage {
                condition.unlock()
                throw StreamByteReaderError.transport(failureMessage)
            }
            if positionValue >= windowStart, frontier > positionValue {
                let offset = Int(positionValue - windowStart)
                let count = min(maxLength, Int(frontier - positionValue))
                window.withUnsafeBytes { raw in
                    if let base = raw.baseAddress { memcpy(buffer, base + offset, count) }
                }
                positionValue += Int64(count)
                trimLocked()
                condition.unlock()
                // The read position is the ceiling's base, so taking bytes is what raises it.
                queue.async { self.applyThrottle() }
                return count
            }
            if let tail, positionValue >= tailStart, positionValue < tailStart + Int64(tail.count) {
                // The footer, from the side fetch or the sidecar: no transaction, and the window
                // is left exactly as it is for the seek back that follows.
                let offset = Int(positionValue - tailStart)
                let count = min(maxLength, tail.count - offset)
                tail.withUnsafeBytes { raw in
                    if let base = raw.baseAddress { memcpy(buffer, base + offset, count) }
                }
                positionValue += Int64(count)
                condition.unlock()
                return count
            }
            if readingTail {
                // The look came before the side fetch landed (or after it failed). End-of-stream,
                // now: `ff_id3v1_read` takes a short read as "no footer" and seeks straight back,
                // and the window it seeks back into is untouched. Waiting here was the 4–10 s
                // probe on a cache-served resume (#193).
                if tailStatusValue != .late {
                    tailStatusValue = .late
                    engineLog.info("bytes: tail late host=\(self.url.host ?? "?", privacy: .public) at=\(self.positionValue)")
                }
                condition.unlock()
                return 0
            }
            if isEndOfStreamLocked() {
                condition.unlock()
                return 0
            }
            if !armedFetch {
                // Nothing to serve and nothing asked for: arm the fetch ONCE. Re-arming on every
                // wakeup is what the 50 ms poll did, and across a real cellular stall that is
                // hundreds of hops onto the transaction queue for a transaction that is already
                // open — work that cannot make the bytes arrive sooner.
                armedFetch = true
                condition.unlock()
                queue.async { self.ensureFetching() }
                condition.lock()
                continue
            }
            // Every path that can produce a byte, end the stream or fail it broadcasts, so this
            // sleeps until there is something to do rather than until a poll interval elapses.
            if !condition.wait(until: Date().addingTimeInterval(Self.blockedWaitSeconds)), let onReadWaiting = onReadWaitingValue {
                condition.unlock()
                onReadWaiting()
                condition.lock()
            }
        }
    }

    func seek(to offset: Int64) throws {
        condition.lock()
        if isCancelled {
            condition.unlock()
            throw StreamByteReaderError.cancelled
        }
        if isInterrupted {
            condition.unlock()
            throw StreamByteReaderError.interrupted
        }
        if offset < 0 || (totalLengthValue.map { offset > $0 } ?? false) {
            condition.unlock()
            throw StreamByteReaderError.unseekable
        }
        if offset == positionValue {
            condition.unlock()
            return
        }
        // Inside what this transaction already holds — including the byte the fetch is about to
        // write next — is not a seek at all as far as the network is concerned.
        if offset >= windowStart, offset <= frontier, hasBufferLocked() {
            positionValue = offset
            readingTail = false
            trimLocked()
            condition.unlock()
            queue.async { self.applyThrottle() }
            return
        }
        // Inside the tail, held or on its way: the ID3v1 look FFmpeg takes on every mp3 open.
        // The window stays as it is, because the next seek is back into it.
        if tailHoldsLocked(offset) {
            positionValue = offset
            readingTail = true
            condition.unlock()
            return
        }
        positionValue = offset
        readingTail = false
        resetWindowLocked(at: offset)
        failureMessage = nil
        streamEnded = false
        condition.broadcast()
        condition.unlock()
        queue.async { self.startTransaction(at: offset, isContinuation: false) }
    }

    func cancel() {
        condition.lock()
        let wasCancelled = isCancelled
        isCancelled = true
        condition.broadcast()
        condition.unlock()
        guard !wasCancelled else { return }
        releaseRun()
        if let pathObserver { pathMonitor?.removeObserver(pathObserver) }
        queue.async {
            self.invalidated = true
            self.ticker?.cancel()
            self.ticker = nil
            self.task?.cancel()
            self.task = nil
            self.withLock { self.pacedTask = nil; self.tailTaskMirror = nil }
            self.tailTask?.cancel()
            self.tailTask = nil
            // The session is shared and stays; the cancelled tasks complete and release this
            // object as their delegate.
        }
    }

    func interrupt() {
        condition.lock()
        isInterrupted = true
        // Every waiter re-checks the flag it was woken for, so this is the whole of it: the
        // transaction stays open and its bytes stay in the window for the seek to reuse.
        condition.broadcast()
        condition.unlock()
    }

    func clearInterrupt() {
        condition.lock()
        isInterrupted = false
        condition.unlock()
    }

    /// **Rule 8's one lever**: drop the transaction in flight and open a fresh one at the frontier, on
    /// `queue`. What a path change does to a body that has gone quiet (``pathDidChange()``).
    ///
    /// A `seek(to:)` inside the window never touches the network — it is the whole point of the
    /// window — so a player whose decoder is parked in a read at the frontier, on a body the host
    /// has stopped sending, has nothing to seek to that would make a new request. This is what the
    /// `AVPlayer` re-seek did for AVFoundation, spelled out: cancel the request the host went quiet
    /// on and ask again for the same bytes. A continuation, so the window and its offsets survive
    /// and nothing already decoded is thrown away. A no-op on a source that has ended, been
    /// cancelled or never opened. A pending retry is superseded, since this is that retry, sooner.
    /// At the ceiling nothing is opened: the task is dropped and the throttle opens the continuation
    /// once the window has room, as it does for a body that ended short.
    private func reopenAtFrontier(reason: String) {
        guard !invalidated, hasOpened, !isComplete, diskCursor == nil else { return }
        let (at, failed) = withLock { (frontier, failureMessage != nil) }
        // A read already failed has nothing waiting on it; the seek that clears it opens its own.
        guard !failed else { return }
        engineLog.info("bytes: reopen host=\(self.url.host ?? "?", privacy: .public) at=\(at) reason=\(reason, privacy: .public)")
        if at < readAheadCeiling() {
            startTransaction(at: at, isContinuation: true, window: transactionWindowGeneration)
            return
        }
        dropPendingRetry()
        dropTask(endedAt: at)
        wantsResume = true
    }

    /// Cancel the open body, if there is one, and close its transaction for the tee: the task's own
    /// completion arrives for a task that is no longer `task`, and does nothing.
    private func dropTask(endedAt: Int64) {
        guard let task else { return }
        task.cancel()
        self.task = nil
        withLock { pacedTask = nil }
        tee?.byteSourceDidCloseTransaction(endedAtByte: endedAt)
    }

    /// The network may have moved under the transaction (#896). A backoff waiting out the old path
    /// is cut short; a body that has gone quiet for ``pathQuietSeconds`` is dropped now rather than
    /// when it errors or the watchdog notices; a body still bringing bytes is on a path that works,
    /// whatever `NWPath` now prefers, and is left alone. Each path acted on is a new start, so the
    /// retry budget is too. Not on a cancelled source: the change can have been queued before the
    /// ``cancel()`` that invalidates it.
    private func pathDidChange() {
        guard !invalidated, !withLock({ isCancelled }) else { return }
        if pendingRetryWindow != nil {
            // The backoff was waiting out the old path; the new one is worth trying now.
            engineLog.info("bytes: retry host=\(self.url.host ?? "?", privacy: .public) reason=path")
            retryAttempts = 0
            retryNow()
            return
        }
        // Nothing in flight: the throttle opens what is needed over the new path.
        guard hasOpened, task != nil else { return }
        let quiet = scheduler.now() - lastByteAt
        guard quiet >= Self.pathQuietSeconds else {
            engineLog.info("bytes: path change host=\(self.url.host ?? "?", privacy: .public) — body still flowing, kept")
            return
        }
        retryAttempts = 0
        reopenAtFrontier(reason: "path")
    }

    /// Open the transaction a pending retry is waiting to, at the frontier. A continuation, not an
    /// anchor: a retry says nothing about where the listener is.
    private func retryNow() {
        guard let window = pendingRetryWindow else { return }
        startTransaction(at: withLock { frontier }, isContinuation: true, window: window)
    }

    /// The transaction died at `endedAt` — an error, or a stall the watchdog dropped — and nothing is
    /// open: retry it after the next backoff, or fail the read with `failure` once the budget is
    /// spent. On `queue`.
    private func retryOrFail(at endedAt: Int64, reason: String, failure: String) {
        guard retryAttempts < Self.maxRetryAttempts else {
            fail(failure)
            return
        }
        retryAttempts += 1
        // A retry resumes at the byte the body stopped at, and says nothing about where the
        // listener is: a continuation, not an anchor.
        let backoff = Self.retryBackoff(attempt: retryAttempts)
        engineLog.error(
            """
            bytes: retry host=\(self.url.host ?? "?", privacy: .public) at=\(endedAt) attempt=\(self.retryAttempts) \
            in=\(backoff, privacy: .public)s \(reason, privacy: .public)
            """
        )
        pendingRetryWindow = transactionWindowGeneration
        let generation = retryGeneration
        scheduler.schedule(after: backoff, on: queue) { [weak self] in
            guard let self, !self.invalidated, self.retryGeneration == generation else { return }
            self.retryNow()
        }
    }

    /// A transaction is being opened (or the reopen decided against one): any backoff still to
    /// fire belongs to the transaction before, and must not open another behind this one.
    private func dropPendingRetry() {
        pendingRetryWindow = nil
        retryGeneration += 1
    }

    /// Tag the next transaction with the seek it is being opened for. Set by the reader before it
    /// asks the decoder to seek, read by the tee's open callback — see ``SpineEngineCapture``.
    var seekGeneration: Int {
        get { withLock { seekGenerationValue } }
        set { withLock { seekGenerationValue = newValue } }
    }

    // MARK: - Appetite

    /// Widen the window to the full decision horizon while the spine has a run open at its scan
    /// frontier. Balanced with ``closeAppetite()``, held by count because two runs can overlap.
    func openAppetite() {
        queue.async {
            self.appetiteHolders += 1
            self.applyThrottle()
        }
    }

    func closeAppetite() {
        queue.async {
            self.appetiteHolders = max(self.appetiteHolders - 1, 0)
            self.applyThrottle()
        }
    }

    // MARK: - The transaction

    /// Open or resume the fetch if the reader is waiting on bytes nobody is fetching.
    private func ensureFetching() {
        withLock { ensureFetchingCallsValue += 1 }
        guard !invalidated else { return }
        startTicker()
        armWatchdog()
        if !hasOpened {
            startTransaction(at: position, isContinuation: false)
            return
        }
        // A retry waiting out its backoff is what will open: a reader arriving meanwhile does not
        // cut it short, or a host that keeps failing would be asked again at once, every read.
        if task == nil, !isComplete, pendingRetryWindow == nil { wantsResume = true }
        applyThrottle()
    }

    /// `window`: for a continuation picked up where a transaction left off, the ``windowGeneration``
    /// that transaction filled. A seek that resets the window in between has its own transaction
    /// queued behind this one, and this one is not opened: its offset belongs to the old window, and
    /// it would otherwise adopt the new one and fill it from there (#318). See ``openTransaction``.
    private func startTransaction(at requestedOffset: Int64, isContinuation: Bool, window: UInt64? = nil) {
        guard !invalidated else { return }
        #if DEBUG
        testBeforeTransaction?(requestedOffset)
        #endif
        // The task goes FIRST, so a seek that arrives while a discarded prefix is still streaming
        // aborts that body rather than waiting for it to finish. On a range-ignoring host the
        // prefix can be the whole episode, so this is the difference between a seek that responds
        // and a seek that waits out the download.
        task?.cancel()
        task = nil
        withLock { pacedTask = nil }
        wantsResume = false
        dropPendingRetry()
        // The watchdog's count starts at the open: a connect is allowed the same time a chunk is.
        lastProgressAt = scheduler.now()
        lastByteAt = -.infinity
        switchToDiskAtClose = false
        diskCursor = nil
        var run = runStore?.run(for: runKey)
        // A body that was approaching the run from before it, and ended (its bounded `Range`) or
        // dropped inside it before the run's first bytes could be compared, has proven nothing
        // about the run, so nothing of it may be served. What the window still holds from the
        // run's start becomes the run instead, and this continuation carries on past it — through
        // the overlap rule, against its own bytes.
        if isContinuation, case let .watchRunStart(runStart) = runWrite, let runStore, let old = run,
           requestedOffset >= runStart, requestedOffset < old.end {
            // Only the window this continuation was asked for may stand in for the run: a reset one
            // holds none of it, and would have the run removed.
            let replacement = withLock { () -> (Int64, Data)? in
                if let window, windowGeneration != window { return nil }
                let from = max(runStart, windowStart)
                return (from, Data(self.window[Int(from - windowStart)..<self.window.count]))
            }
            guard let (from, held) = replacement else { return }
            engineLog.warning("bytes: run unproven host=\(self.url.host ?? "?", privacy: .public) at=\(runStart) — replaced from \(from)")
            if held.isEmpty {
                runStore.remove(runKey)
            } else {
                runStore.replace(runKey, startingAt: from, totalLength: totalLength)
                runStore.append(runKey, held)
            }
            run = runStore.run(for: runKey)
        }
        // Bytes the run holds are not asked of the network at all: the window is filled from the
        // file, at the same pace and through the same tee as a body would be.
        if let run, run.start <= requestedOffset, requestedOffset < run.end {
            guard openTransaction(at: requestedOffset, isContinuation: isContinuation, window: window) else { return }
            diskCursor = requestedOffset
            engineLog.info("bytes: open disk key=\(self.url.host ?? "?", privacy: .public) start=\(requestedOffset) runEnd=\(run.end)")
            tee?.byteSourceDidOpenTransaction(
                startByte: requestedOffset,
                totalBytes: totalLength,
                isContinuation: openIsContinuation,
                seekGeneration: transactionSeekGeneration
            )
            // A head from disk still gets FFmpeg's look at the footer; a run that stops short of
            // it (a play that was left before the end) would otherwise pay a transaction for it.
            // S2: only an mp3 has that footer; the run's head names any other container (#822).
            if requestedOffset == 0, let total = totalLength, run.end < total,
               !Self.headRulesOutMP3(runStore?.read(runKey, at: 0, maxLength: 12) ?? Data()) {
                fetchTailIfNeeded(total: total)
            }
            pumpDisk()
            return
        }
        // A host that has already ignored one `Range` will ignore this one too: opening at 0 says
        // out loud what the body is going to be, instead of discovering it from the response and
        // re-declaring the transaction with bytes already in flight.
        let offset = hostIgnoresRange ? 0 : requestedOffset
        guard openTransaction(at: offset, isContinuation: isContinuation, window: window) else { return }

        // Where this body stands relative to the run decides what its bytes do to it. Only a body
        // that starts exactly at the run's end can extend it, and only after the overlap agrees.
        var requestStart = offset
        if runStore == nil {
            runWrite = .none
        } else if hostIgnoresRange {
            runWrite = .replaceOnArrival(at: 0)
        } else if let run {
            if offset == run.end {
                let n = min(Self.overlapBytes, Int(run.length))
                if let tail = runStore?.read(runKey, at: run.end - Int64(n), maxLength: n), tail.count == n, Self.hasEnoughEntropy(tail) {
                    runWrite = .overlap(expected: tail, matched: 0, mismatched: false, at: offset)
                    requestStart = offset - Int64(n)
                } else {
                    // The seam cannot be validated, so the body is a different stitch from what
                    // the disk just pumped: the tee must not see it as the same one carried on.
                    runWrite = .replaceOnArrival(at: offset)
                    openIsContinuation = false
                }
            } else if offset < run.start {
                runWrite = .watchRunStart(runStart: run.start)
            } else {
                runWrite = .none
            }
        } else {
            runWrite = .replaceOnArrival(at: offset)
        }

        // Bounded, never `bytes=X-`: the host is made to send only the window, so no client-side
        // reaction can arrive too late to matter. `minWindowBytes` keeps a transaction opened right
        // at the ceiling from asking for a degenerate handful of bytes.
        var endByte = max(offset + policy.minWindowBytes, readAheadCeiling()) - 1
        if let total = totalLength { endByte = min(endByte, total - 1) }
        endByte = max(endByte, offset)
        transactionEndByte = endByte

        let target = resolvedURL ?? url
        let request = makeRequest(url: target, range: "bytes=\(requestStart)-\(endByte)")
        engineLog.info(
            """
            bytes: open host=\(target.host ?? "?", privacy: .public) path=\(target.path, privacy: .private(mask: .hash)) \
            start=\(offset) end=\(endByte) \
            overlap=\(offset - requestStart) continuation=\(self.openIsContinuation) resolved=\(self.resolvedURL != nil) \
            remembered=\(self.resolutionUnproven)
            """
        )
        let task = session.dataTask(with: request)
        task.delegate = self
        self.task = task
        taskOpenedAt[ObjectIdentifier(task)] = StartupTiming.now()
        withLock {
            // A fresh task starts running: both pause reasons and the mirror start from nothing.
            pacedTask = task
            pausedForThrottle = false
            pausedForInbound = false
            taskIsSuspended = false
            if requestIssuedAtValue == nil { requestIssuedAtValue = StartupTiming.now() }
        }
        task.resume()
    }

    /// The remembered end of the chain did not answer: forget it and open the CURRENT transaction
    /// again from the original URL, which walks the chain as a first play would. Once per source —
    /// `resolutionUnproven` is cleared here, so whatever the original URL leads to gets the normal
    /// retry and failure handling. On `queue`, with no response accepted from the dead task.
    private func fallBackFromRememberedURL(reason: String) {
        engineLog.warning(
            """
            bytes: remembered url rejected host=\(self.resolvedURL?.host ?? "?", privacy: .public) \
            \(reason, privacy: .public) — reopening from \(self.url.host ?? "?", privacy: .public)
            """
        )
        resolutionUnproven = false
        resolvedURL = nil
        resolvedURLs?.invalidate(resolvedKey)
        // Whatever hops the remembered URL took before failing are not the chain the first
        // response will report.
        withLock {
            redirectHopsValue = 0
            redirectHostsValue = []
            requestIssuedAtValue = nil
        }
        let at = transactionStart
        let isContinuation = openIsContinuation
        task = nil
        startTransaction(at: at, isContinuation: isContinuation)
    }

    /// The bookkeeping every transaction shares, body or file: where it starts, which seek it is
    /// for, whether the window survives it.
    ///
    /// `window`, when given, is the ``windowGeneration`` the caller read `offset` against: the
    /// transaction is not opened, and nothing of it is recorded, if a seek has reset the window
    /// since. Checked in the same lock hold that adopts the window, so a reset lands either before
    /// it (refused here) or after it (refused by every append's own guard). Returns whether it opened.
    private func openTransaction(at offset: Int64, isContinuation: Bool, window: UInt64? = nil) -> Bool {
        let continues = isContinuation && hasOpened && !hostIgnoresRange
        let opened = withLock { () -> Bool in
            if let window, windowGeneration != window { return false }
            // A seek's bytes are a different stitch; a continuation's are the same body carried on,
            // so its window must survive or the offsets it appends at would be wrong.
            if !continues {
                resetWindowLocked(at: offset)
                streamEnded = false
            }
            transactionCountValue += 1
            transactionWindowGeneration = windowGeneration
            return true
        }
        guard opened else { return false }
        // A seek's window starts empty, and every byte of it is new. A continuation's keeps its
        // mark, even when the host's disregard of `Range` refills it from 0.
        if !isContinuation { progressMark = offset }
        transactionIsSeek = !isContinuation
        unsniffedResponse = nil
        transactionStart = offset
        transactionSeekGeneration = seekGeneration
        openIsContinuation = continues
        hasOpened = true
        return true
    }

    /// Whether the window is still the one the CURRENT transaction was opened against — see
    /// ``transactionWindowGeneration``. On `queue`.
    private func isCurrentWindow() -> Bool {
        withLock { windowGeneration == transactionWindowGeneration }
    }

    // MARK: - The run on disk

    /// Fill the window from the run file up to the ceiling. The disk's `applyThrottle`: called from
    /// the same places a body's throttle is, so the window is paced identically.
    private func pumpDisk() {
        guard var cursor = diskCursor, let runStore else { return }
        let ceiling = readAheadCeiling()
        let runEnd = runStore.run(for: runKey)?.end ?? cursor
        while cursor < runEnd, cursor < ceiling {
            let want = Int(min(Int64(Self.diskChunkBytes), runEnd - cursor))
            guard let chunk = runStore.read(runKey, at: cursor, maxLength: want), !chunk.isEmpty else { break }
            // The same guard a body's chunk gets: a seek that reset the window between two pumps
            // has its own transaction queued, and that one decides where the disk is read from.
            let appended = withLock { () -> Bool in
                guard windowGeneration == transactionWindowGeneration else { return false }
                appendLocked(chunk, fetched: false)
                condition.broadcast()
                return true
            }
            guard appended else { return }
            tee?.byteSource(didReceive: chunk, at: cursor)
            cursor += Int64(chunk.count)
        }
        diskCursor = cursor
        guard cursor >= runEnd else { return }
        // The run is used up. Past it the resource carries on over the network — as a continuation
        // at the run's end, which is the seam the overlap rule validates.
        diskCursor = nil
        tee?.byteSourceDidCloseTransaction(endedAtByte: cursor)
        // The verdict is for the window this pump filled. A seek can reset it on the caller's thread
        // after the last chunk went in (the tee call above is time enough): the ID3v1 look served
        // off disk, then the seek back. That seek's transaction is queued behind this one, and an
        // end-of-stream left on its window answers the probe's next read with EOF (#315).
        if let total = totalLength, cursor >= total {
            withLock {
                guard windowGeneration == transactionWindowGeneration else { return }
                streamEnded = true
                condition.broadcast()
            }
            return
        }
        // The same seek has its own transaction queued, and that one decides where reading resumes.
        // A cheap early out only: the seek can still land after it, and the continuation's open is
        // what refuses a reset window, under the lock (#318).
        guard isCurrentWindow() else { return }
        wantsResume = true
        applyThrottle()
    }

    /// Route one chunk of a body through the run. Returns the bytes the decoder gets: everything
    /// but a validation overlap, which was the run's own bytes asked for again.
    private func writeToRun(_ data: Data) -> Data {
        guard let runStore else { return data }
        switch runWrite {
        case .none, .watchRunStart:
            return data
        case .append:
            if !runStore.append(runKey, data) { runWrite = .none }
            return data
        case let .replaceOnArrival(at):
            runStore.replace(runKey, startingAt: at, totalLength: totalLength)
            runStore.append(runKey, data)
            runWrite = .append
            return data
        case let .overlap(expected, matched, mismatched, at):
            let take = min(expected.count - matched, data.count)
            let differs = mismatched || data.prefix(take) != expected.subdata(in: matched..<(matched + take))
            let rest = data.count > take ? data.subdata(in: take..<data.count) : Data()
            guard matched + take == expected.count else {
                runWrite = .overlap(expected: expected, matched: matched + take, mismatched: differs, at: at)
                return rest
            }
            if differs {
                engineLog.warning("bytes: seam mismatch host=\(self.url.host ?? "?", privacy: .public) at=\(at) — run replaced")
                runStore.replace(runKey, startingAt: at, totalLength: totalLength)
                // The bytes before the seam were another stitch. The tee was told this body carries
                // the disk's bytes on; it is told again, as a body that starts here, so its ring
                // holds one stitch only. Nothing of this body has been teed yet: the overlap was
                // consumed whole.
                if openIsContinuation {
                    openIsContinuation = false
                    tee?.byteSourceDidCloseTransaction(endedAtByte: at)
                    tee?.byteSourceDidOpenTransaction(
                        startByte: at,
                        totalBytes: totalLength,
                        isContinuation: false,
                        seekGeneration: transactionSeekGeneration
                    )
                }
            } else {
                engineLog.info("bytes: seam match host=\(self.url.host ?? "?", privacy: .public) at=\(at) overlap=\(expected.count)")
            }
            runWrite = .append
            if !rest.isEmpty { runStore.append(runKey, rest) }
            return rest
        }
    }

    /// A body that started before the run has reached the run's first bytes: compare them. Equal
    /// means the disk can carry on from here and the body is cancelled; anything else — a
    /// differing byte, or a window already trimmed past the bytes to compare — means the run
    /// cannot be trusted and the live body replaces it from the earliest byte still held.
    private func checkRunStart() {
        guard case let .watchRunStart(runStart) = runWrite, let runStore,
              let run = runStore.run(for: runKey) else { return }
        let n = min(Self.overlapBytes, Int(run.length))
        let (start, live) = withLock { () -> (Int64, Data?) in
            guard self.frontier >= runStart + Int64(n) else { return (self.windowStart, nil) }
            // Everything held from the run's start (or from the window's, if that is later).
            let lower = Int(max(runStart, self.windowStart) - self.windowStart)
            return (self.windowStart, Data(self.window[lower..<self.window.count]))
        }
        guard let live else { return }
        if start <= runStart, live.count >= n,
           let first = runStore.read(runKey, at: run.start, maxLength: n), first == live.prefix(n), Self.hasEnoughEntropy(first) {
            engineLog.info("bytes: run reached host=\(self.url.host ?? "?", privacy: .public) at=\(runStart) — serving from disk")
            runWrite = .none
            switchToDiskAtClose = true
            task?.cancel()
            return
        }
        let from = max(runStart, start)
        engineLog.warning("bytes: run start mismatch host=\(self.url.host ?? "?", privacy: .public) at=\(runStart) — run replaced from \(from)")
        runStore.replace(runKey, startingAt: from, totalLength: totalLength)
        runStore.append(runKey, live)
        runWrite = .append
    }

    /// The silence guard. `Set` of 4 KiB blocks: digital-silence frames repeat exactly, so an
    /// overlap that is all one block proves nothing about which stitch it came from.
    static func hasEnoughEntropy(_ overlap: Data) -> Bool {
        var blocks = Set<Data>()
        var offset = overlap.startIndex
        while offset < overlap.endIndex {
            let end = min(offset + overlapBlockBytes, overlap.endIndex)
            blocks.insert(overlap.subdata(in: offset..<end))
            if blocks.count >= minDistinctOverlapBlocks { return true }
            offset = end
        }
        return false
    }

    /// A ranged request to `url` with this source's headers. The auth headers go only to the
    /// ORIGINAL host: a private feed's `Authorization` is for the feed, and a redirect to a CDN
    /// must not carry it there.
    private func makeRequest(url target: URL, range: String) -> URLRequest {
        var request = URLRequest(url: target)
        // Matched on each hop's own origin, so a redirect to another host never carries the server's headers (#921)
        for (field, value) in serverPolicy?.headers(for: target) ?? [:] { request.setValue(value, forHTTPHeaderField: field) }
        if Self.sameHost(target, url) {
            for (field, value) in authHeaders { request.setValue(value, forHTTPHeaderField: field) }
        }
        request.setValue(range, forHTTPHeaderField: "Range")
        // Without this a host may gzip the body, and a compressed body has no usable mapping from a
        // byte offset in the response to a byte offset in the resource.
        request.setValue("identity", forHTTPHeaderField: "Accept-Encoding")
        return request
    }

    private static func sameHost(_ a: URL, _ b: URL) -> Bool {
        a.host?.lowercased() == b.host?.lowercased() && a.port == b.port && a.scheme?.lowercased() == b.scheme?.lowercased()
    }

    // MARK: - Throttle

    /// The absolute byte this transaction may read up to, measured from the DECODER's read
    /// position — never from the frontier, which would chase itself.
    ///
    /// The loader took `max(playhead, transactionStart)` because `AVPlayer`'s clock could report
    /// from behind a transaction AVFoundation had already opened ahead of it. There is no such
    /// second opinion here: ``position`` is the read position, it can never be ahead of what has
    /// been fetched, and taking the max with the transaction start would ratchet the window forward
    /// by a whole window on every continuation — 128 KiB fetched for a 64 KiB bound, which is
    /// exactly what the read-ahead test caught.
    private func readAheadCeiling() -> Int64 {
        policy.ceiling(
            baseByte: withLock { readingTail ? windowStart : positionValue },
            bytesPerSecond: bytesPerSecond(),
            appetiteOpen: appetiteHolders > 0
        )
    }

    private func bytesPerSecond() -> Int64 {
        withLock {
            guard let duration = durationHintValue, duration > 0, let total = totalLengthValue else { return 0 }
            return Int64(Double(total) / duration)
        }
    }

    private func applyThrottle() {
        guard !invalidated else { return }
        let ceiling = readAheadCeiling()
        let frontier = withLock { self.frontier }

        if diskCursor != nil {
            pumpDisk()
            return
        }
        if task == nil {
            // A body that ended short is picked up here, at the frontier, and only once the ceiling
            // has room for it to make progress. Reopening while still at the ceiling would only
            // idle out again — the once-a-minute reopen loop the device gate measured.
            //
            // Only for the window the last transaction filled: a seek that reset it — on the
            // caller's thread, at any point up to the open — has its own transaction queued behind
            // this one, and a continuation opened here would fill the seek's window from this
            // frontier. The open refuses it under the lock, so the lock is never held across this
            // call, which re-enters the throttle and the pump (#318).
            if wantsResume, !isComplete, frontier < ceiling {
                startTransaction(at: frontier, isContinuation: true, window: transactionWindowGeneration)
            }
            return
        }

        let shouldPause = frontier >= ceiling
        withLock {
            pausedForThrottle = shouldPause
            syncPacingLocked()
        }
    }

    /// Apply the two pause reasons to ``pacedTask``: suspended while either holds, running while
    /// neither does. The only caller of the task's `suspend()`/`resume()`. Behind `condition`.
    private func syncPacingLocked() {
        guard let pacedTask else { return }
        let wanted = pausedForThrottle || pausedForInbound
        guard wanted != taskIsSuspended else { return }
        taskIsSuspended = wanted
        if wanted { pacedTask.suspend() } else { pacedTask.resume() }
    }

    /// One chunk has been handed over by the session and is about to be enqueued for `queue`:
    /// count it, and if the backlog has crossed the high-water mark hold the body here, on the
    /// delegate queue, because `queue` is by definition not getting a turn. On the delegate queue.
    /// Tail chunks are excluded (see ``tailTaskMirror``) and never reach this budget at all.
    private func inboundDidEnqueue(_ count: Int, for dataTask: URLSessionDataTask) {
        withLock {
            guard dataTask !== tailTaskMirror else { tailBytesEnqueued += count; return }
            inFlightBytes += count
            peakInFlightBytes = max(peakInFlightBytes, inFlightBytes)
            guard !pausedForInbound, inFlightBytes >= Self.inFlightHighWaterBytes, dataTask === pacedTask else { return }
            pausedForInbound = true
            inboundPauseCount += 1
            syncPacingLocked()
        }
    }

    /// The chunk's turn on `queue` has come: it is no longer in flight, and once the backlog has
    /// drained past the low-water mark the body may run again. On `queue`, so `tailTask` itself
    /// (not the mirror) is safe to read here — the mirror exists only for the delegate queue above.
    private func inboundDidDequeue(_ count: Int, for dataTask: URLSessionDataTask) {
        guard dataTask !== tailTask else { return }
        withLock {
            inFlightBytes -= count
            guard pausedForInbound, inFlightBytes <= Self.inFlightLowWaterBytes else { return }
            pausedForInbound = false
            syncPacingLocked()
        }
    }

    private var isComplete: Bool {
        withLock {
            guard let total = totalLengthValue else { return false }
            return frontier >= total
        }
    }

    /// Re-ask the ceiling on a timer: a suspended body delivers no callbacks, so nothing else would
    /// notice that the decoder has read far enough for the window to move.
    private func startTicker() {
        guard ticker == nil else { return }
        let timer = DispatchSource.makeTimerSource(queue: queue)
        timer.schedule(deadline: .now() + Self.throttleTickSeconds, repeating: Self.throttleTickSeconds)
        timer.setEventHandler { [weak self] in
            guard let self, !self.invalidated else { return }
            self.applyThrottle()
        }
        ticker = timer
        timer.resume()
    }

    // MARK: - Stall watchdog

    /// Start the watchdog's look every ``watchdogTickSeconds``, once, for the life of the source.
    private func armWatchdog() {
        guard !watchdogArmed else { return }
        watchdogArmed = true
        lastProgressAt = scheduler.now()
        scheduleWatchdog()
    }

    private func scheduleWatchdog() {
        scheduler.schedule(after: Self.watchdogTickSeconds, on: queue) { [weak self] in
            guard let self, !self.invalidated else { return }
            self.checkForStall()
            self.scheduleWatchdog()
        }
    }

    /// A body that is open, running and below the ceiling — one the window is waiting on — and has
    /// brought nothing for ``stallTimeoutSeconds`` is dropped and asked for again at the frontier
    /// (#896). Time it spent with no reason to bring anything (held by the throttle or the in-flight
    /// budget, nothing open, the disk pumping) is not counted. On `queue`.
    private func checkForStall() {
        let now = scheduler.now()
        let (frontier, suspended) = withLock { (self.frontier, taskIsSuspended) }
        guard task != nil, diskCursor == nil, !suspended, frontier < readAheadCeiling() else {
            lastProgressAt = now
            return
        }
        let idle = now - lastProgressAt
        guard idle >= Self.stallTimeoutSeconds else { return }
        engineLog.warning(
            "bytes: stall host=\(self.url.host ?? "?", privacy: .public) at=\(frontier) idle=\(String(format: "%.1f", idle), privacy: .public)s"
        )
        // A stall is a failure that never errors: it spends the same budget, on the same backoff,
        // so a host that accepts and never answers fails the read as a refused one does.
        dropTask(endedAt: frontier)
        retryOrFail(at: frontier, reason: "stall", failure: "no bytes for \(Int(Self.stallTimeoutSeconds)) s")
    }

    // MARK: - The tail

    /// `offset` is inside the tail this source holds, or inside the tail its side request is
    /// fetching. Behind `condition`.
    private func tailHoldsLocked(_ offset: Int64) -> Bool {
        if let tail { return offset >= tailStart && offset < tailStart + Int64(tail.count) }
        guard tailPending, let total = totalLengthValue else { return false }
        return offset >= total - Int64(Self.tailBytes) && offset < total
    }

    /// S2: whether a stream's first bytes name a container other than mp3 (FLAC, Ogg, WAV, MP4, WavPack, APE), which
    /// FFmpeg never looks for an ID3v1 footer in. An ID3v2 tag or a bare frame header might be mp3, as might a head
    /// too short to tell.
    static func headRulesOutMP3(_ head: Data) -> Bool {
        let bytes = [UInt8](head.prefix(12))
        func magic(_ text: String, at offset: Int = 0) -> Bool {
            let expected = [UInt8](text.utf8)
            return bytes.count >= offset + expected.count && Array(bytes[offset..<offset + expected.count]) == expected
        }
        return magic("fLaC") || magic("OggS") || magic("RIFF") || magic("ftyp", at: 4) || magic("wvpk") || magic("MAC ")
    }

    /// S2: whether a response's type names audio other than mp3 (`audio/flac`, `audio/mp4`, `application/ogg`). An
    /// untyped or generic body (`application/octet-stream`) might be mp3.
    static func typeRulesOutMP3(_ mimeType: String?) -> Bool {
        guard let mimeType = mimeType?.lowercased() else { return false }
        guard mimeType.hasPrefix("audio/") || mimeType == "application/ogg" else { return false }
        return !["audio/mpeg", "audio/mp3", "audio/mpeg3", "audio/x-mpeg", "audio/x-mp3", "audio/x-mpeg-3"].contains(mimeType)
    }

    /// One bounded request for the resource's last ``tailBytes``, beside the transaction that is
    /// open. Only when nothing holds them yet and the open body will not reach them. On `queue`.
    private func fetchTailIfNeeded(total: Int64) {
        guard !invalidated, tailTask == nil, !hostIgnoresRange, total > Int64(Self.tailBytes) else { return }
        let needed = withLock { () -> Bool in
            guard tail == nil, !tailPending else { return false }
            tailPending = true
            if tailStatusValue == .none { tailStatusValue = .pending }
            return true
        }
        guard needed else { return }
        let start = total - Int64(Self.tailBytes)
        let target = resolvedURL ?? url
        engineLog.info("bytes: open tail host=\(target.host ?? "?", privacy: .public) start=\(start) total=\(total)")
        tailBuffer.removeAll()
        let task = session.dataTask(with: makeRequest(url: target, range: "bytes=\(start)-\(total - 1)"))
        task.delegate = self
        tailTask = task
        withLock { tailTaskMirror = task }
        task.resume()
    }

    /// The side request answered, well or badly. On `queue`. Nothing is waiting on it: a reader
    /// parked inside the tail has already been given end-of-stream by ``read(into:maxLength:)``,
    /// so a failure only clears the flag — the next seek into the tail's range, if there ever is
    /// one, takes the ordinary route, a transaction at its position.
    private func finishTailFetch(total: Int64?, reason: String) {
        tailTask = nil
        let bytes = tailBuffer
        tailBuffer = Data()
        let complete = total != nil && bytes.count == Self.tailBytes
        withLock {
            tailTaskMirror = nil
            tailPending = false
            if complete, let total {
                tail = bytes
                tailStart = total - Int64(bytes.count)
                if tailStatusValue != .late { tailStatusValue = .fetched }
            } else if tailStatusValue != .late {
                tailStatusValue = .dropped
            }
            condition.broadcast()
        }
        if complete, let total {
            engineLog.info("bytes: tail held total=\(total)")
            runStore?.setTail(runKey, bytes, totalLength: total)
        } else {
            engineLog.warning("bytes: tail dropped \(reason, privacy: .public)")
        }
    }

    /// A body that happens to start exactly at the tail (the ordinary route, when nothing held it)
    /// is kept as the tail once the window has all of it. On `queue`.
    private func keepTailFromWindowIfComplete() {
        guard let total = totalLength, transactionStart == total - Int64(Self.tailBytes) else { return }
        let kept: Data? = withLock {
            guard tail == nil, windowStart <= transactionStart, frontier >= total else { return nil }
            let from = Int(transactionStart - windowStart)
            let bytes = Data(window[from..<(from + Self.tailBytes)])
            tail = bytes
            tailStart = transactionStart
            if tailStatusValue == .none { tailStatusValue = .fetched }
            return bytes
        }
        if let kept { runStore?.setTail(runKey, kept, totalLength: total) }
    }

    // MARK: - Window

    private func withLock<T>(_ body: () -> T) -> T {
        condition.lock()
        defer { condition.unlock() }
        return body()
    }

    private func hasBufferLocked() -> Bool { hasOpenedWindow }
    private var hasOpenedWindow: Bool { frontier > windowStart || !window.isEmpty }

    private func resetWindowLocked(at offset: Int64) {
        window.removeAll(keepingCapacity: true)
        windowStart = offset
        frontier = offset
        windowGeneration &+= 1
    }

    private func appendLocked(_ data: Data, fetched: Bool = true) {
        window.append(contentsOf: data)
        frontier += Int64(data.count)
        if fetched { bytesFetchedValue += Int64(data.count) }
        trimLocked()
    }

    /// Release what nothing can come back for, keeping ``ReadAheadPolicy/backWindowBytes`` behind the
    /// read position so a short backwards seek costs no transaction. Copying, so it is only done
    /// once a worthwhile prefix has accumulated.
    private func trimLocked() {
        guard !readingTail else { return }
        let floor = min(max(windowStart, positionValue - policy.backWindowBytes), frontier)
        let drop = floor - windowStart
        guard drop >= Self.trimSlackBytes else { return }
        window.removeFirst(Int(drop))
        windowStart = floor
    }

    private func isEndOfStreamLocked() -> Bool {
        if let total = totalLengthValue, positionValue >= total { return true }
        return streamEnded && positionValue >= frontier
    }

    private func fail(_ message: String) {
        engineLog.error("bytes: fail host=\(self.url.host ?? "?", privacy: .public) reason=\(message, privacy: .public)")
        condition.lock()
        failureMessage = message
        condition.broadcast()
        condition.unlock()
    }
}

// MARK: - URLSessionDataDelegate

/// Every callback arrives on the shared session's delegate queue and hops onto `queue` with
/// `async` before it reads or writes anything: `queue` stays the sole owner of transaction
/// state, and the delegate queue — shared with every other source in the process — is never
/// held for this source's work (see the type's threading note). The two things the old `sync`
/// hop provided are provided otherwise: the disposition of a response is answered from `queue`,
/// and the session delivers no body byte until it is, so a held `didReceive response` still
/// holds the body (#226); and a body that outruns `queue` is suspended from the delegate queue
/// by the in-flight budget, so a `task.suspend()` the throttle decides on chunk N landing after
/// chunk N+1 was enqueued costs at most that budget, never the episode. Nothing here touches
/// state outside `condition` before the hop except ``inboundDidEnqueue(_:for:)``, which is
/// entirely behind it.
extension HTTPRangeByteSource: URLSessionDataDelegate {

    /// Follow the redirect ourselves, so the request that reaches the next hop still carries the
    /// `Range` and the `Accept-Encoding` (CFNetwork drops custom headers on a cross-host redirect,
    /// and without the range the CDN would answer with the whole episode), and so the hop's
    /// destination can be remembered — the whole point, see the type's note.
    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void
    ) {
        queue.async { [self] in
            guard !invalidated, task === self.task || task === tailTask, let next = newRequest.url else {
                completionHandler(nil)
                return
            }
            let range = task.originalRequest?.value(forHTTPHeaderField: "Range") ?? "bytes=0-"
            if task === tailTask {
                // The side request follows its own hops with the same headers, and says nothing about
                // the chain the first response reports.
                completionHandler(makeRequest(url: next, range: range))
                return
            }
            engineLog.info(
                """
                bytes: redirect status=\(response.statusCode, privacy: .public) \
                from=\(response.url?.host ?? "?", privacy: .public) to=\(next.host ?? "?", privacy: .public)
                """
            )
            // Every hop overwrites, so what is kept is the chain's end.
            resolvedURL = next
            withLock {
                redirectHopsValue += 1
                redirectHostsValue.append(next.host ?? "?")
            }
            completionHandler(makeRequest(url: next, range: range))
        }
    }

    /// A server whose certificate the system refuses is still played when it's the one the user trusted for it (#921).
    /// Answered here rather than on the session, which has no delegate: see ``session``.
    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        ServerConnections.handle(challenge, policy: serverPolicy, completion: completionHandler)
    }

    func urlSession(
        _ session: URLSession,
        dataTask: URLSessionDataTask,
        didReceive response: URLResponse,
        completionHandler: @escaping (URLSession.ResponseDisposition) -> Void
    ) {
        queue.async { [self] in
            if !invalidated, dataTask === tailTask {
                // Only a `206` whose `Content-Range` starts where the tail was asked for, out of the
                // same total the head reported, is the tail; a host that ignored the range would hand
                // over the whole file instead, and a re-stitched file of a different length would
                // hand over another file's footer.
                guard let http = response as? HTTPURLResponse, http.statusCode == 206,
                      let total = totalLength,
                      let contentRange = http.value(forHTTPHeaderField: "Content-Range"),
                      Self.contentRangeStart(contentRange) == total - Int64(Self.tailBytes),
                      Self.contentRangeTotal(contentRange) ?? total == total else {
                    completionHandler(.cancel)
                    finishTailFetch(total: nil, reason: "status=\((response as? HTTPURLResponse)?.statusCode ?? 0)")
                    return
                }
                completionHandler(.allow)
                return
            }
            // A response for the window a seek has since reset belongs to nobody: cancelled, and the
            // seek's queued `startTransaction` opens the window it reset. See
            // ``transactionWindowGeneration``.
            guard !invalidated, dataTask === task, isCurrentWindow() else {
                completionHandler(.cancel)
                return
            }
            lastProgressAt = scheduler.now()
            lastByteAt = lastProgressAt
            let http = response as? HTTPURLResponse
            if let http {
                if resolutionUnproven, !(200..<300).contains(http.statusCode) {
                    // The remembered end of the chain has stopped answering (a signed CDN URL past its
                    // expiry says 403; a moved file says 404). This response is nobody's first
                    // response: the transaction is opened again from the original URL, chain and all.
                    completionHandler(.cancel)
                    fallBackFromRememberedURL(reason: "status=\(http.statusCode)")
                    return
                }
                if resolutionUnproven, http.statusCode == 200 {
                    // The signed URL past its expiry that answers a page instead of a refusal: `200`
                    // with an HTML body. Read as a host ignoring the range, the page's bytes went to
                    // the decoder as the episode's first (#226). The headers settle it when they name
                    // a page; when they name nothing playable either, the first chunk settles it.
                    if Self.isPage(mimeType: http.mimeType) {
                        completionHandler(.cancel)
                        fallBackFromRememberedURL(reason: "status=200 content-type=\(http.mimeType ?? "?")")
                        return
                    }
                    if !Self.isMedia(mimeType: http.mimeType) {
                        unsniffedResponse = (dataTask, http, response, Data())
                        completionHandler(.allow)
                        return
                    }
                }
            }
            acceptResponse(http, response, from: dataTask)
            completionHandler(.allow)
        }
    }

    /// The response is this transaction's: stamped, learned from, announced. Everything a `2xx`
    /// from the right task gets, once nothing about it is still in doubt.
    private func acceptResponse(_ http: HTTPURLResponse?, _ response: URLResponse, from dataTask: URLSessionDataTask) {
        var learned: Int64?
        if let http {
            let remembered = resolutionUnproven
            resolutionUnproven = false
            // Stamped before anything else is decided about the response: the timing wants the
            // moment the first bytes' headers arrived, not the moment they were understood.
            withLock {
                guard firstResponseValue == nil else { return }
                firstResponseAtValue = StartupTiming.now()
                firstResponseValue = StartupTiming.FirstResponse(
                    status: http.statusCode,
                    redirects: redirectHopsValue,
                    hosts: redirectHostsValue,
                    remembered: remembered
                )
                firstResponseTaskIdentifier = ObjectIdentifier(dataTask)
            }
            // A `2xx` proves where the chain ends, whether it was walked this play or remembered
            // from the last one (and re-stamped, so a URL that keeps working keeps its trust).
            // Once per source: later transactions reuse the same end.
            if let resolvedURL, !resolutionRecorded {
                resolutionRecorded = true
                resolvedURLs?.record(original: resolvedKey, resolved: resolvedURL)
            }
            if let contentRange = http.value(forHTTPHeaderField: "Content-Range"),
               let total = contentRange.split(separator: "/").last.flatMap({ Int64($0) }) {
                learned = total
            } else if http.statusCode == 200, response.expectedContentLength > 0 {
                learned = response.expectedContentLength
            }
            if http.statusCode == 200, transactionStart > 0 {
                // The host ignored the `Range` and started from zero. Writing byte 0 of this body at
                // the offset we asked for would corrupt every offset downstream, so the transaction
                // is re-declared as what it actually is: a whole-body stream from 0. The read
                // position stays where the caller put it and the prefix is discarded as it arrives.
                //
                // Kept, because it is what the AVPlayer loader does and what a listener on such a
                // host gets instead of nothing — but logged ONCE. It is a property of the host, not
                // of this transaction, and a per-body line on a CDN that never honours a range is
                // one warning per continuation for a whole episode.
                if !hostIgnoresRange {
                    engineLog.warning(
                        "bytes: range_ignored host=\(self.url.host ?? "?", privacy: .public) asked=\(self.transactionStart) — whole body will be fetched and trimmed"
                    )
                }
                hostIgnoresRange = true
                transactionStart = 0
                // A seek's window is refilled from 0, all of it new; a continuation's prefix is
                // what it already had, and is no progress (see ``progressMark``).
                if transactionIsSeek { progressMark = 0 }
                openIsContinuation = false
                // A body from 0 is a whole new stitch: whatever the run held is dropped for it.
                if runStore != nil { runWrite = .replaceOnArrival(at: 0) }
                withLock {
                    resetWindowLocked(at: 0)
                    streamEnded = false
                    transactionWindowGeneration = windowGeneration
                }
            }
        }
        if let learned {
            runStore?.setTotalLength(runKey, learned)
            let isNew = withLock { () -> Bool in
                guard totalLengthValue != learned else { return false }
                totalLengthValue = learned
                condition.broadcast()
                return true
            }
            if isNew { tee?.byteSource(didLearnTotalBytes: learned) }
        }
        tee?.byteSourceDidOpenTransaction(
            startByte: transactionStart,
            totalBytes: learned ?? totalLength,
            isContinuation: openIsContinuation,
            seekGeneration: transactionSeekGeneration
        )
        // The open: the footer look is coming, so its bytes are asked for now, beside this body,
        // unless this body reaches them anyway.
        // S2: not when the body is typed as something other than mp3, which has no footer (#822).
        if transactionStart == 0, !openIsContinuation, !hostIgnoresRange,
           let total = learned ?? totalLength, transactionEndByte < total - 1,
           !Self.typeRulesOutMP3(response.mimeType) {
            fetchTailIfNeeded(total: total)
        }
    }

    /// The first byte of `bytes A-B/T`, or nil when the header is not of that shape.
    /// The `Z` of `bytes X-Y/Z`, or nil when the host reports `*` or omits it.
    private static func contentRangeTotal(_ header: String) -> Int64? {
        header.split(separator: "/").last.flatMap { Int64($0.trimmingCharacters(in: .whitespaces)) }
    }

    private static func contentRangeStart(_ header: String) -> Int64? {
        let trimmed = header.trimmingCharacters(in: .whitespaces)
        guard trimmed.hasPrefix("bytes ") else { return nil }
        let spec = trimmed.dropFirst("bytes ".count)
        return spec.split(separator: "-").first.flatMap { Int64($0) }
    }

    /// A page, by its own account: what a signed URL past its expiry answers with a `200`.
    private static func isPage(mimeType: String?) -> Bool {
        guard let mimeType = mimeType?.lowercased() else { return false }
        return mimeType == "text/html" || mimeType == "application/xhtml+xml"
    }

    /// A type that names something the decoder could be handed. `application/octet-stream` and
    /// a missing type name nothing, so a body under them is judged by its bytes.
    private static func isMedia(mimeType: String?) -> Bool {
        guard let mimeType = mimeType?.lowercased() else { return false }
        return mimeType.hasPrefix("audio/") || mimeType.hasPrefix("video/") || mimeType == "application/ogg"
    }

    /// Whether the first bytes of a body from offset 0 start like anything the decoder plays:
    /// an mp3 frame sync or ID3 tag, or the magic of a container it handles. A page never does.
    static func looksLikeMedia(_ data: Data) -> Bool {
        guard data.count >= sniffMinBytes else { return false }
        let bytes = [UInt8](data.prefix(12))
        if bytes[0] == 0xFF, bytes[1] & 0xE0 == 0xE0 { return true }   // mp3 or ADTS frame sync
        let magic = String(decoding: bytes[0..<4], as: UTF8.self)
        if magic.hasPrefix("ID3") || magic == "OggS" || magic == "fLaC" || magic == "RIFF" { return true }
        return String(decoding: bytes[4..<8], as: UTF8.self) == "ftyp"  // mp4 / m4a
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        // Counted before the hop, on the delegate queue, so the budget sees the chunk the moment
        // the session has let go of it; released at the top of its turn on `queue`, whichever
        // way that turn ends.
        inboundDidEnqueue(data.count, for: dataTask)
        queue.async { [self] in
            defer { inboundDidDequeue(data.count, for: dataTask) }
            if !invalidated, dataTask === tailTask {
                tailBuffer.append(data)
                return
            }
            guard !invalidated, dataTask === task, !data.isEmpty else { return }
            #if DEBUG
            if let hold = testBeforeNextChunk {
                testBeforeNextChunk = nil
                hold()
            }
            #endif
            // **This transaction's window, not whichever window there is now.** `task` is still this
            // body's task until the seek's `startTransaction` runs, and that is queued BEHIND this
            // chunk: identity alone let a superseded body's bytes land at the new window's frontier,
            // at an offset they were never at, and reach the tee labelled with it (#224). Checked
            // again under the lock, because the seek can land between here and the append.
            guard isCurrentWindow() else { return }
            lastProgressAt = scheduler.now()
            lastByteAt = lastProgressAt
            var incoming = data
            if let held = unsniffedResponse, held.task === dataTask {
                var buffer = held.buffer
                buffer.append(incoming)
                // A chunk boundary is not a media boundary: the sniff needs enough bytes together,
                // not just enough in this one delivery. Held until there are, or the transaction
                // closes (below, and in `didCompleteWithError`).
                guard buffer.count >= Self.sniffMinBytes else {
                    unsniffedResponse = (held.task, held.http, held.response, buffer)
                    return
                }
                unsniffedResponse = nil
                guard Self.looksLikeMedia(buffer) else {
                    dataTask.cancel()
                    fallBackFromRememberedURL(reason: "status=200 content-type=\(held.http.mimeType ?? "?") body=not-audio")
                    return
                }
                acceptResponse(held.http, held.response, from: dataTask)
                // Every buffered byte, not just this chunk: the sniff held them all back from the run,
                // the window and the tee, and none of them may be lost now that it has passed.
                incoming = buffer
            }
            // The run first: an overlap is consumed here and never reaches the window or the tee.
            let data = writeToRun(incoming)
            // A chunk swallowed whole by the overlap still counts as arrival: the run-start check and
            // the throttle run for it as for any other.
            if !data.isEmpty {
                let appended = withLock { () -> (at: Int64, to: Int64)? in
                    guard windowGeneration == transactionWindowGeneration else { return nil }
                    let at = frontier
                    appendLocked(data)
                    condition.broadcast()
                    return (at, frontier)
                }
                guard let appended else { return }
                if appended.to > progressMark {
                    progressMark = appended.to
                    retryAttempts = 0
                }
                // The tee. The decoder gets these bytes out of the window; the scanner gets the same
                // ones here, with the offset they start at. There is no other byte path.
                tee?.byteSource(didReceive: data, at: appended.at)
            }
            keepTailFromWindowIfComplete()
            checkRunStart()
            // Checked on every chunk, not only on the ticker: a fast host hands over chunk after chunk
            // with this queue never idle between them, and a throttle that only ran on a timer would
            // not get a turn until the whole burst had streamed through.
            applyThrottle()
        }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        queue.async { [self] in
            let (endedAt, capturedGeneration) = withLock { (frontier, windowGeneration) }
            // Every close, including the ones a newer transaction cancelled, so a device log can
            // pair each `open` with how it ended: the 2026-09-15 log's first four transactions each
            // died a `-999` a few hundred ms after their headers, and that was only visible by hand.
            // What it carried, over how long: a slow link reads as low throughput before it stalls (#897).
            let received = task.countOfBytesReceived
            let seconds = taskOpenedAt.removeValue(forKey: ObjectIdentifier(task)).map { StartupTiming.now() - $0 }
            let ms = seconds.map { String(Int(($0 * 1000).rounded())) } ?? "-"
            let kbps = seconds.flatMap { $0 > 0 ? String(Int(Double(received) * 8 / 1000 / $0)) : nil } ?? "-"
            engineLog.info(
                """
                bytes: close host=\(task.currentRequest?.url?.host ?? "?", privacy: .public) \
                path=\(task.currentRequest?.url?.path ?? "-", privacy: .private(mask: .hash)) \
                start=\(task.originalRequest?.value(forHTTPHeaderField: "Range") ?? "-", privacy: .public) \
                at=\(endedAt) current=\(task === self.task) bytes=\(received) ms=\(ms, privacy: .public) \
                kbps=\(kbps, privacy: .public) error=\((error as NSError?)?.code ?? 0, privacy: .public)
                """
            )
            if !invalidated, task === tailTask {
                finishTailFetch(total: error == nil ? totalLength : nil, reason: "error=\((error as NSError?)?.code ?? 0)")
                return
            }
            guard task === self.task else { return }
            self.task = nil
            withLock { pacedTask = nil }
            tee?.byteSourceDidCloseTransaction(endedAtByte: endedAt)
            guard !invalidated else { return }
            // The close of a body whose window a seek has reset: its retry, its continuation and its
            // end-of-stream verdict were all for that window, and the seek's own `startTransaction`
            // is queued behind this closure to open the new one. See ``transactionWindowGeneration``.
            guard capturedGeneration == transactionWindowGeneration else { return }
            if let held = unsniffedResponse, held.task === task {
                // The held response closed — end of body, or an error — before its sniff reached
                // ``sniffMinBytes`` or the cap: too short to be the audio it would need to be.
                unsniffedResponse = nil
                fallBackFromRememberedURL(reason: "status=200 content-type=\(held.http.mimeType ?? "?") body=too-short(\(held.buffer.count))")
                return
            }
            if switchToDiskAtClose {
                // The body met the run and agreed with it: the rest is on disk.
                startTransaction(at: endedAt, isContinuation: true, window: capturedGeneration)
                return
            }

            if let error {
                let nsError = error as NSError
                guard nsError.code != NSURLErrorCancelled else { return }
                if resolutionUnproven {
                    // Not a retry: the remembered host may be gone for good, and the original URL is
                    // the one that is known to lead somewhere.
                    fallBackFromRememberedURL(reason: "error=\(nsError.code)")
                    return
                }
                retryOrFail(at: endedAt, reason: "error=\(nsError.code)", failure: error.localizedDescription)
                return
            }

            // A bounded `Range` that ran out, or a host that dropped an idle connection: the stream is
            // not over, it is picked up at the frontier once the ceiling has room. A seek landing after
            // the guard above is refused at the continuation's open, as the throttle asks it (#318).
            if let total = totalLength, endedAt < total {
                wantsResume = true
                applyThrottle()
                return
            }
            withLock {
                // A seek can reset the window on the caller's thread while this completion is already
                // in flight on `queue`: the identity guard above only rules out a *replaced* task, not
                // one whose window was reset out from under it before this closure landed. Comparing
                // `frontier` to `endedAt` is not enough: two seeks queued ahead of this transaction's
                // own reopening — the second landing back on exactly `endedAt` and outside the window
                // the first one just opened — reset the window twice and leave `frontier == endedAt`
                // true by coincidence. `capturedGeneration` is this transaction's own window's identity,
                // bumped on every reset regardless of where it lands, so it survives that coincidence.
                guard windowGeneration == capturedGeneration else { return }
                if totalLengthValue == nil { totalLengthValue = frontier }
                streamEnded = true
                condition.broadcast()
            }
        }
    }

    /// Split the first transaction's timing into dns/connect/tls/server so a slow
    /// ``StartupTiming/firstResponseMs`` can be blamed on a stage (#193).
    ///
    /// Fires only once the task itself finishes, which for a bounded read-ahead range is well
    /// after the node has already rendered — the `ttfa` line has gone out by then, so this is
    /// logged on its own line rather than folded into a record that no longer exists. Guarded to
    /// the task that produced ``firstResponse`` (identified, not compared by reference, because a
    /// remembered-URL retry means the task that opened the transaction is not always ``task``
    /// by the time this arrives) and to firing once. NOT gated on `invalidated`: the task that
    /// produced the first response usually ends by being cancelled — by the next play's
    /// ``cancel()``, whose closure invalidates before it cancels — and that is exactly the
    /// task whose split the line is for. Nothing here touches transaction state.
    func urlSession(_ session: URLSession, task: URLSessionTask, didFinishCollecting metrics: URLSessionTaskMetrics) {
        queue.async { [self] in
            guard let transaction = metrics.transactionMetrics.last else { return }
            let net = StartupTiming.NetMetrics(
                dnsMs: Self.metricsMs(transaction.domainLookupStartDate, transaction.domainLookupEndDate),
                connectMs: Self.metricsMs(transaction.connectStartDate, transaction.connectEndDate),
                tlsMs: Self.metricsMs(transaction.secureConnectionStartDate, transaction.secureConnectionEndDate),
                serverMs: Self.metricsMs(transaction.requestStartDate, transaction.responseStartDate),
                reusedConnection: transaction.isReusedConnection,
                networkProtocol: transaction.networkProtocolName ?? "-",
                queuedMs: Self.metricsMs(metrics.taskInterval.start, transaction.fetchStartDate)
            )
            let identifier = ObjectIdentifier(task)
            let toLog: (key: Int, status: Int)? = withLock {
                guard netMetricsValue == nil, firstResponseTaskIdentifier == identifier,
                      let firstResponseAtValue, let status = firstResponseValue?.status else { return nil }
                netMetricsValue = net
                return (Int((firstResponseAtValue * 1000).rounded()), status)
            }
            guard let toLog else { return }
            engineLog.info(
                "\(StartupTiming.netLogLine(key: toLog.key, host: transaction.request.url?.host ?? self.url.host ?? "?", status: toLog.status, metrics: net), privacy: .public)"
            )
        }
    }

    private static func metricsMs(_ from: Date?, _ to: Date?) -> Int? {
        guard let from, let to else { return nil }
        return Int((to.timeIntervalSince(from) * 1000).rounded())
    }
}

