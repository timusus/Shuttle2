import Foundation
import S2PlaybackTestSupport
import Shared
import Testing
@testable import S2

/// This device's music (#590): picked folders' bookmarks across launches, the listing and its paths, and a file's tags
/// as the Kotlin provider gets them. Each test has its own defaults suite and its own Documents in the temp folder.
@MainActor
struct LocalLibraryTests {
    private let suite = "LocalLibraryTests.\(UUID().uuidString)"
    private let root = FileManager.default.temporaryDirectory.appendingPathComponent("LocalLibraryTests-\(UUID().uuidString)")
    private var defaults: UserDefaults { UserDefaults(suiteName: suite)! }
    private var documents: URL { root.appendingPathComponent("Documents") }

    /// Documents is there, as it always is on a device: a missing one is a folder that couldn't be read.
    private func library() -> LocalLibrary {
        try? FileManager.default.createDirectory(at: documents, withIntermediateDirectories: true)
        return LocalLibrary(defaults: defaults, documents: documents)
    }

    /// A library whose security scopes never open and whose container is elsewhere, as for a folder outside the app.
    private func libraryWithoutScopes() -> LocalLibrary {
        LocalLibrary(defaults: defaults, documents: documents, container: URL(fileURLWithPath: "/nowhere"), openScope: { _ in false })
    }

