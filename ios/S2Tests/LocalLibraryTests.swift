import Foundation
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

    private func library() -> LocalLibrary {
        LocalLibrary(defaults: defaults, documents: documents)
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

        let paths = Set(sut.audioFiles().map(\.path))

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

    @Test func aChangeIsSeenUntilTheNextImport() throws {
        defer { cleanUp() }
        try write(to: documents.appendingPathComponent("a.mp3"))
        let sut = library()
        #expect(sut.changedSinceLastImport())
        _ = sut.audioFiles()
        #expect(!sut.changedSinceLastImport())

        try write(to: documents.appendingPathComponent("b.mp3"))
        #expect(sut.changedSinceLastImport())
        _ = sut.audioFiles()
        try FileManager.default.removeItem(at: documents.appendingPathComponent("a.mp3"))
        #expect(sut.changedSinceLastImport())
    }

    // MARK: Tags

    @Test func aFilesTagsReachKotlin() throws {
        defer { cleanUp() }
        try write(Self.wav(info: ["INAM": "Hyperballad", "IART": "Björk", "IPRD": "Post", "ICRD": "1995", "ITRK": "3", "IGNR": "Electronic"]),
                  to: documents.appendingPathComponent("song.wav"))

        let tags = try #require(library().readTags(path: "s2local://documents/song.wav"))

        #expect(tags.title == "Hyperballad")
        #expect(tags.artists == ["Björk"])
        #expect(tags.album == "Post")
        #expect(tags.year?.intValue == 1995)
        #expect(tags.track?.intValue == 3)
        #expect(tags.genres == ["Electronic"])
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
