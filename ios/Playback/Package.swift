// swift-tools-version: 5.9
// S2's iOS player engine (#588; design: docs/architecture/ios-port/phase-6-playback.md): the byte
// sources and FFmpeg pull decoder copied from Shuttle Podcasts, and `MusicPlaybackController`, the
// gapless two-item AVAudioEngine controller the Kotlin `EnginePlayerController` drives.
//
// Builds for iOS 17 and macOS 14. The macOS platform is only there so `swift test` runs the
// offline-rendering and decoder tests on the Mac without a simulator; nothing here touches
// AVAudioSession (the app owns the session, phase 6 step 7).
import Foundation
import PackageDescription

// The FFmpeg xcframework is a build artefact (scripts/build-ffmpeg.sh), gitignored. The package
// must still load and test without it, so the binary target and the C decoder that calls it only
// exist when the artefact is present; `FFmpegStreamDecoder` is `#if canImport(CS2StreamDecode)`.
let ffmpegXCFramework = "Frameworks/FFmpeg.xcframework"
let hasFFmpeg = FileManager.default.fileExists(
    atPath: URL(fileURLWithPath: #filePath).deletingLastPathComponent()
        .appendingPathComponent(ffmpegXCFramework).path
)

var playbackDependencies: [Target.Dependency] = []
var ffmpegTargets: [Target] = []
if hasFFmpeg {
    ffmpegTargets = [
        .binaryTarget(name: "CFFmpeg", path: ffmpegXCFramework),
        // The decode step in C, next to the FFmpeg API it calls.
        .target(
            name: "CS2StreamDecode",
            dependencies: ["CFFmpeg"],
            // libavformat's ID3v2, MP4 `cmov` and matroska paths call zlib; metadata conversion
            // calls iconv. Both ship with the system.
            linkerSettings: [.linkedLibrary("z"), .linkedLibrary("iconv")]
        ),
    ]
    playbackDependencies.append("CS2StreamDecode")
}

let package = Package(
    name: "S2Playback",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "S2Playback", targets: ["S2Playback"]),
        // The loopback HTTP origin and the fixtures, for the app's own tests.
        .library(name: "S2PlaybackTestSupport", targets: ["S2PlaybackTestSupport"]),
    ],
    targets: [
        .target(name: "S2Playback", dependencies: playbackDependencies),
        .target(
            name: "S2PlaybackTestSupport",
            resources: [.copy("Fixtures")]
        ),
        .testTarget(
            name: "S2PlaybackTests",
            dependencies: ["S2Playback", "S2PlaybackTestSupport"]
        ),
    ] + ffmpegTargets
)