    /// A folder outside Documents, as the Files picker would return.
    private func folder(_ name: String) throws -> URL {
        let url = root.appendingPathComponent(name, isDirectory: true)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    private func write(_ data: Data = Data([0]), to url: URL) throws {
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try data.write(to: url)
    }

    private func cleanUp() {
        UserDefaults().removePersistentDomain(forName: suite)
        try? FileManager.default.removeItem(at: root)
    }

    // MARK: Folders

    @Test func aPickedFolderIsKeptAcrossLaunches() throws {
        defer { cleanUp() }
        let music = try folder("Music")
        let first = library()
        #expect(first.addFolder(url: first.stage(music)))
        let added = try #require(first.folders().first)

        let relaunched = library().folders()
        #expect(relaunched.count == 1)
        #expect(relaunched.first?.id == added.id)
        #expect(relaunched.first?.name == "Music")
        #expect(relaunched.first?.hasAccess == true)
    }

    @Test func aFolderThatsGoneIsOutOfReachButKept() throws {
        defer { cleanUp() }
        let music = try folder("Music")
        let first = library()
        #expect(first.addFolder(url: first.stage(music)))
        try FileManager.default.removeItem(at: music)

        let relaunched = library().folders()
        #expect(relaunched.count == 1)
        #expect(relaunched.first?.hasAccess == false)
    }

    @Test func pickingAFolderAgainKeepsItsId() throws {
        defer { cleanUp() }
        let music = try folder("Music")
        let sut = library()
        #expect(sut.addFolder(url: sut.stage(music)))
        let id = try #require(sut.folders().first?.id)
        #expect(sut.addFolder(url: sut.stage(music)))
        #expect(sut.folders().map(\.id) == [id])
    }

    @Test func aRemovedFolderIsForgotten() throws {
        defer { cleanUp() }
        let sut = library()
        #expect(sut.addFolder(url: sut.stage(try folder("Music"))))
        sut.removeFolder(id: try #require(sut.folders().first?.id))
        #expect(sut.folders().isEmpty)
        #expect(library().folders().isEmpty)
    }

    @Test func onlyFileURLsAreFolders() {
        defer { cleanUp() }
        #expect(!library().addFolder(url: "https://example.com/Music"))
    }

    // MARK: Files

    @Test func audioFilesAreListedUnderTheirFolder() throws {
        defer { cleanUp() }
        try write(to: documents.appendingPathComponent("a.mp3"))
        try write(to: documents.appendingPathComponent("Björk/03 Hyperballad.FLAC"))
        try write(to: documents.appendingPathComponent(".hidden/c.mp3"))
        try write(to: documents.appendingPathComponent("._a.mp3"))
        try write(to: documents.appendingPathComponent("notes.txt"))
        let music = try folder("Music")
        try write(to: music.appendingPathComponent("x.m4a"))
        let sut = library()
        #expect(sut.addFolder(url: sut.stage(music)))
        let id = try #require(sut.folders().first?.id)

        let paths = Set(sut.audioFiles().files.map(\.path))

        #expect(paths == ["s2local://documents/a.mp3", "s2local://documents/Björk/03 Hyperballad.FLAC", "s2local://\(id)/x.m4a"])
    }

    @Test func aSongsPathResolvesToItsFile() throws {
        defer { cleanUp() }
        let file = documents.appendingPathComponent("Björk/03 Hyperballad.flac")
        try write(to: file)
        let sut = library()

        let url = try #require(sut.fileUrl(path: "s2local://documents/Björk/03 Hyperballad.flac").flatMap(URL.init(string:)))

        #expect(url.isFileURL)
        #expect(url.resolvingSymlinksInPath().path == file.resolvingSymlinksInPath().path)
        #expect(sut.fileUrl(path: "s2local://unknown/a.mp3") == nil)
        #expect(sut.fileUrl(path: "https://example.com/a.mp3") == nil)
    }

    @Test func aChangeIsSeenUntilTheNextImportIsStored() throws {
        defer { cleanUp() }
        try write(to: documents.appendingPathComponent("a.mp3"))
        let sut = library()
        #expect(sut.changedSinceLastImport())
        let listing = sut.audioFiles()
        // Listed, but the import was interrupted before storing it: still changed, so it runs again
        #expect(sut.changedSinceLastImport())
        sut.imported(listing: listing)
        #expect(!sut.changedSinceLastImport())

        try write(to: documents.appendingPathComponent("b.mp3"))
        #expect(sut.changedSinceLastImport())
        sut.imported(listing: sut.audioFiles())
        try FileManager.default.removeItem(at: documents.appendingPathComponent("a.mp3"))
        #expect(sut.changedSinceLastImport())
    }

    @Test func aFolderOutOfReachIsUnread() throws {
        defer { cleanUp() }
        let music = try folder("Music")
        try write(to: music.appendingPathComponent("x.m4a"))
        let first = library()
        #expect(first.addFolder(url: first.stage(music)))
        let id = try #require(first.folders().first?.id)
        try FileManager.default.removeItem(at: music)

        let listing = library().audioFiles()

        #expect(listing.unread == [id])
        #expect(listing.folders.contains(id))
        #expect(listing.files.isEmpty)
    }

    @Test func aFolderWhoseScopeWontOpenIsOutOfReach() throws {
        defer { cleanUp() }
        let music = try folder("Music")
        try write(to: music.appendingPathComponent("x.m4a"))
        let first = library()
        #expect(first.addFolder(url: first.stage(music)))
        let id = try #require(first.folders().first?.id)

        let relaunched = libraryWithoutScopes()

        #expect(relaunched.folders().first?.hasAccess == false)
        #expect(relaunched.audioFiles().unread == [id])
        #expect(!libraryWithoutScopes().addFolder(url: try folder("Other").absoluteString))
    }

    @Test func anUnreadableFolderIsUnread() throws {
        defer { cleanUp() }
        let music = try folder("Music")
        try write(to: music.appendingPathComponent("x.m4a"))
        let sut = library()
        #expect(sut.addFolder(url: sut.stage(music)))
        let id = try #require(sut.folders().first?.id)
        try FileManager.default.setAttributes([.posixPermissions: 0o000], ofItemAtPath: music.path)
        defer { try? FileManager.default.setAttributes([.posixPermissions: 0o755], ofItemAtPath: music.path) }

        let listing = sut.audioFiles()

        #expect(listing.unread == [id])
        #expect(listing.files.isEmpty)
    }

    @Test func anOffloadedFileIsListedUnderItsOwnName() throws {
        defer { cleanUp() }
        try write(to: documents.appendingPathComponent("Post/.03 Hyperballad.flac.icloud"))
        try write(to: documents.appendingPathComponent(".a.mp3.icloud"))
        try write(to: documents.appendingPathComponent(".notes.txt.icloud"))
        try write(to: documents.appendingPathComponent(".DS_Store"))

        let listing = library().audioFiles()

        #expect(Set(listing.offloaded) == ["s2local://documents/Post/03 Hyperballad.flac", "s2local://documents/a.mp3"])
        #expect(listing.files.isEmpty)
        #expect(listing.unread.isEmpty)
    }

    // MARK: Overlapping folders

    @Test func pickingDocumentsOrAFolderInsideItAddsNothing() throws {
        defer { cleanUp() }
        try write(to: documents.appendingPathComponent("Rock/a.mp3"))
        let sut = library()

        #expect(sut.addFolder(url: sut.stage(documents)))
        #expect(sut.addFolder(url: sut.stage(documents.appendingPathComponent("Rock", isDirectory: true))))

        #expect(sut.folders().isEmpty)
        #expect(sut.audioFiles().files.map(\.path) == ["s2local://documents/Rock/a.mp3"])
    }

    @Test func pickingAFolderInsideAPickedOneAddsNothing() throws {
        defer { cleanUp() }
        let music = try folder("Music")
        try write(to: music.appendingPathComponent("Rock/a.mp3"))
        let sut = library()
        #expect(sut.addFolder(url: sut.stage(music)))
        let id = try #require(sut.folders().first?.id)

        #expect(sut.addFolder(url: sut.stage(music.appendingPathComponent("Rock", isDirectory: true))))

        #expect(sut.folders().map(\.id) == [id])
        #expect(sut.audioFiles().files.map(\.path) == ["s2local://\(id)/Rock/a.mp3"])
    }

    @Test func aFolderPickedAroundOnesThereLeavesThemTheirFiles() throws {
        defer { cleanUp() }
        let music = try folder("Music")
        try write(to: music.appendingPathComponent("Rock/a.mp3"))
        try write(to: music.appendingPathComponent("b.mp3"))
        try write(to: documents.appendingPathComponent("c.mp3"))
        let sut = library()
        let rock = music.appendingPathComponent("Rock", isDirectory: true)
        #expect(sut.addFolder(url: sut.stage(rock)))
        let rockID = try #require(sut.folders().first?.id)

        // The parent of both the picked folder and Documents
        #expect(sut.addFolder(url: sut.stage(root)))
        let rootID = try #require(sut.folders().last?.id)
        try write(to: root.appendingPathComponent("d.mp3"))

        let paths = sut.audioFiles().files.map(\.path).sorted()

        #expect(paths == [
            "s2local://\(rootID)/Music/b.mp3",
            "s2local://\(rootID)/d.mp3",
            "s2local://\(rockID)/a.mp3",
            "s2local://documents/c.mp3",
        ].sorted())
    }

