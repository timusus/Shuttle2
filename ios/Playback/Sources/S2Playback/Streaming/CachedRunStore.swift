// Copied from Shuttle Podcasts (podcasts@9ee6e0954) mobile/ios/Playback/Sources/Playback/Streaming/CachedRunStore.swift — see ios/Playback/README.md.
import CryptoKit
import Foundation
import OSLog

private let engineLog = Logger(subsystem: "com.simplecityapps.shuttle2", category: "network")

/// **One contiguous run of streamed bytes per episode, on disk.**
///
/// Plan: `docs/plans/2026-09-16-streaming-playback-cache.md`. The bytes ``HTTPRangeByteSource``
/// fetched for the listener are kept so a re-listen, a cold-start resume and a back-seek read from
/// here instead of the network, and so the ad-skip scanner can read a finished episode whole.
///
/// Deliberately ONE run per key: a data file holding `[start, start + length)` of the resource and
/// a sidecar saying where it starts. No sparse index, no database. The run only ever grows at its
/// end, across a seam the byte source has validated against the host's own bytes (the overlap rule
/// in ``HTTPRangeByteSource``), or is replaced outright. Hosts re-stitch ads behind a stable URL,
/// so a run is never trusted on its headers — only on its bytes.
///
/// Thread-safe by one lock; every call is short file I/O. The directory sits under Application
/// Support, excluded from iCloud backup: it is a cache, and the real downloads live elsewhere.
public final class CachedRunStore {

    /// Least-recently-touched eviction to this many bytes, run by ``evict(toBudget:)`` from the
    /// auto-download pass. A constant, not a setting.
    public static let budgetBytes: Int64 = 512 * 1024 * 1024

    /// What the sidecar records. `length` is the data file's size, so an append never rewrites it.
    public struct Run: Equatable {
        public let start: Int64
        public let length: Int64
        /// The resource's total size once a response has said it; nil until then.
        public let totalLength: Int64?
        public var end: Int64 { start + length }
        /// The whole resource, from byte 0 to its last: what the scanner may read as a file.
        public var isComplete: Bool { start == 0 && totalLength.map { length >= $0 } ?? false }
    }

    private struct Sidecar: Codable {
        var start: Int64
        var totalLength: Int64?
        var touched: TimeInterval
        /// The resource's last bytes (``HTTPRangeByteSource`` keeps the ID3v1 footer FFmpeg reads
        /// on every mp3 open), valid only for `totalLength`. Optional, so older sidecars decode.
        var tail: Data?
    }

    /// Where the app's runs (and the resolved-URL map beside them) live: `Caches`, which the OS may
    /// purge and iCloud never backs up. Everything in it is re-fetchable.
    static let sharedDirectory: URL = FileManager.default
        .urls(for: .cachesDirectory, in: .userDomainMask).first!
        .appendingPathComponent("streamed-runs", isDirectory: true)

