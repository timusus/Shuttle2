import Testing
@testable import S2

/// The telemetry keys from Info.plist (#776): a blank or unexpanded key is no key, so that SDK stays off.
struct TelemetryConfigTests {
    @Test func readsTheKeysAndTheReleaseName() {
        let config = TelemetryConfig(info: [
            "S2SentryDSN": "https://key@o1.ingest.sentry.io/2",
            "S2PostHogAPIKey": "phc_test",
            "S2PostHogHost": "https://eu.i.posthog.com",
            "CFBundleShortVersionString": "2026.10.04",
            "CFBundleVersion": "7",
        ])

        #expect(config.sentryDsn == "https://key@o1.ingest.sentry.io/2")
        #expect(config.postHogApiKey == "phc_test")
        #expect(config.postHogHost == "https://eu.i.posthog.com")
        #expect(config.releaseName == "com.simplecityapps.shuttle@2026.10.04+7")
        #expect(config.environment == "development")
        #expect(config.buildType == "debug")
    }

    @Test func aBlankOrUnexpandedKeyIsNoKey() {
        let config = TelemetryConfig(info: ["S2SentryDSN": "  ", "S2PostHogAPIKey": "$(S2_POSTHOG_API_KEY)", "S2PostHogHost": ""])

        #expect(config.sentryDsn == nil)
        #expect(config.postHogApiKey == nil)
        #expect(config.postHogHost == TelemetryConfig.defaultPostHogHost)
    }
}