    // MARK: Tags

    @Test func aFilesTagsReachKotlin() throws {
        defer { cleanUp() }
        try write(Self.wav(info: ["INAM": "Hyperballad", "IART": "Björk", "IPRD": "Post", "ICRD": "1995", "ITRK": "3", "IGNR": "Electronic"]),
                  to: documents.appendingPathComponent("song.wav"))

        let tags = try #require(library().readTags(path: "s2local://documents/song.wav"))

        // Raw, as libavformat names a RIFF INFO chunk's; the Kotlin provider maps them (FfmpegTagsTest)
        let values = Dictionary(tags.tags.map { ($0.key, $0.value) }, uniquingKeysWith: { first, _ in first })
        #expect(values["title"] == "Hyperballad")
        #expect(values["artist"] == "Björk")
        #expect(values["album"] == "Post")
        #expect(values["date"] == "1995")
        #expect(values["track"] == "3")
        #expect(values["genre"] == "Electronic")
        #expect(abs((tags.durationMs?.int64Value ?? 0) - 1000) <= 20)
        #expect(tags.sampleRate?.intValue == 8000)
        #expect(tags.channelCount?.intValue == 1)
    }

    @Test func aFileThatIsntAudioHasNoTags() throws {
        defer { cleanUp() }
        try write(Data("not audio".utf8), to: documents.appendingPathComponent("broken.mp3"))
        #expect(library().readTags(path: "s2local://documents/broken.mp3") == nil)
    }

