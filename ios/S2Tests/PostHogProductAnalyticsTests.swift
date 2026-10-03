import PostHog
import Testing
@testable import S2

/// PostHog's config (#776): anonymous, on the given host, with lifecycle events and nothing captured automatically.
struct PostHogProductAnalyticsTests {
    @Test func configuresAnonymousLifecycleOnlyAnalytics() {
        let config = PostHogProductAnalytics.makeConfig(apiKey: "phc_test", host: "https://eu.i.posthog.com")

        #expect(config.host.absoluteString == "https://eu.i.posthog.com")
        #expect(config.personProfiles == .identifiedOnly)
        #expect(config.captureApplicationLifecycleEvents)
        #expect(!config.captureScreenViews)
        #expect(!config.captureElementInteractions)
        #expect(!config.sessionReplay)
        #expect(!config.surveys)
        #expect(!config.preloadFeatureFlags)
        #expect(!config.errorTrackingConfig.autoCapture)
    }
}
