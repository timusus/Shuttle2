import Foundation
import os
import PostHog
import Shared

/// PostHog product analytics, the shared `IosProductAnalytics`. Set up the first time the user allows analytics (the
/// shared `TelemetryConsentGate`), then opted in and out as they change their mind; events are dropped while opted
/// out. Without an API key it never sets up. As on Android (`PostHogAnalytics`): the EU host, anonymous (person
/// profiles for identified users only, and nothing ever identifies), app lifecycle events, and no screen views,
/// autocapture, session replay, surveys or feature flags.
final class PostHogProductAnalytics: NSObject, IosProductAnalytics {
    private static let log = Logger(subsystem: "com.simplecityapps.shuttle2", category: "Telemetry")

    private let config: TelemetryConfig
    private let lock = NSLock()
    private var enabled = false
    private var setUp = false
    /// The super properties, registered with PostHog once it's set up. `build_type` is the app's own, as on Android;
    /// the rest come from the shared `IosTelemetryStartup`.
    private(set) var superProperties: [String: Any]

    init(config: TelemetryConfig) {
        self.config = config
        superProperties = ["build_type": config.buildType]
    }

    func setEnabled(enabled: Bool) {
        guard let apiKey = config.postHogApiKey else { return }
        lock.lock()
        defer { lock.unlock() }
        guard enabled != self.enabled else { return }
        self.enabled = enabled
        if enabled {
            if !setUp {
                PostHogSDK.shared.setup(Self.makeConfig(apiKey: apiKey, host: config.postHogHost))
                if !superProperties.isEmpty { PostHogSDK.shared.register(superProperties) }
                setUp = true
                Self.log.notice("PostHog set up")
            }
            PostHogSDK.shared.optIn()
            Self.log.notice("PostHog opted in")
        } else if setUp {
            PostHogSDK.shared.optOut()
            Self.log.notice("PostHog opted out")
        }
    }

    func capture(event: String, properties: [String: Any]) {
        lock.lock()
        let sending = enabled && setUp
        lock.unlock()
        guard sending else { return }
        PostHogSDK.shared.capture(event, properties: properties)
    }

    func register(properties: [String: Any]) {
        lock.lock()
        defer { lock.unlock() }
        superProperties.merge(properties) { _, new in new }
        if setUp { PostHogSDK.shared.register(properties) }
    }

    static func makeConfig(apiKey: String, host: String) -> PostHogConfig {
        let config = PostHogConfig(projectToken: apiKey, host: host)
        config.captureApplicationLifecycleEvents = true
        config.captureScreenViews = false
        config.captureElementInteractions = false
        config.sessionReplay = false
        config.surveys = false
        config.preloadFeatureFlags = false
        config.errorTrackingConfig.autoCapture = false
        // Nothing ever calls identify(), so no person profile is made: PostHog stays on its own anonymous device id
        config.personProfiles = .identifiedOnly
        #if DEBUG
        config.debug = true
        #endif
        return config
    }
}
