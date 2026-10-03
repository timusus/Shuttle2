import Sentry
import Testing
@testable import S2

/// Sentry's options and its scrubber (#776): what's on and off, and that nothing identifying leaves in an event or a
/// breadcrumb. The scrubbing itself is the shared `TelemetryScrubber` (`TelemetryScrubberTest`); these check every
/// field reaches it.
struct SentryCrashReporterTests {
    private let config = TelemetryConfig(info: ["CFBundleShortVersionString": "1.2", "CFBundleVersion": "34"])

    @Test func configuresCrashesHangsSessionsAndAppStartOnly() {
        let options = Options()
        SentryCrashReporter.configure(options, dsn: "https://key@o1.ingest.sentry.io/2", config: config)

        #expect(options.releaseName == "com.simplecityapps.shuttle@1.2+34")
        #expect(options.dist == "34")
        #expect(options.sendDefaultPii == false)
        #expect(options.enableCrashHandler)
        #expect(options.enableAutoSessionTracking)
        #expect(options.appHangTimeoutInterval == 2)
        #expect(options.tracesSampleRate == 0.05)
        #expect(options.enableStandaloneAppStartTracing)
        #expect(!options.enableUIViewControllerTracing)
        #expect(!options.enableNetworkTracking)
        #expect(!options.enableFileIOTracing)
        #expect(!options.enableUserInteractionTracing)
        #expect(!options.enableAutoBreadcrumbTracking)
        #expect(!options.enableNetworkBreadcrumbs)
        #expect(!options.enableCaptureFailedRequests)
        #expect(!options.attachScreenshot)
        #expect(!options.attachViewHierarchy)
        #expect(options.sessionReplay.sessionSampleRate == 0)
        #expect(options.sessionReplay.onErrorSampleRate == 0)
        #expect(options.configureProfiling == nil)
        #expect(options.beforeSend != nil)
        #expect(options.beforeBreadcrumb != nil)
    }

    @Test func aBreadcrumbsMessageAndDataAreScrubbed() {
        let crumb = Breadcrumb(level: .error, category: "JellyfinAuth")
        crumb.message = "GET https://music.example.com/Items?api_key=abc failed"
        crumb.setData(value: "nas.local:8096", key: "host")
        crumb.setData(value: 3, key: "count")

        let scrubbed = TelemetryScrub.breadcrumb(crumb)

        #expect(scrubbed.message == "GET <url> failed")
        #expect(scrubbed.data?["host"] as? String == "<host>")
        #expect(scrubbed.data?["count"] as? Int == 3)
    }

    @Test func anEventsMessageExceptionsBreadcrumbsExtrasAndRequestAreScrubbed() {
        let event = Event(level: .error)
        event.message = SentryMessage(formatted: "Couldn't open /var/mobile/Containers/Data/Application/X/Documents/a.flac")
        event.exceptions = [Exception(value: "Failed to connect to /192.168.1.20:8096", type: "IOException")]
        let crumb = Breadcrumb(level: .warning, category: "Sync")
        crumb.message = "retry token=s3cr3t"
        event.breadcrumbs = [crumb]
        event.extra = ["user": "signed in as sam@example.com"]
        let request = SentryRequest()
        request.url = "https://jellyfin.example.com/Users"
        request.queryString = "api_key=abc"
        event.request = request

        let scrubbed = TelemetryScrub.event(event)

        #expect(scrubbed.message?.formatted == "Couldn't open <path>")
        #expect(scrubbed.exceptions?.first?.value == "Failed to connect to /<ip>")
        #expect(scrubbed.breadcrumbs?.first?.message == "retry token=<redacted>")
        #expect(scrubbed.extra?["user"] as? String == "signed in as <email>")
        #expect(scrubbed.request?.url == "<url>")
        #expect(scrubbed.request?.queryString == nil)
    }

    @Test func anEventsTagsAndContextsAreScrubbed() {
        let event = Event(level: .error)
        event.tags = ["server": "Tims-NAS.local", "kotlin_native": "true"]
        event.context = ["sync": ["host": "homeserver:8096", "songs": 12, "nested": ["url": "https://a.example.com/x"]]]

        let scrubbed = TelemetryScrub.event(event)

        #expect(scrubbed.tags == ["server": "<host>", "kotlin_native": "true"])
        #expect(scrubbed.context?["sync"]?["host"] as? String == "<host>")
        #expect(scrubbed.context?["sync"]?["songs"] as? Int == 12)
        #expect((scrubbed.context?["sync"]?["nested"] as? [String: Any])?["url"] as? String == "<url>")
    }

    // MARK: - The SIGABRT that follows a Kotlin crash

    private func marker() -> KotlinCrashMarker {
        KotlinCrashMarker(url: FileManager.default.temporaryDirectory.appendingPathComponent("KotlinCrashSent-\(UUID().uuidString)"))
    }

    private func abortReport(at date: Date, signalOnly: Bool = false) -> Event {
        let event = Event(level: .fatal)
        event.timestamp = date
        let exception = Exception(value: "Signal 6, Code 0", type: signalOnly ? "SIGABRT" : "EXC_CRASH")
        event.exceptions = [exception]
        return event
    }

    @Test func theAbortReportAfterAKotlinCrashIsDroppedOnce() {
        let marker = marker()
        let crashed = Date()
        marker.record(at: crashed)

        #expect(marker.isDuplicate(abortReport(at: crashed.addingTimeInterval(0.2))))
        #expect(!marker.isDuplicate(abortReport(at: crashed.addingTimeInterval(0.2), signalOnly: true)))
    }

    @Test func theKotlinEventItselfAndOtherCrashesAreKept() {
        let marker = marker()
        let crashed = Date()
        marker.record(at: crashed)

        let kotlin = KotlinCrashEvent.make(message: "boom", stackTrace: "", exceptionClass: "kotlin.IllegalStateException")
        kotlin.timestamp = crashed
        #expect(!marker.isDuplicate(kotlin))

        let segv = Event(level: .fatal)
        segv.timestamp = crashed
        segv.exceptions = [Exception(value: "Signal 11", type: "SIGSEGV")]
        #expect(!marker.isDuplicate(segv))

        #expect(marker.isDuplicate(abortReport(at: crashed, signalOnly: true)))
    }

    @Test func anAbortLongAfterTheMarkerIsKeptAndClearsIt() {
        let marker = marker()
        let crashed = Date()
        marker.record(at: crashed)

        #expect(!marker.isDuplicate(abortReport(at: crashed.addingTimeInterval(3600))))
        #expect(!marker.isDuplicate(abortReport(at: crashed)))
    }

    @Test func noMarkerKeepsEveryAbort() {
        #expect(!marker().isDuplicate(abortReport(at: Date())))
    }
}
