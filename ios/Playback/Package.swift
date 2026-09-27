// swift-tools-version: 5.9
// S2's iOS player engine (#588; design: docs/architecture/ios-port/phase-6-playback.md): the byte
// sources and FFmpeg pull decoder copied from Shuttle Podcasts, and `MusicPlaybackController`, the
// gapless two-item AVAudioEngine controller the Kotlin `EnginePlayerController` drives.
//
// Builds for iOS 17 and macOS 14. The macOS platform is only there so `swift test` runs the
// offline-rendering and decoder tests on the Mac without a simulator; nothing here touches
// AVAudioSession (the app owns the session, phase 6 step 7).
import PackageDescription

// FFmpeg is four dynamic xcframeworks, one per library, built by ios/scripts/build-ffmpeg.sh into
// Frameworks/ (gitignored; see README.md). Dynamic because FFmpeg is LGPL and a user must be able to
// replace it in the app. There is no fallback without them: a package that built without FFmpeg
// would give an app that links, runs and decodes nothing. `ios/scripts/build-framework.sh` and
// `ios/scripts/test.sh` run the build script first, which only copies from its cache once built.
let ffmpegLibraries = ["libavutil", "libswresample", "libavcodec", "libavformat"]

let package = Package(
    name: "S2Playback",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "S2Playback", targets: ["S2Playback"]),
        // The loopback HTTP origin and the fixtures, for the app's own tests.
        .library(name: "S2PlaybackTestSupport", targets: ["S2PlaybackTestSupport"]),
    ],
    targets: [
        .target(name: "S2Playback", dependencies: ["CS2StreamDecode"]),
        // The decode step in C, next to the FFmpeg API it calls. `<libavcodec/avcodec.h>` resolves
        // through the framework search path because each framework is named after its library.
        .target(
            name: "CS2StreamDecode",
            dependencies: ffmpegLibraries.map { .target(name: $0) }
        ),
        .target(
            name: "S2PlaybackTestSupport",
            resources: [.copy("Fixtures")]
        ),
        .testTarget(
            name: "S2PlaybackTests",
            dependencies: ["S2Playback", "S2PlaybackTestSupport"]
        ),
    ] + ffmpegLibraries.map { .binaryTarget(name: $0, path: "Frameworks/\($0).xcframework") }
)
