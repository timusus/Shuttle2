// Copied from Shuttle Podcasts (podcasts@9ee6e0954) mobile/ios/Playback/Sources/Playback/Streaming/ResolvedURLCache.swift — see ios/Playback/README.md.
import Foundation
import OSLog

private let engineLog = Logger(subsystem: "com.simplecityapps.shuttle2", category: "network")

/// **Where an enclosure's redirect chain ended last time, so the next play can skip it.**
///
/// An enclosure URL is routinely several `302`s from the bytes (feed host → one or two tracking
/// prefixes → CDN), and every hop is a fresh DNS + TCP + TLS round trip paid in series before the
/// first byte. ``HTTPRangeByteSource`` already follows the chain once per play and reuses its end
/// for every later transaction of that play (#193 measured 5 hops at 5.3 s before the first
/// response); this keeps that end ACROSS plays, keyed by the URL as the feed gave it, so a fresh
/// start or a resume opens its first transaction at the CDN directly.
///
/// The entry is a hint, never a promise: the source that uses it falls back to the original URL
/// the moment the remembered one answers anything but a `2xx` (a signed CDN URL past its expiry
/// answers `403`) or fails to connect, and the entry is dropped. Entries also expire on their own
/// after ``ttl``, and the whole map is capped at ``maxEntries`` (oldest out) so it cannot grow
/// with the library. Persisted as one small JSON file next to the run store, because the resume
/// that matters most is the cold-start one.
///
/// Thread-safe by one lock; every call is a dictionary lookup plus, on a change, one small write.
public final class ResolvedURLCache {

    /// How long a remembered end is trusted without being re-proven. A day: CDN paths behind a
    /// tracking prefix are stable for months, and anything shorter-lived answers `403` and is
    /// dropped on the spot anyway.
    public static let ttl: TimeInterval = 24 * 60 * 60
    /// Oldest-out cap on the map. A listener's active rotation is a few dozen episodes.
    static let maxEntries = 256

    public static let shared: ResolvedURLCache = {
        let directory = CachedRunStore.sharedDirectory
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return ResolvedURLCache(fileURL: directory.appendingPathComponent("resolved-urls.json"))
    }()

    struct Entry: Codable, Equatable {
        var resolved: URL
        var resolvedAt: TimeInterval
    }

    private let fileURL: URL?
    private let now: () -> TimeInterval
    private let lock = NSLock()
    /// Loaded from disk on first use, nil until then.
    private var entries: [String: Entry]?

    /// - Parameters:
    ///   - fileURL: where the map is persisted; nil keeps it in memory only (tests).
    ///   - now: the clock expiry is measured on. Injectable so a test can age an entry.
    public init(fileURL: URL?, now: @escaping () -> TimeInterval = { Date().timeIntervalSince1970 }) {
        self.fileURL = fileURL
        self.now = now
    }

    /// The remembered end of `original`'s chain, or nil when there is none, it has expired, or it
    /// is `original` itself (nothing to skip).
    func resolved(for original: URL) -> URL? {
        lock.lock(); defer { lock.unlock() }
        guard let entry = loadedLocked()[original.absoluteString] else { return nil }
        guard now() - entry.resolvedAt < Self.ttl else {
            entries?[original.absoluteString] = nil
            saveLocked()
            return nil
        }
        return entry.resolved == original ? nil : entry.resolved
    }

    /// Remember (or refresh) where `original` ended up. A chain that ends where it started is not
    /// recorded, and an unchanged entry is only re-stamped, so a working URL keeps its trust.
    func record(original: URL, resolved: URL) {
        lock.lock(); defer { lock.unlock() }
        var map = loadedLocked()
        let key = original.absoluteString
        if resolved == original {
            guard map[key] != nil else { return }
            map[key] = nil
        } else {
            map[key] = Entry(resolved: resolved, resolvedAt: now())
            if map.count > Self.maxEntries {
                let oldest = map.sorted { $0.value.resolvedAt < $1.value.resolvedAt }.prefix(map.count - Self.maxEntries)
                for (staleKey, _) in oldest { map[staleKey] = nil }
            }
        }
        entries = map
        saveLocked()
    }

    /// Forget `original`'s entry: the remembered end stopped answering.
    func invalidate(_ original: URL) {
        lock.lock(); defer { lock.unlock() }
        var map = loadedLocked()
        guard map.removeValue(forKey: original.absoluteString) != nil else { return }
        entries = map
        saveLocked()
    }

    /// How many entries are held. Tests only.
    var count: Int {
        lock.lock(); defer { lock.unlock() }
        return loadedLocked().count
    }

    // MARK: - Disk

    private func loadedLocked() -> [String: Entry] {
        if let entries { return entries }
        var loaded: [String: Entry] = [:]
        if let fileURL, let data = try? Data(contentsOf: fileURL),
           let decoded = try? JSONDecoder().decode([String: Entry].self, from: data) {
            loaded = decoded
        }
        entries = loaded
        return loaded
    }

    private func saveLocked() {
        guard let fileURL, let entries else { return }
        do {
            // The OS may have purged `Caches` since the last write.
            try FileManager.default.createDirectory(at: fileURL.deletingLastPathComponent(), withIntermediateDirectories: true)
            try JSONEncoder().encode(entries).write(to: fileURL, options: .atomic)
        } catch {
            engineLog.error("bytes: resolved-url cache write failed error=\(error.localizedDescription, privacy: .public)")
        }
    }
}
