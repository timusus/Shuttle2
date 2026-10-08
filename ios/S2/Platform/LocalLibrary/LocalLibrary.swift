import Foundation
import os
import S2Tags
import Shared

/// This device's music files (#590), the Kotlin `IosLocalFiles` the library's local provider imports through: the
/// app's Documents folder (Files' "On My iPhone > Shuttle Music", and Finder's file sharing) and the folders picked in
/// Files, kept as bookmarks.
///
/// A song's path is `s2local://<folder id>/<path in the folder>`: `documents` for Documents, the folder's own id for a
/// picked one. The app's container moves on every update, so paths never hold it; `fileURL(forSongPath:)` resolves one
/// to where the file is now.
///
/// A picked folder's security scope is opened when its bookmark resolves (at launch, off the main thread, or when it's
/// picked) and stays open for the process's life, so the engine can open its files whenever it plays them. It counts
/// as reachable only once its scope opened, or when it's inside the app's own container, which needs none. A bookmark
/// that resolves stale is saved again; one that doesn't resolve leaves the folder without access until it's picked
/// again, which keeps its id, so its songs keep their history.
///
/// Each file is read under one folder only: picking Documents, or a folder inside Documents or inside one already
/// picked, adds nothing, as its files are read already; a folder picked around ones already there is added, and its
/// listing leaves out the folders inside it that are read on their own, so their songs keep their paths.
///
/// A listing tells the provider which folders it couldn't read in full (out of reach, unreadable, or failing partway),
/// so their songs are kept rather than removed. A file iCloud has offloaded (`.<name>.icloud`) is listed as offloaded
/// under its own name, so its song is kept too.
///
/// **Threading:** Kotlin calls in from the import's background threads and the player's main thread; the state is
/// behind a lock, and the file system is read outside it. Every call that needs the picked folders waits until the
/// bookmarks have resolved.
final class LocalLibrary: NSObject, IosLocalFiles, @unchecked Sendable {
    static let scheme = "s2local"
    static let documentsID = "documents"

    /// The extensions read as audio: every container the engine's FFmpeg build demuxes.
    static let audioExtensions: Set<String> = [
        "mp3", "m4a", "m4b", "mp4", "aac", "flac", "ogg", "oga", "opus", "wav", "aif", "aiff", "aifc", "mka",
    ]

    /// The images beside a file its album's artwork may be, as Android's `FolderImageReader` looks for them.
    static let folderImageNames = ["cover", "folder", "front", "album", "albumart"]
    static let folderImageExtensions = ["jpg", "jpeg", "png"]

    /// A picked folder as it's saved: [path] is where it was last found, for display and to match a re-pick.
    struct Folder: Codable, Equatable {
        var id: String
        var name: String
        var path: String
        var bookmark: Data
    }

    /// A reachable folder: [scoped] when its security scope was opened, so it's closed when the folder goes.
    private struct Access {
        let url: URL
        let scoped: Bool
    }

    private static let foldersKey = "local_library_folders"
    private static let fingerprintKey = "local_library_fingerprint"
    private static let log = os.Logger(subsystem: "com.simplecityapps.shuttle2", category: "LocalLibrary")

    let documents: URL
    private let defaults: UserDefaults
    /// The app's own container: a folder inside it needs no security scope.
    private let container: URL
    private let openScope: (URL) -> Bool
    private let fileManager = FileManager.default
    private let lock = NSLock()
    private let resolution = DispatchGroup()
    private var saved: [Folder]
    /// Each picked folder whose bookmark resolved and whose scope is open.
    private var reachable: [String: Access] = [:]
    /// Folders the picker returned, by their URL string, until Kotlin adds them: the picker's URL carries the
    /// security scope, which a URL rebuilt from its string doesn't.
    private var staged: [String: URL] = [:]
    /// Artwork already read: a song's by its path, a folder's image by the folder's (`folder:`-prefixed), each nil when
    /// there's none. A cover drawn at several sizes (a row, the album page, Now Playing, the widgets) reads its file once,
    /// and an album's songs share their folder's image. Every listing clears it, as files may have changed by then.
    private let artworkCache: NSCache<NSString, ReadArtwork> = {
        let cache = NSCache<NSString, ReadArtwork>()
        cache.totalCostLimit = 32 * 1024 * 1024
        cache.countLimit = 2000
        return cache
    }()