    @Test func artworkFallsBackToAnImageBesideTheFile() throws {
        defer { cleanUp() }
        let cover = Data([0xFF, 0xD8, 0xFF, 0xE0])
        try write(Self.wav(info: [:]), to: documents.appendingPathComponent("Post/03 Hyperballad.wav"))
        try write(cover, to: documents.appendingPathComponent("Post/Cover.JPG"))
        let sut = library()

        // As Kotlin's ArtworkUrls writes it: each segment percent-encoded.
        let url = try #require(URL(string: "s2local://documents/Post/03%20Hyperballad.wav"))

        #expect(sut.artwork(forArtworkURL: url) == cover)
        #expect(sut.artwork(forArtworkURL: try #require(URL(string: "s2local://documents/Post/missing.wav"))) == nil)
    }

    @Test func aPickedFoldersArtworkUrlSurvivesNormalising() throws {
        defer { cleanUp() }
        let cover = Data([0xFF, 0xD8, 0xFF, 0xE0])
        let music = try folder("Music")
        try write(Self.wav(info: [:]), to: music.appendingPathComponent("Post/03 Hyperballad.wav"))
        try write(cover, to: music.appendingPathComponent("Post/cover.jpg"))
        let sut = library()
        #expect(sut.addFolder(url: sut.stage(music)))
        let id = try #require(sut.folders().first?.id)
        #expect(UUID(uuidString: id) != nil)
        #expect(id == id.lowercased())

        for host in [id, id.uppercased()] {
            let url = try #require(URL(string: "s2local://\(host)/Post/03%20Hyperballad.wav"))
            #expect(sut.artwork(forArtworkURL: url) == cover)
        }
    }

    @Test func aFolderSavedWithAnUppercaseIdFindsItsArtworkFromALowercasedHost() throws {
        defer { cleanUp() }
        let cover = Data([0xFF, 0xD8, 0xFF, 0xE0])
        let music = try folder("Music")
        try write(Self.wav(info: [:]), to: music.appendingPathComponent("song.wav"))
        try write(cover, to: music.appendingPathComponent("folder.png"))
        let id = UUID().uuidString
        let bookmark = try music.bookmarkData(options: [], includingResourceValuesForKeys: nil, relativeTo: nil)
        let saved = [LocalLibrary.Folder(id: id, name: "Music", path: music.path, bookmark: bookmark)]
        defaults.set(try JSONEncoder().encode(saved), forKey: "local_library_folders")

        let url = try #require(URL(string: "s2local://\(id.lowercased())/song.wav"))

        #expect(library().artwork(forArtworkURL: url) == cover)
    }

    /// The PNG cover embedded in each `S2PlaybackTestSupport` fixture: ID3 APIC, FLAC PICTURE and MP4 covr.
    private static let png: [UInt8] = [0x89, 0x50, 0x4E, 0x47]

    @Test(arguments: [("tagged", "mp3"), ("tagged", "flac"), ("tagged-alac", "m4a")])
    func aFilesEmbeddedPictureComesBeforeAnImageBesideIt(name: String, ext: String) throws {
        defer { cleanUp() }
        let fixture = try #require(LoopbackMediaServer.fixtureURL(name, withExtension: ext))
        try write(try Data(contentsOf: fixture), to: documents.appendingPathComponent("Album/01.\(ext)"))
        try write(Data([0xFF, 0xD8, 0xFF, 0xE0]), to: documents.appendingPathComponent("Album/cover.jpg"))

        let artwork = try #require(library().artwork(forSongPath: "s2local://documents/Album/01.\(ext)"))

        #expect(Array(artwork.prefix(4)) == Self.png)
    }

