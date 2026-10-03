import Foundation
import S2Playback
import Shared

/// This device's music files (#590), the Kotlin `IosLocalFiles` the library's local provider imports through: the
/// app's Documents folder (Files' "On My iPhone > Shuttle Music", and Finder's file sharing) and the folders picked in
/// Files, kept as bookmarks.
///
/// A song's path is `s2local://<folder id>/<path in the folder>`: `documents` for Documents, the folder's own id for a
/// picked one. The app's container moves on every update, so paths never hold it; `fileURL(forSongPath:)` resolves one
/// to where the file is now.
///
/// A picked folder's security scope is opened when its bookmark resolves (at launch, or when it's picked) and stays
/// open for the process's life, so the engine can open its files whenever it plays them. A bookmark that resolves
/// stale is saved again; one that doesn't resolve leaves the folder without access until it's picked again, which
/// keeps its id, so its songs keep their history.
///
/// **Threading:** Kotlin calls in from the import's background threads and the player's main thread; the state is
/// behind a lock, and the file system is read outside it.
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

    private static let foldersKey = "local_library_folders"
    private static let fingerprintKey = "local_library_fingerprint"

    let documents: URL
    private let defaults: UserDefaults
    private let fileManager = FileManager.default
    private let lock = NSLock()
    private var saved: [Folder]
    /// Each picked folder whose bookmark resolved, its scope open.
    private var reachable: [String: URL] = [:]
    /// Folders the picker returned, by their URL string, until Kotlin adds them: the picker's URL carries the
    /// security scope, which a URL rebuilt from its string doesn't.
    private var staged: [String: URL] = [:]

    init(
        defaults: UserDefaults = .standard,
        documents: URL = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    ) {
        self.defaults = defaults
        self.documents = documents
        saved = defaults.data(forKey: Self.foldersKey).flatMap { try? JSONDecoder().decode([Folder].self, from: $0) } ?? []
        super.init()
        resolveSaved()
    }

    // MARK: - Folders

    /// Keeps [url], a folder the Files picker returned, until Kotlin's `addFolder(url:)` adds it by its string.
    func stage(_ url: URL) -> String {
        lock.withLock { staged[url.absoluteString] = url }
        return url.absoluteString
    }

    func folders() -> [IosLocalFolder] {
        lock.withLock {
            saved.map { folder in
                IosLocalFolder(id: folder.id, name: folder.name, path: folder.path, hasAccess: reachable[folder.id] != nil)
            }
        }
    }

    func addFolder(url string: String) -> Bool {
        guard let url = lock.withLock({ staged.removeValue(forKey: string) }) ?? URL(string: string), url.isFileURL else {
            return false
        }
        let opened = url.startAccessingSecurityScopedResource()
        guard let bookmark = try? url.bookmarkData(options: [], includingResourceValuesForKeys: nil, relativeTo: nil) else {
            if opened { url.stopAccessingSecurityScopedResource() }
            return false
        }
        let path = url.resolvingSymlinksInPath().path
        lock.withLock {
            if let index = saved.firstIndex(where: { $0.path == path }) {
                let id = saved[index].id
                reachable.removeValue(forKey: id)?.stopAccessingSecurityScopedResource()
                saved[index].bookmark = bookmark
                reachable[id] = url
            } else {
                let folder = Folder(id: UUID().uuidString, name: url.lastPathComponent, path: path, bookmark: bookmark)
                saved.append(folder)
                reachable[folder.id] = url
            }
            save()
        }
        return true
    }

    func removeFolder(id: String) {
        lock.withLock {
            saved.removeAll { $0.id == id }
            reachable.removeValue(forKey: id)?.stopAccessingSecurityScopedResource()
            save()
        }
    }

    /// Resolves each saved bookmark, opening its scope; a stale one is saved again.
    private func resolveSaved() {
        var changed = false
        for index in saved.indices {
            var stale = false
            guard let url = try? URL(resolvingBookmarkData: saved[index].bookmark, options: [], relativeTo: nil, bookmarkDataIsStale: &stale) else {
                continue
            }
            _ = url.startAccessingSecurityScopedResource()
            reachable[saved[index].id] = url
            saved[index].path = url.resolvingSymlinksInPath().path
            if stale, let bookmark = try? url.bookmarkData(options: [], includingResourceValuesForKeys: nil, relativeTo: nil) {
                saved[index].bookmark = bookmark
                changed = true
            }
        }
        if changed { save() }
    }

    private func save() {
        defaults.set(try? JSONEncoder().encode(saved), forKey: Self.foldersKey)
    }

    // MARK: - Files

    func audioFiles() -> [IosLocalFileRef] {
        let files = listAudioFiles()
        defaults.set(Self.fingerprint(of: files), forKey: Self.fingerprintKey)
        return files.map { IosLocalFileRef(path: $0.path, lastModifiedMs: $0.lastModifiedMs, size: $0.size) }
    }

    /// Whether the files differ from the ones the last import listed: one added, removed or changed, or a folder
    /// picked or out of reach. Lists every file, so call it off the main thread.
    func changedSinceLastImport() -> Bool {
        defaults.string(forKey: Self.fingerprintKey) != Self.fingerprint(of: listAudioFiles())
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

    /// The picture embedded in song [path]'s file, else an image beside it (cover.jpg, folder.png...).
    func artwork(forSongPath path: String) -> Data? {
        guard let url = fileURL(forSongPath: path), fileManager.fileExists(atPath: url.path) else { return nil }
        if let picture = AudioFileTags.embeddedPicture(fileAt: url) { return picture }
        let directory = url.deletingLastPathComponent()
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

    /// The artwork of the song an `s2local:` artwork url names: Kotlin's `ArtworkUrls` percent-encodes the song's path.
    func artwork(forArtworkURL url: URL) -> Data? {
        guard url.scheme == Self.scheme, let id = url.host(percentEncoded: false) else { return nil }
        return artwork(forSongPath: "\(Self.scheme)://\(id)\(url.path(percentEncoded: false))")
    }

    private func root(_ id: String) -> URL? {
        id == Self.documentsID ? documents : lock.withLock { reachable[id] }
    }

    struct Listed: Equatable {
        var path: String
        var lastModifiedMs: Int64
        var size: Int64
    }

    /// Every audio file under Documents and the reachable picked folders, skipping hidden files and folders.
    func listAudioFiles() -> [Listed] {
        let roots = [(Self.documentsID, documents)] + lock.withLock {
            saved.compactMap { folder in reachable[folder.id].map { (folder.id, $0) } }
        }
        return roots.flatMap { id, root in list(root, id: id) }
    }

    private func list(_ root: URL, id: String) -> [Listed] {
        guard let enumerator = fileManager.enumerator(atPath: root.path) else { return [] }
        var files: [Listed] = []
        while let relative = enumerator.nextObject() as? String {
            let name = (relative as NSString).lastPathComponent
            let attributes = enumerator.fileAttributes
            if name.hasPrefix(".") {
                if attributes?[.type] as? FileAttributeType == .typeDirectory { enumerator.skipDescendants() }
                continue
            }
            guard attributes?[.type] as? FileAttributeType == .typeRegular,
                  Self.audioExtensions.contains((name as NSString).pathExtension.lowercased())
            else { continue }
            let modified = (attributes?[.modificationDate] as? Date) ?? .distantPast
            let size = (attributes?[.size] as? NSNumber)?.int64Value ?? 0
            files.append(Listed(
                path: "\(Self.scheme)://\(id)/\(relative)",
                lastModifiedMs: Int64((modified.timeIntervalSince1970 * 1000).rounded()),
                size: size
            ))
        }
        return files
    }

    /// FNV-1a over the listing: stable across launches, as `Hasher` isn't.
    static func fingerprint(of files: [Listed]) -> String {
        var hash: UInt64 = 0xcbf2_9ce4_8422_2325
        for file in files.sorted(by: { $0.path < $1.path }) {
            for byte in "\(file.path)\u{0}\(file.lastModifiedMs)\u{0}\(file.size)\n".utf8 {
                hash ^= UInt64(byte)
                hash = hash &* 0x0000_0100_0000_01b3
            }
        }
        return "\(files.count):\(String(hash, radix: 16))"
    }
}

extension IosLocalTags {
    /// The engine's reading of a file's tags, as the Kotlin provider maps them to a song.
    convenience init(_ tags: AudioFileTags) {
        self.init(
            title: tags.title,
            artists: tags.artists,
            artistDisplay: tags.artistDisplay,
            artistsTag: tags.artistsTag,
            albumArtist: tags.albumArtist,
            albumArtists: tags.albumArtists,
            album: tags.album,
            track: tags.track.map { KotlinInt(int: Int32(clamping: $0)) },
            disc: tags.disc.map { KotlinInt(int: Int32(clamping: $0)) },
            year: tags.year.map { KotlinInt(int: Int32(clamping: $0)) },
            genres: tags.genres,
            replayGainTrack: tags.replayGainTrack.map { KotlinDouble(double: $0) },
            replayGainAlbum: tags.replayGainAlbum.map { KotlinDouble(double: $0) },
            lyrics: tags.lyrics,
            grouping: tags.grouping,
            compilation: tags.compilation.map { KotlinBoolean(bool: $0) },
            mbTrackId: tags.mbTrackId,
            mbAlbumId: tags.mbAlbumId,
            mbReleaseGroupId: tags.mbReleaseGroupId,
            mbArtistIds: tags.mbArtistIds,
            mbAlbumArtistIds: tags.mbAlbumArtistIds,
            durationMs: tags.durationMs.map { KotlinLong(longLong: $0) },
            sampleRate: tags.sampleRate.map { KotlinInt(int: Int32(clamping: $0)) },
            channelCount: tags.channelCount.map { KotlinInt(int: Int32(clamping: $0)) },
            bitDepth: tags.bitDepth.map { KotlinInt(int: Int32(clamping: $0)) },
            bitRate: tags.bitRateKbps.map { KotlinInt(int: Int32(clamping: $0)) },
            codec: tags.codec
        )
    }
}
