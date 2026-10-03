import Foundation

/// The telemetry keys and release identity, read from Info.plist at runtime. The keys reach Info.plist through the
/// gitignored `Config/Telemetry.local.xcconfig`, which `ios/scripts/generate-telemetry-config.sh` writes from the
/// environment or `~/.config/s2-telemetry/ios.env`; a build without one has empty keys, and that SDK stays off.
struct TelemetryConfig: Equatable {
    var sentryDsn: String?
    var postHogApiKey: String?
    var postHogHost: String
    /// `com.simplecityapps.shuttle@<version>+<build>`, Android's shape; `upload-dsyms.sh` uploads under the same name.
    var releaseName: String
    var build: String
    /// Android's split: debug builds send too, as `development`.
    var environment: String
    /// Android's `build_type` super property, so debug builds' analytics can be filtered out: `debug` or `release`.
    var buildType: String

    static let defaultPostHogHost = "https://eu.i.posthog.com"

    /// The app's own, with no keys while it hosts S2Tests, so a test run reports nothing.
    static let main = TelemetryConfig(
        info: ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] == nil ? Bundle.main.infoDictionary ?? [:] : [:]
    )

    init(info: [String: Any]) {
        sentryDsn = Self.value(info["S2SentryDSN"])
        postHogApiKey = Self.value(info["S2PostHogAPIKey"])
        postHogHost = Self.value(info["S2PostHogHost"]) ?? Self.defaultPostHogHost
        let version = info["CFBundleShortVersionString"] as? String ?? "unknown"
        build = info["CFBundleVersion"] as? String ?? "unknown"
        releaseName = "com.simplecityapps.shuttle@\(version)+\(build)"
        #if DEBUG
        environment = "development"
        buildType = "debug"
        #else
        environment = "production"
        buildType = "release"
        #endif
    }

    /// A blank value, or a build setting that never expanded, is no key.
    private static func value(_ raw: Any?) -> String? {
        guard let text = (raw as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
              !text.isEmpty, !text.hasPrefix("$(") else { return nil }
        return text
    }
}
