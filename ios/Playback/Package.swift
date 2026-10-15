// swift-tools-version: 5.9
// S2's playback glue over AudioPlaybackKit, whose `Player` the app plays through (#1020): the stream
// store and the server connection policy for its `PlaybackStreaming` source (#958), the EQ's DSP and
// the local library's tag reader. Decode is AudioPlaybackKit's `PlaybackDecode` (#957).
//
// Builds for iOS 17 and macOS 14. The macOS platform is only there so `swift test` runs on the Mac
// without a simulator.
import PackageDescription

let package = Package(
    name: "S2Playback",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "S2Playback", targets: ["S2Playback"]),
        // The local library's tag reader, on the same FFmpeg the player decodes with.
        .library(name: "S2Tags", targets: ["S2Tags"]),
        // The loopback HTTP origin, the fixtures and AudioPlaybackKit's player rig, for the app's own tests.
        .library(name: "S2PlaybackTestSupport", targets: ["S2PlaybackTestSupport"]),
    ],
    dependencies: [
        // Pre-1.0: pinned exactly and bumped deliberately. Its FFmpeg is one dynamic xcframework,
        // committed in the package (LGPL notes in README.md).
        .package(url: "https://github.com/timusus/AudioPlaybackKit.git", exact: "0.15.0"),
    ],
    targets: [
        .target(
            name: "S2Playback",
            dependencies: [
                .product(name: "PlaybackDecode", package: "AudioPlaybackKit"),
                .product(name: "PlaybackStreaming", package: "AudioPlaybackKit"),
                .product(name: "PlaybackPlayer", package: "AudioPlaybackKit"),
                .product(name: "PlaybackNowPlaying", package: "AudioPlaybackKit"),
                .product(name: "PlaybackRender", package: "AudioPlaybackKit"),
            ]
        ),
        .target(name: "S2Tags", dependencies: ["CS2Tags"]),
        // The tag reader in C, next to the libavformat API it calls. It links the package's `FFmpeg`
        // product, the same FFmpeg `PlaybackDecode` links, so the app carries one copy. The
        // framework is dynamic and links its own system libraries.
        .target(
            name: "CS2Tags",
            dependencies: [.product(name: "FFmpeg", package: "AudioPlaybackKit")]
        ),
        .target(
            name: "S2PlaybackTestSupport",
            dependencies: [
                .product(name: "PlaybackNowPlayingTestSupport", package: "AudioPlaybackKit"),
                .product(name: "PlaybackPlayerTestSupport", package: "AudioPlaybackKit"),
                .product(name: "PlaybackRenderTestSupport", package: "AudioPlaybackKit"),
            ],
            resources: [.copy("Fixtures")]
        ),
        .testTarget(
            name: "S2PlaybackTests",
            dependencies: [
                "S2Playback", "S2PlaybackTestSupport",
                .product(name: "PlaybackDecode", package: "AudioPlaybackKit"),
                .product(name: "PlaybackStreaming", package: "AudioPlaybackKit"),
                .product(name: "PlaybackStreamingTestSupport", package: "AudioPlaybackKit"),
                .product(name: "PlaybackPlayerTestSupport", package: "AudioPlaybackKit"),
            ]
        ),
        .testTarget(
            name: "S2TagsTests",
            dependencies: ["S2Tags", "S2PlaybackTestSupport"]
        ),
    ]
)