    init(
        defaults: UserDefaults = .standard,
        documents: URL = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0],
        container: URL = URL(fileURLWithPath: NSHomeDirectory(), isDirectory: true),
        openScope: @escaping (URL) -> Bool = { $0.startAccessingSecurityScopedResource() }
    ) {
        self.defaults = defaults
        self.documents = documents
        self.container = container
        self.openScope = openScope
        saved = defaults.data(forKey: Self.foldersKey).flatMap { try? JSONDecoder().decode([Folder].self, from: $0) } ?? []
        super.init()
        // Resolving a bookmark can wait on a file provider, so it's kept off the main thread at launch
        resolution.enter()
        DispatchQueue.global(qos: .userInitiated).async {
            self.resolveSaved()
            self.resolution.leave()
        }
    }

    /// Waits until the saved bookmarks have resolved.
    private func waitForResolution() {
        resolution.wait()
    }

    // MARK: - Folders

    /// Keeps [url], a folder the Files picker returned, until Kotlin's `addFolder(url:)` adds it by its string.
    func stage(_ url: URL) -> String {
        lock.withLock { staged[url.absoluteString] = url }
        return url.absoluteString
    }

    func folders() -> [IosLocalFolder] {
        waitForResolution()
        return lock.withLock {
            saved.map { folder in
                IosLocalFolder(id: folder.id, name: folder.name, path: folder.path, hasAccess: reachable[folder.id] != nil)
            }
        }
    }

    func addFolder(url string: String) -> Bool {
        waitForResolution()
        guard let url = lock.withLock({ staged.removeValue(forKey: string) }) ?? URL(string: string), url.isFileURL else {
            return false
        }
        let path = url.resolvingSymlinksInPath().path
        if isReadAlready(path) { return true }
        guard let access = open(url) else {
            Self.log.error("Picked folder \(path, privacy: .private) couldn't be opened")
            return false
        }
        guard let bookmark = try? url.bookmarkData(options: [], includingResourceValuesForKeys: nil, relativeTo: nil) else {
            close(access)
            return false
        }
        lock.withLock {
            if let index = saved.firstIndex(where: { $0.path == path }) {
                let id = saved[index].id
                reachable.removeValue(forKey: id).map(close)
                saved[index].bookmark = bookmark
                reachable[id] = access
            } else {
                // Lowercase, as a URL's host is once normalised: artwork urls carry the id there
                let folder = Folder(id: UUID().uuidString.lowercased(), name: url.lastPathComponent, path: path, bookmark: bookmark)
                saved.append(folder)
                reachable[folder.id] = access
            }
            save()
        }
        return true
    }

    func removeFolder(id: String) {
        waitForResolution()
        lock.withLock {
            saved.removeAll { $0.id == id }
            reachable.removeValue(forKey: id).map(close)
            save()
        }
    }

    /// Whether the files at [path] are read already: it's Documents or inside it, or inside a picked folder (picking
    /// one again renews its access instead).
    private func isReadAlready(_ path: String) -> Bool {
        if Self.isInside(path, documents.resolvingSymlinksInPath().path) { return true }
        return lock.withLock { saved.contains { $0.path != path && Self.isInside(path, $0.path) } }
    }

    /// Whether [path] is [folder] or inside it.
    static func isInside(_ path: String, _ folder: String) -> Bool {
        path == folder || path.hasPrefix(folder.hasSuffix("/") ? folder : folder + "/")
    }

    /// Opens [url]'s security scope; nil when it won't open and the folder is outside the app's container.
    private func open(_ url: URL) -> Access? {
        if openScope(url) { return Access(url: url, scoped: true) }
        guard Self.isInside(url.resolvingSymlinksInPath().path, container.resolvingSymlinksInPath().path) else { return nil }
        return Access(url: url, scoped: false)
    }

    private func close(_ access: Access) {
        if access.scoped { access.url.stopAccessingSecurityScopedResource() }
    }

    /// Resolves each saved bookmark, opening its scope; a stale one is saved again.
    private func resolveSaved() {
        let folders = lock.withLock { saved }
        var resolved: [String: (access: Access, path: String, bookmark: Data?)] = [:]
        for folder in folders {
            var stale = false
            let url: URL
            do {
                url = try URL(resolvingBookmarkData: folder.bookmark, options: [], relativeTo: nil, bookmarkDataIsStale: &stale)
            } catch {
                Self.log.error("Folder \(folder.id, privacy: .public)'s bookmark didn't resolve, so it's out of reach until it's picked again: \(error.localizedDescription, privacy: .public)")
                continue
            }
            guard let access = open(url) else {
                Self.log.error("Folder \(folder.id, privacy: .public)'s security scope didn't open, so it's out of reach until it's picked again")
                continue
            }
            let bookmark = stale ? try? url.bookmarkData(options: [], includingResourceValuesForKeys: nil, relativeTo: nil) : nil
            resolved[folder.id] = (access, url.resolvingSymlinksInPath().path, bookmark)
        }
        lock.withLock {
            var changed = false
            for index in saved.indices {
                guard let found = resolved[saved[index].id] else { continue }
                reachable[saved[index].id] = found.access
                saved[index].path = found.path
                if let bookmark = found.bookmark {
                    saved[index].bookmark = bookmark
                    changed = true
                }
            }
            if changed { save() }
        }
    }

    private func save() {
        defaults.set(try? JSONEncoder().encode(saved), forKey: Self.foldersKey)
    }

    // MARK: - Files

    /// The listing for an import. It isn't the last import's until `imported(listing:)` says its songs are stored.
    func audioFiles() -> IosLocalListing {
        let listing = listAudioFiles()
        return IosLocalListing(
            files: listing.files.map { IosLocalFileRef(path: $0.path, lastModifiedMs: $0.lastModifiedMs, size: $0.size) },
            offloaded: listing.offloaded,
            folders: listing.folders,
            unread: listing.unread,
            fingerprint: Self.fingerprint(of: listing)
        )
    }

    func imported(listing: IosLocalListing) {
        defaults.set(listing.fingerprint, forKey: Self.fingerprintKey)
    }

    /// Whether the files differ from the ones the last stored import listed: one added, removed, changed, offloaded or
    /// downloaded, or a folder picked, out of reach or unreadable. Lists every file, so call it off the main thread.
    func changedSinceLastImport() -> Bool {
        defaults.string(forKey: Self.fingerprintKey) != Self.fingerprint(of: listAudioFiles())
    }

    func download(path: String) {
        guard let url = fileURL(forSongPath: path) else { return }
        do {
            try fileManager.startDownloadingUbiquitousItem(at: url)
        } catch {
            Self.log.error("Couldn't download an offloaded file: \(error.localizedDescription, privacy: .public)")
        }
    }

    func readTags(path: String) -> IosLocalTags? {
        guard let url = fileURL(forSongPath: path), let tags = AudioFileTags.read(fileAt: url) else { return nil }
        return IosLocalTags(tags)
    }

    func fileUrl(path: String) -> String? {
        fileURL(forSongPath: path)?.absoluteString
    }

    /// Where song [path]'s file is now, or nil when its folder is gone or out of reach.
    func fileURL(forSongPath path: String) -> URL? {
        let prefix = "\(Self.scheme)://"
        guard path.hasPrefix(prefix) else { return nil }
        let rest = path.dropFirst(prefix.count)
        guard let slash = rest.firstIndex(of: "/") else { return nil }
        let id = String(rest[..<slash])
        let relative = String(rest[rest.index(after: slash)...])
        guard !relative.isEmpty, let root = root(id) else { return nil }
        return root.appendingPathComponent(relative, isDirectory: false)
    }

    /// The picture embedded in song [path]'s file, else an image beside it (cover.jpg, folder.png...). Read once until the
    /// next listing.
    func artwork(forSongPath path: String) -> Data? {
        guard let url = fileURL(forSongPath: path), fileManager.fileExists(atPath: url.path) else { return nil }
        return cachedArtwork(path) {
            AudioFileTags.embeddedPicture(fileAt: url) ?? folderImage(in: url.deletingLastPathComponent())
        }
    }

    /// The first of the folder image names [directory] has, in their order and with any case.
    private func folderImage(in directory: URL) -> Data? {
        cachedArtwork("folder:\(directory.path)") {
            guard let names = try? fileManager.contentsOfDirectory(atPath: directory.path) else { return nil }
            let byLowercased = Dictionary(names.map { ($0.lowercased(), $0) }, uniquingKeysWith: { first, _ in first })
            for name in Self.folderImageNames {
                for ext in Self.folderImageExtensions {
                    if let match = byLowercased["\(name).\(ext)"], let data = try? Data(contentsOf: directory.appendingPathComponent(match)) {
                        return data
                    }
                }
            }
            return nil
        }
    }

    private func cachedArtwork(_ key: String, read: () -> Data?) -> Data? {
        if let hit = artworkCache.object(forKey: key as NSString) { return hit.data }
        let data = read()
        artworkCache.setObject(ReadArtwork(data), forKey: key as NSString, cost: data?.count ?? 0)
        return data
    }

    /// The artwork of the song an `s2local:` artwork url names: Kotlin's `ArtworkUrls` percent-encodes the song's path.
    /// The folder id is the url's host, which normalising may have lowercased, so it's matched without case.
    func artwork(forArtworkURL url: URL) -> Data? {
        guard url.scheme == Self.scheme, let host = url.host(percentEncoded: false) else { return nil }
        return artwork(forSongPath: "\(Self.scheme)://\(folderID(matching: host))\(url.path(percentEncoded: false))")
    }

    /// The saved folder id [host] names, whatever its case.
    private func folderID(matching host: String) -> String {
        if host.caseInsensitiveCompare(Self.documentsID) == .orderedSame { return Self.documentsID }
        waitForResolution()
        return lock.withLock { saved.first { $0.id.caseInsensitiveCompare(host) == .orderedSame }?.id } ?? host
    }

    private func root(_ id: String) -> URL? {
        if id == Self.documentsID { return documents }
        waitForResolution()
        return lock.withLock { reachable[id]?.url }
    }

    struct Listed: Equatable {
        var path: String
        var lastModifiedMs: Int64
        var size: Int64
    }

    /// See `IosLocalListing`: the audio files, the offloaded ones' song paths, each folder tried, and those not read in full.
    struct Listing: Equatable {
        var files: [Listed] = []
        var offloaded: [String] = []
        var folders: [String] = []
        var unread: [String] = []
    }

    /// Every audio file under Documents and the picked folders, skipping hidden files and folders. A picked folder out
    /// of reach is unread.
    func listAudioFiles() -> Listing {
        waitForResolution()
        artworkCache.removeAllObjects()
        let (picked, unreachable) = lock.withLock {
            (saved.compactMap { folder in reachable[folder.id].map { (folder.id, $0.url) } }, saved.map(\.id).filter { reachable[$0] == nil })
        }
        let roots = [(Self.documentsID, documents)] + picked
        let rootPaths = roots.map { ($0.0, $0.1.resolvingSymlinksInPath().path) }
        var listing = Listing(folders: roots.map(\.0) + unreachable, unread: unreachable)
        for (id, root) in roots {
            let path = root.resolvingSymlinksInPath().path
            // Folders inside this one that are read on their own
            let nested = Set(rootPaths.filter { $0.0 != id && $0.1 != path && Self.isInside($0.1, path) }.map(\.1))
            if !list(root, id: id, skipping: nested, into: &listing) { listing.unread.append(id) }
        }
        return listing
    }

    /// Lists [root]'s audio files into [listing]; false when it couldn't be read in full.
    private func list(_ root: URL, id: String, skipping nested: Set<String>, into listing: inout Listing) -> Bool {
        var isDirectory: ObjCBool = false
        guard fileManager.fileExists(atPath: root.path, isDirectory: &isDirectory), isDirectory.boolValue,
              fileManager.isReadableFile(atPath: root.path)
        else {
            Self.log.error("Folder \(id, privacy: .public) is missing or unreadable, so its songs are kept")
            return false
        }
        var failed = false
        let keys: [URLResourceKey] = [.isDirectoryKey, .isRegularFileKey, .contentModificationDateKey, .fileSizeKey]
        guard let enumerator = fileManager.enumerator(
            at: root,
            includingPropertiesForKeys: keys,
            options: [.producesRelativePathURLs],
            errorHandler: { _, error in
                Self.log.error("Folder \(id, privacy: .public) failed partway, so its songs are kept: \(error.localizedDescription, privacy: .public)")
                failed = true
                return true
            }
        ) else { return false }
        while let url = enumerator.nextObject() as? URL {
            let relative = url.relativePath
            let name = url.lastPathComponent
            let values = try? url.resourceValues(forKeys: Set(keys))
            if values?.isDirectory == true {
                if name.hasPrefix(".") || (!nested.isEmpty && nested.contains(url.absoluteURL.resolvingSymlinksInPath().path)) {
                    enumerator.skipDescendants()
                }
                continue
            }
            if name.hasPrefix(".") {
                // iCloud's placeholder for a file it offloaded: `.<name>.icloud` beside where `<name>` was
                if name.hasSuffix(".icloud") {
                    let original = String(name.dropFirst().dropLast(".icloud".count))
                    if Self.audioExtensions.contains((original as NSString).pathExtension.lowercased()) {
                        let directory = (relative as NSString).deletingLastPathComponent
                        listing.offloaded.append("\(Self.scheme)://\(id)/\(directory.isEmpty ? original : directory + "/" + original)")
                    }
                }
                continue
            }
            guard values?.isRegularFile == true, Self.audioExtensions.contains((name as NSString).pathExtension.lowercased()) else { continue }
            let modified = values?.contentModificationDate ?? .distantPast
            listing.files.append(Listed(
                path: "\(Self.scheme)://\(id)/\(relative)",
                lastModifiedMs: Int64((modified.timeIntervalSince1970 * 1000).rounded()),
                size: Int64(values?.fileSize ?? 0)
            ))
        }
        return !failed
    }

    /// FNV-1a over the listing: stable across launches, as `Hasher` isn't.
    static func fingerprint(of listing: Listing) -> String {
        var hash: UInt64 = 0xcbf2_9ce4_8422_2325
        let lines = listing.files.map { "\($0.path)\u{0}\($0.lastModifiedMs)\u{0}\($0.size)" }
            + listing.offloaded.map { "\($0)\u{0}offloaded" }
            + listing.unread.map { "\($0)\u{0}unread" }
        for line in lines.sorted() {
            for byte in "\(line)\n".utf8 {
                hash ^= UInt64(byte)
                hash = hash &* 0x0000_0100_0000_01b3
            }
        }
        return "\(listing.files.count):\(String(hash, radix: 16))"
    }
}

extension IosLocalTags {
    /// The engine's reading of a file: its raw tags, which the Kotlin provider maps to a song, and audio properties.
    convenience init(_ tags: AudioFileTags) {
        self.init(
            tags: tags.tags.map { IosLocalTag(key: $0.key, value: $0.value) },
            durationMs: tags.durationMs.map { KotlinLong(longLong: $0) },
            sampleRate: tags.sampleRate.map { KotlinInt(int: Int32(clamping: $0)) },
            channelCount: tags.channelCount.map { KotlinInt(int: Int32(clamping: $0)) },
            bitDepth: tags.bitDepth.map { KotlinInt(int: Int32(clamping: $0)) },
            bitRate: tags.bitRateKbps.map { KotlinInt(int: Int32(clamping: $0)) },
            codec: tags.codec
        )
    }
}

/// A read of a song's or folder's artwork, for `NSCache`, which holds objects: nil data is a file with none.
private final class ReadArtwork {
    let data: Data?

    init(_ data: Data?) {
        self.data = data
    }
}