    /// Before the cap and `Caches`, runs went under Application Support, which is backed up and
    /// never purged, and nothing evicted them.
    static var legacyDirectory: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
            .appendingPathComponent("streamed-runs", isDirectory: true)
    }

    public static let shared: CachedRunStore = {
        let store = CachedRunStore(directory: sharedDirectory)
        // Off the caller's thread: the first play must not wait on a directory walk or a delete.
        defaultEvictionQueue { removeLegacyDirectory(legacyDirectory) }
        store.scheduleStartupEviction()
        return store
    }()

    /// Where eviction passes run by default: a utility-priority global queue.
    public static let defaultEvictionQueue: (@escaping () -> Void) -> Void = { work in
        DispatchQueue.global(qos: .utility).async(execute: work)
    }

    /// How often a use is written back to the sidecar; in between it is only remembered in memory.
    static let touchPersistInterval: TimeInterval = 60

    /// Delete the pre-`Caches` directory, once: a no-op when it is already gone.
    static func removeLegacyDirectory(_ legacy: URL) {
        guard FileManager.default.fileExists(atPath: legacy.path) else { return }
        try? FileManager.default.removeItem(at: legacy)
    }

    private let directory: URL
    private let budget: Int64
    private let now: () -> Date
    private let evictionQueue: (@escaping () -> Void) -> Void
    private let lock = NSLock()
    private var lastScheduledEviction: Date = .distantPast
    /// Keys a byte source has open, with how many: never evicted while held.
    private var activeKeys: [String: Int] = [:]
    /// The latest use of each key since launch, ahead of what the sidecar says.
    private var usedAt: [String: TimeInterval] = [:]
    /// When each key's use was last written to its sidecar.
    private var persistedAt: [String: TimeInterval] = [:]

    /// - Parameters:
    ///   - budget: what eviction trims to.
    ///   - now: the clock the eviction throttle and use times are measured on. Injectable for tests.
    ///   - evictionQueue: runs an eviction pass; the default is a utility global queue. Injectable for tests.
    public init(
        directory: URL,
        budget: Int64 = CachedRunStore.budgetBytes,
        now: @escaping () -> Date = Date.init,
        evictionQueue: @escaping (@escaping () -> Void) -> Void = CachedRunStore.defaultEvictionQueue
    ) {
        self.directory = directory
        self.budget = budget
        self.now = now
        self.evictionQueue = evictionQueue
        ensureDirectory()
    }

    /// Create the directory (and exclude it from backup) when missing: the OS may purge `Caches`
    /// while the app runs.
    private func ensureDirectory() {
        guard !FileManager.default.fileExists(atPath: directory.path) else { return }
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        var dir = directory
        try? dir.setResourceValues(values)
    }

    // MARK: - Active runs

    /// A byte source has the run open: it is not evicted until a matching ``release(_:)``.
    public func retain(_ key: String) {
        lock.lock(); defer { lock.unlock() }
        activeKeys[key, default: 0] += 1
    }

    public func release(_ key: String) {
        lock.lock(); defer { lock.unlock() }
        guard let count = activeKeys[key] else { return }
        if count <= 1 { activeKeys[key] = nil } else { activeKeys[key] = count - 1 }
    }

    /// Note a use: in memory always, in the sidecar at most once a minute.
    private func noteUseLocked(_ key: String) {
        let time = now().timeIntervalSince1970
        usedAt[key] = time
        guard time - (persistedAt[key] ?? 0) >= Self.touchPersistInterval else { return }
        persistedAt[key] = time
        if var sidecar = readSidecarLocked(key) {
            sidecar.touched = time
            writeSidecarLocked(key, sidecar)
        }
    }

    // MARK: - Lookup

    public func run(for key: String) -> Run? {
        lock.lock(); defer { lock.unlock() }
        return runLocked(key)
    }

    /// The data file of a run that holds the whole resource, for a `FileByteSource`; nil otherwise.
    public func completeRunURL(for key: String) -> URL? {
        guard let run = run(for: key), run.isComplete else { return nil }
        return dataURL(key)
    }

    /// Bytes `[offset, offset + maxLength)` clipped to the run; nil when `offset` is outside it.
    public func read(_ key: String, at offset: Int64, maxLength: Int) -> Data? {
        lock.lock(); defer { lock.unlock() }
        guard let run = runLocked(key), offset >= run.start, offset < run.end, maxLength > 0 else { return nil }
        noteUseLocked(key)
        guard let handle = FileHandle(forReadingAtPath: dataURL(key).path) else { return nil }
        defer { try? handle.close() }
        do {
            try handle.seek(toOffset: UInt64(offset - run.start))
            return try handle.read(upToCount: Int(min(Int64(maxLength), run.end - offset)))
        } catch {
            return nil
        }
    }

    // MARK: - Writing

    /// Drop whatever the key held and begin an empty run at `offset`.
    public func replace(_ key: String, startingAt offset: Int64, totalLength: Int64?) {
        lock.lock(); defer { lock.unlock() }
        let previous = runLocked(key)
        let previousSidecar = readSidecarLocked(key)
        let total = totalLength ?? previous?.totalLength
        // The tail is of the file, not of the run: it survives a replace unless the size changed.
        let tail = total == previousSidecar?.totalLength ? previousSidecar?.tail : nil
        // Old data out, then the sidecar, then the empty file: a crash at any point leaves either
        // the old run, or a sidecar over no bytes (a zero-length run eviction will get to) — never
        // old bytes under a new start, and never a data file eviction cannot see.
        try? FileManager.default.removeItem(at: dataURL(key))
        let time = now().timeIntervalSince1970
        usedAt[key] = time
        persistedAt[key] = time
        writeSidecarLocked(key, Sidecar(start: offset, totalLength: total, touched: time, tail: tail))
        FileManager.default.createFile(atPath: dataURL(key).path, contents: nil)
        engineLog.info("bytes: run replace key=\(Self.fileName(key), privacy: .public) at=\(offset) had=\(previous?.length ?? -1)")
        scheduleEvictionLocked(excluding: key)
    }

    /// A new run is the moment the store grows: trim in the background, at most once a minute,
    /// never touching the run just begun (or any run a byte source has open).
    private func scheduleEvictionLocked(excluding key: String) {
        let time = now()
        guard time.timeIntervalSince(lastScheduledEviction) > 60 else { return }
        lastScheduledEviction = time
        evictionQueue { [self] in
            evict(toBudget: budget, excluding: key)
        }
    }

    /// The first-access pass: trim once, in the background, with no run singled out (the active
    /// ones are still protected). Called when the shared store is created.
    func scheduleStartupEviction() {
        evictionQueue { [self] in
            evict(toBudget: budget, excluding: nil)
        }
    }

    /// Extend the run by `data` at its end. False, with nothing written, when there is no run.
    @discardableResult
    public func append(_ key: String, _ data: Data) -> Bool {
        lock.lock(); defer { lock.unlock() }
        guard runLocked(key) != nil, let handle = FileHandle(forWritingAtPath: dataURL(key).path) else { return false }
        defer { try? handle.close() }
        do {
            try handle.seekToEnd()
            try handle.write(contentsOf: data)
            noteUseLocked(key)
            return true
        } catch {
            return false
        }
    }

    public func setTotalLength(_ key: String, _ total: Int64) {
        lock.lock(); defer { lock.unlock() }
        guard var sidecar = readSidecarLocked(key), sidecar.totalLength != total else { return }
        // A different size is a different file: its last bytes are not these.
        if sidecar.totalLength != nil { sidecar.tail = nil }
        sidecar.totalLength = total
        writeSidecarLocked(key, sidecar)
    }

    // MARK: - The tail

    /// The resource's last bytes, when a play kept them and the size they were read at still
    /// stands. Nil when there is no run, no tail, or no total to check it against.
    public func tail(for key: String) -> Data? {
        lock.lock(); defer { lock.unlock() }
        guard let sidecar = readSidecarLocked(key), sidecar.totalLength != nil else { return nil }
        return sidecar.tail
    }

    /// Keep the resource's last bytes for the next play. With no run yet, an empty run at 0 is
    /// begun to hold the sidecar: the body that follows replaces it and carries the tail forward.
    /// Ignored when the sidecar already knows a different total: the tail is not of that file.
    public func setTail(_ key: String, _ tail: Data, totalLength: Int64) {
        lock.lock(); defer { lock.unlock() }
        if var sidecar = readSidecarLocked(key) {
            guard sidecar.totalLength == nil || sidecar.totalLength == totalLength else { return }
            sidecar.totalLength = totalLength
            sidecar.tail = tail
            writeSidecarLocked(key, sidecar)
        } else {
            writeSidecarLocked(key, Sidecar(start: 0, totalLength: totalLength, touched: now().timeIntervalSince1970, tail: tail))
            FileManager.default.createFile(atPath: dataURL(key).path, contents: nil)
        }
    }

    public func remove(_ key: String) {
        lock.lock(); defer { lock.unlock() }
        removeLocked(key)
    }

    // MARK: - Retention

    /// Refresh the key's clock: a play, from the network or from the disk, is a use.
    public func touch(_ key: String) {
        lock.lock(); defer { lock.unlock() }
        let time = now().timeIntervalSince1970
        usedAt[key] = time
        persistedAt[key] = time
        if var sidecar = readSidecarLocked(key) {
            sidecar.touched = time
            writeSidecarLocked(key, sidecar)
        }
    }

    /// Bytes held across every run.
    public func totalBytes() -> Int64 {
        scanEntries().entries.reduce(0) { $0 + $1.size }
    }

    /// Delete least-recently-touched runs until the store fits `budget`. `playingKey` is the
    /// episode just begun; runs a byte source has open (``retain(_:)``) are skipped too, since one
    /// may be writing it and the whole-file scanner may be reading it. The directory is listed and
    /// every sidecar decoded once WITHOUT the lock, which a victim's delete takes only briefly (and
    /// re-checks under), so a play never waits on a pass. Returns how many runs were removed.
    @discardableResult
    public func evict(toBudget budget: Int64? = nil, excluding playingKey: String? = nil) -> Int {
        let budget = budget ?? self.budget
        let scan = scanEntries()
        removeOrphansAndCorrupt(scan)

        lock.lock()
        let used = usedAt
        lock.unlock()
        var entries = scan.entries.map { entry in
            Entry(key: entry.key, size: entry.size, touched: max(entry.touched, used[entry.key] ?? 0))
        }
        var total = entries.reduce(0) { $0 + $1.size }
        guard total > budget else { return 0 }
        entries.sort { $0.touched < $1.touched }
        var removed = 0
        for entry in entries where total > budget && entry.key != playingKey {
            lock.lock()
            // Re-check under the lock: a source may have opened the run since the scan.
            if activeKeys[entry.key] == nil {
                removeLocked(entry.key)
                total -= entry.size
                removed += 1
            }
            lock.unlock()
        }
        engineLog.info("bytes: run evict removed=\(removed) remaining=\(total)")
        return removed
    }

    // MARK: - Files

    private struct Entry {
        let key: String
        let size: Int64
        let touched: TimeInterval
    }

    private struct Scan {
        var entries: [Entry] = []
        /// File-name stems of sidecars that do not decode.
        var corruptStems: [String] = []
        /// `.run` files with no sidecar beside them.
        var orphanDataNames: [String] = []
    }

    /// Every run on disk, sidecar decoded once (it carries the key so eviction can name it back).
    /// Takes no lock: sidecars are written atomically and every delete re-checks under the lock.
    private func scanEntries() -> Scan {
        let names = (try? FileManager.default.contentsOfDirectory(atPath: directory.path)) ?? []
        var scan = Scan()
        var stems = Set<String>()
        for name in names where name.hasSuffix(".json") {
            let stem = String(name.dropLast(5))
            stems.insert(stem)
            guard let file = readSidecarFile(stem: stem) else {
                // `resolved-urls.json` shares the directory; only digest-named sidecars are ours.
                if stem != "resolved-urls" { scan.corruptStems.append(stem) }
                continue
            }
            scan.entries.append(Entry(key: file.key, size: dataSize(stem: stem), touched: file.sidecar.touched))
        }
        scan.orphanDataNames = names.filter { $0.hasSuffix(".run") && !stems.contains(String($0.dropLast(4))) }
        return scan
    }

    /// `.run` files whose sidecar is gone (a crash between the two deletes), and runs whose
    /// sidecar will not decode: nothing can name either, so eviction would never see them. Each is
    /// re-checked under the lock before it goes.
    private func removeOrphansAndCorrupt(_ scan: Scan) {
        for name in scan.orphanDataNames {
            lock.lock()
            let stem = String(name.dropLast(4))
            if !FileManager.default.fileExists(atPath: directory.appendingPathComponent("\(stem).json").path) {
                try? FileManager.default.removeItem(at: directory.appendingPathComponent(name))
            }
            lock.unlock()
        }
        for stem in scan.corruptStems {
            lock.lock()
            if readSidecarFile(stem: stem) == nil {
                try? FileManager.default.removeItem(at: directory.appendingPathComponent("\(stem).run"))
                try? FileManager.default.removeItem(at: directory.appendingPathComponent("\(stem).json"))
            }
            lock.unlock()
        }
    }

    private func readSidecarFile(stem: String) -> SidecarFile? {
        guard let data = try? Data(contentsOf: directory.appendingPathComponent("\(stem).json")) else { return nil }
        return try? JSONDecoder().decode(SidecarFile.self, from: data)
    }

    private func dataSize(stem: String) -> Int64 {
        let attributes = try? FileManager.default.attributesOfItem(atPath: directory.appendingPathComponent("\(stem).run").path)
        return (attributes?[.size] as? NSNumber)?.int64Value ?? 0
    }

    private func runLocked(_ key: String) -> Run? {
        guard let sidecar = readSidecarLocked(key) else { return nil }
        return Run(start: sidecar.start, length: dataSizeLocked(key), totalLength: sidecar.totalLength)
    }

    private func dataSizeLocked(_ key: String) -> Int64 {
        let attributes = try? FileManager.default.attributesOfItem(atPath: dataURL(key).path)
        return (attributes?[.size] as? NSNumber)?.int64Value ?? 0
    }

    private func removeLocked(_ key: String) {
        try? FileManager.default.removeItem(at: dataURL(key))
        try? FileManager.default.removeItem(at: sidecarURL(key))
        usedAt[key] = nil
        persistedAt[key] = nil
    }

    private struct SidecarFile: Codable {
        var key: String
        var sidecar: Sidecar
    }

    private func readSidecarLocked(_ key: String) -> Sidecar? {
        guard let data = try? Data(contentsOf: sidecarURL(key)) else { return nil }
        return (try? JSONDecoder().decode(SidecarFile.self, from: data))?.sidecar
    }

    private func writeSidecarLocked(_ key: String, _ sidecar: Sidecar) {
        ensureDirectory()
        guard let data = try? JSONEncoder().encode(SidecarFile(key: key, sidecar: sidecar)) else { return }
        try? data.write(to: sidecarURL(key), options: .atomic)
    }

    private func dataURL(_ key: String) -> URL { directory.appendingPathComponent("\(Self.fileName(key)).run") }
    private func sidecarURL(_ key: String) -> URL { directory.appendingPathComponent("\(Self.fileName(key)).json") }

    /// A URL is not a file name; its digest is.
    private static func fileName(_ key: String) -> String {
        SHA256.hash(data: Data(key.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}