    @Test func aFileWithoutAPictureFallsBackToTheFolderImage() throws {
        defer { cleanUp() }
        let cover = Data([0xFF, 0xD8, 0xFF, 0xE0])
        let untagged = try #require(LoopbackMediaServer.fixtureURL("tagged", withExtension: "opus"))
        try write(try Data(contentsOf: untagged), to: documents.appendingPathComponent("Album/01.opus"))
        try write(cover, to: documents.appendingPathComponent("Album/FRONT.jpeg"))

        #expect(library().artwork(forSongPath: "s2local://documents/Album/01.opus") == cover)
    }

    @Test func folderImagesAreMatchedInNameOrderWithAnyCase() throws {
        defer { cleanUp() }
        let album = documents.appendingPathComponent("Album")
        try write(Self.wav(info: [:]), to: album.appendingPathComponent("01.wav"))
        try write(Data("back".utf8), to: album.appendingPathComponent("back.jpg"))
        try write(Data("front".utf8), to: album.appendingPathComponent("Front.PNG"))
        try write(Data("folder".utf8), to: album.appendingPathComponent("Folder.jpg"))
        try write(Data("cover".utf8), to: album.appendingPathComponent("COVER.png"))

        #expect(library().artwork(forSongPath: "s2local://documents/Album/01.wav") == Data("cover".utf8))

        try FileManager.default.removeItem(at: album.appendingPathComponent("COVER.png"))
        #expect(library().artwork(forSongPath: "s2local://documents/Album/01.wav") == Data("folder".utf8))

        try FileManager.default.removeItem(at: album.appendingPathComponent("Folder.jpg"))
        #expect(library().artwork(forSongPath: "s2local://documents/Album/01.wav") == Data("front".utf8))

        // Not one of the names
        try FileManager.default.removeItem(at: album.appendingPathComponent("Front.PNG"))
        #expect(library().artwork(forSongPath: "s2local://documents/Album/01.wav") == nil)
    }

    @Test func artworkIsReadOnceUntilTheNextListing() throws {
        defer { cleanUp() }
        let album = documents.appendingPathComponent("Album")
        try write(Self.wav(info: [:]), to: album.appendingPathComponent("01.wav"))
        try write(Self.wav(info: [:]), to: album.appendingPathComponent("02.wav"))
        try write(Data("old".utf8), to: album.appendingPathComponent("cover.jpg"))
        let sut = library()
        #expect(sut.artwork(forSongPath: "s2local://documents/Album/01.wav") == Data("old".utf8))

        try write(Data("new".utf8), to: album.appendingPathComponent("cover.jpg"))
        // The same song, and its album's next, without reading the folder again
        #expect(sut.artwork(forSongPath: "s2local://documents/Album/01.wav") == Data("old".utf8))
        #expect(sut.artwork(forSongPath: "s2local://documents/Album/02.wav") == Data("old".utf8))

        _ = sut.listAudioFiles()
        #expect(sut.artwork(forSongPath: "s2local://documents/Album/01.wav") == Data("new".utf8))
    }

    /// One second of 8 kHz mono 16-bit silence, with [info] as its RIFF INFO tags.
    static func wav(info: [String: String]) -> Data {
        func chunk(_ id: String, _ body: Data) -> Data {
            var data = Data(id.utf8)
            withUnsafeBytes(of: UInt32(body.count).littleEndian) { data.append(contentsOf: $0) }
            data.append(body)
            if body.count % 2 == 1 { data.append(0) }
            return data
        }
        func le<T: FixedWidthInteger>(_ value: T) -> Data { withUnsafeBytes(of: value.littleEndian) { Data($0) } }

        let format = le(UInt16(1)) + le(UInt16(1)) + le(UInt32(8000)) + le(UInt32(16000)) + le(UInt16(2)) + le(UInt16(16))
        var list = Data("INFO".utf8)
        for (key, value) in info.sorted(by: { $0.key < $1.key }) {
            list.append(chunk(key, Data(value.utf8) + Data([0])))
        }
        var body = Data("WAVE".utf8)
        body.append(chunk("fmt ", format))
        if !info.isEmpty { body.append(chunk("LIST", list)) }
        body.append(chunk("data", Data(count: 16000)))
        return chunk("RIFF", body)
    }
}
