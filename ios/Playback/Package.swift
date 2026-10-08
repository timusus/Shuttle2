// swift-tools-version: 5.9
// S2's iOS player engine (#588): the HTTP byte
// source copied from Shuttle Podcasts, and `MusicPlaybackController`, the gapless two-item
// AVAudioEngine controller the Kotlin `EnginePlayerController` drives. Demux, decode, resampling and
// seeking are shuttle-playback's `PlaybackDecode` (#957).
//
// Builds for iOS 17 and macOS 14. The macOS platform is only there so `swift test` runs the
// offline-rendering and decoder tests on the Mac without a simulator; nothing here touches
// AVAudioSession (the app owns the session, phase 6 step 7).
import PackageDescription

let package = Package(
    name: "S2Playback",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "S2Playback", targets: ["S2Playback"]),
        // The local library's tag reader, on the same FFmpeg the engine decodes with.
        .library(name: "S2Tags", targets: ["S2Tags"]),
        // The loopback HTTP origin and the fixtures, for the app's own tests.
        .library(name: "S2PlaybackTestSupport", targets: ["S2PlaybackTestSupport"]),
    ],
    dependencies: [
        // Pre-1.0: pinned exactly and bumped deliberately. Its FFmpeg is one static xcframework,
        // committed in the package (LGPL notes in README.md).
        .package(url: "https://github.com/timusus/shuttle-playback.git", exact: "0.4.0"),
    ],
    targets: [
        .target(
            name: "S2Playback",
            dependencies: [.product(name: "PlaybackDecode", package: "shuttle-playback")]
        ),
        .target(name: "S2Tags", dependencies: ["CS2Tags"]),
        // The tag reader in C, next to the libavformat API it calls. It links the package's `FFmpeg`
        // product, the same static FFmpeg `PlaybackDecode` links, so the app carries one copy. A
        // binary target carries no linker settings, so the system libraries the static FFmpeg calls
        // are listed here, as the package's `CStreamDecode` lists them: zlib (ID3v2, MP4 `cmov`),
        // iconv (metadata conversion) and libavutil's VideoToolbox hardware context.
        .target(
            name: "CS2Tags",
            dependencies: [.product(name: "FFmpeg", package: "shuttle-playback")],
            linkerSettings: [
                .linkedLibrary("z"), .linkedLibrary("iconv"),
                .linkedFramework("CoreFoundation"), .linkedFramework("CoreMedia"),
                .linkedFramework("CoreVideo"), .linkedFramework("VideoToolbox"),
            ]
        ),
        .target(
            name: "S2PlaybackTestSupport",
            resources: [.copy("Fixtures")]
        ),
        .testTarget(
            name: "S2PlaybackTests",
            dependencies: [
                "S2Playback", "S2PlaybackTestSupport",
                .product(name: "PlaybackDecode", package: "shuttle-playback"),
            ]
        ),
        .testTarget(
            name: "S2TagsTests",
            dependencies: ["S2Tags", "S2PlaybackTestSupport"]
        ),
    ]
)
