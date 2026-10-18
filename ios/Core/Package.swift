// swift-tools-version: 5.9
// App logic with no Kotlin, UIKit or SwiftUI behind it, so its tests run with `swift test` on the Mac instead of
// inside the app host. A type moves here with its tests when it needs none of those.
//
// The macOS platform is only there so `swift test` runs without a simulator.
import PackageDescription

let package = Package(
    name: "S2Core",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "S2Core", targets: ["S2Core"]),
    ],
    targets: [
        .target(name: "S2Core"),
        .testTarget(name: "S2CoreTests", dependencies: ["S2Core"]),
    ]
)
