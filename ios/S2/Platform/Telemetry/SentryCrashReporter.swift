import Foundation
import os
import Sentry
import Shared

/// Sentry crash, app hang and session reporting, the shared `IosCrashReporter`. Not started until the user allows
/// crash reporting (the shared `TelemetryConsentGate`), and closed again if they turn it off. Without a DSN it never
/// starts. Like Android's `SentryCrashReporting`: crashes, hangs (2 s) and sessions; no profiling, automatic or network
/// breadcrumbs, failed-request capture, screenshots, view hierarchy or user identity. Unlike Android, which sends
/// crashes only, it also samples app start traces at 5% (#776's plan). Every message, exception, breadcrumb, tag,
/// context and extra goes through the shared `TelemetryScrubber` first (`TelemetryScrub`).
final class SentryCrashReporter: NSObject, IosCrashReporter {
    private static let log = Logger(subsystem: "com.simplecityapps.shuttle2", category: "Telemetry")

    private let config: TelemetryConfig
    private let lock = NSLock()
    private var enabled = false
    private var crashHookInstalled = false

    init(config: TelemetryConfig) {
        self.config = config
    }

    func setEnabled(enabled: Bool) {
        guard let dsn = config.sentryDsn else { return }
        lock.lock()
        defer { lock.unlock() }
        guard enabled != self.enabled else { return }
        self.enabled = enabled
        if enabled {
            SentrySDK.start { [config] options in Self.configure(options, dsn: dsn, config: config) }
            installCrashHook()
            Self.log.notice("Sentry started")
        } else {
            SentrySDK.close()
            Self.log.notice("Sentry closed")
        }
    }

    func addBreadcrumb(category: String, message: String, isError: Bool) {
        guard SentrySDK.isEnabled else { return }
        let crumb = Breadcrumb(level: isError ? .error : .warning, category: category)
        crumb.message = message
        SentrySDK.addBreadcrumb(crumb)
    }

    static func configure(_ options: Options, dsn: String, config: TelemetryConfig) {
        options.dsn = dsn
        options.environment = config.environment
        options.releaseName = config.releaseName
        options.dist = config.build
        #if DEBUG
        options.debug = true
        #endif
        options.sendDefaultPii = false
        options.enableAutoSessionTracking = true
        options.enableCrashHandler = true
        options.enableWatchdogTerminationTracking = true
        options.appHangTimeoutInterval = 2

        // App start only: the standalone app start transaction, sampled at 5%; no other automatic tracing. iOS only on
        // purpose: Android sends crashes alone, with no tracing
        options.tracesSampleRate = 0.05
        options.enableAutoPerformanceTracing = true
        options.enableStandaloneAppStartTracing = true
        options.enableUIViewControllerTracing = false
        options.enableUserInteractionTracing = false
        options.enableNetworkTracking = false
        options.enableFileIOTracing = false
        options.enableDataSwizzling = false
        options.enableFileManagerSwizzling = false
        options.enableCoreDataTracing = false
        options.enableTimeToFullDisplayTracing = false
        options.enableGraphQLOperationTracking = false
        options.tracePropagationTargets = []

        // Nothing that could carry an address, a file or what's on screen. Breadcrumbs are our own Logger warnings and
        // errors (`BreadcrumbLogger`), not Sentry's automatic lifecycle, touch, navigation and system ones
        options.enableAutoBreadcrumbTracking = false
        options.enableNetworkBreadcrumbs = false
        options.enableCaptureFailedRequests = false
        options.attachScreenshot = false
        options.attachViewHierarchy = false
        options.reportAccessibilityIdentifier = false
        options.enableMetricKit = false
        options.enableLogs = false
        options.sessionReplay.sessionSampleRate = 0
        options.sessionReplay.onErrorSampleRate = 0
        // No profiling: `configureProfiling` stays nil

        options.beforeBreadcrumb = { TelemetryScrub.breadcrumb($0) }
        options.beforeSend = { event in
            KotlinCrashMarker.standard.isDuplicate(event) ? nil : TelemetryScrub.event(event)
        }
    }

    /// Routes uncaught Kotlin exceptions to Sentry before the runtime aborts (Shuttle Podcasts' `SentrySetup`): the
    /// Objective-C frame chain knows nothing about them, so Sentry would see a bare SIGABRT. The abort that follows still
    /// leaves Sentry's own crash report, sent on the next launch, so the hook leaves a `KotlinCrashMarker` and
    /// `beforeSend` drops that SIGABRT report: one event per crash, and the session still ends as crashed. Installed
    /// once; reports only while Sentry runs.
    private func installCrashHook() {
        guard !crashHookInstalled else { return }
        crashHookInstalled = true
        CrashHooksKt.installUnhandledExceptionHook { message, stackTrace, exceptionClass in
            guard SentrySDK.isEnabled else { return }
            SentrySDK.capture(event: KotlinCrashEvent.make(message: message, stackTrace: stackTrace, exceptionClass: exceptionClass))
            KotlinCrashMarker.standard.record()
            // The runtime aborts as soon as this returns; flushing gives the envelope a chance to leave
            SentrySDK.flush(timeout: 5)
        }
    }
}

/// The scrubber for Sentry's hooks: every free-text field through the shared `TelemetryScrubber` (URLs, hosts, IPs,
/// file paths, emails, `user=`/`token=` values), so nothing identifying leaves the device.
enum TelemetryScrub {
    static func text(_ text: String) -> String {
        IosTelemetryKt.scrubForTelemetry(text: text)
    }

    static func breadcrumb(_ crumb: Breadcrumb) -> Breadcrumb {
        crumb.message = crumb.message.map(text)
        // Key by key: the `data` setter is deprecated (Sentry 9)
        crumb.data?.forEach { key, value in
            if let string = value as? String { crumb.setData(value: text(string), key: key) }
        }
        return crumb
    }

    static func event(_ event: Event) -> Event {
        if let message = event.message {
            event.message = SentryMessage(formatted: text(message.formatted))
        }
        event.exceptions?.forEach { $0.value = $0.value.map(text) }
        event.breadcrumbs = event.breadcrumbs?.map(breadcrumb)
        event.extra = event.extra.map(values)
        event.tags = event.tags?.mapValues(text)
        event.context = event.context?.mapValues(values)
        if let request = event.request {
            request.url = request.url.map(text)
            request.queryString = nil
            request.cookies = nil
            request.headers = nil
        }
        return event
    }

    private static func values(_ data: [String: Any]) -> [String: Any] {
        data.mapValues(value)
    }

    private static func value(_ value: Any) -> Any {
        switch value {
        case let string as String: text(string)
        case let dictionary as [String: Any]: values(dictionary)
        case let array as [Any]: array.map(Self.value)
        default: value
        }
    }
}

/// Marks that a Kotlin crash went to Sentry just before the runtime aborted, so the SIGABRT crash report Sentry writes
/// for that same abort, and sends on the next launch, is dropped rather than filed as a second, stackless issue. The
/// marker is a file holding the crash time; it's spent on the first abort report within `window` of it.
struct KotlinCrashMarker {
    static let window: TimeInterval = 60
    static let standard = KotlinCrashMarker(
        url: FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("KotlinCrashSent")
    )

    let url: URL

    func record(at date: Date = Date()) {
        try? String(date.timeIntervalSince1970).write(to: url, atomically: true, encoding: .utf8)
    }

    /// True for an abort crash report from within `window` of a recorded Kotlin crash; that spends the marker. A stale
    /// marker is cleared by the next abort report without dropping it.
    func isDuplicate(_ event: Event) -> Bool {
        guard event.level == .fatal, event.tags?["kotlin_native"] != "true", Self.isAbort(event),
              let text = try? String(contentsOf: url, encoding: .utf8), let recorded = TimeInterval(text) else { return false }
        try? FileManager.default.removeItem(at: url)
        guard let crashed = event.timestamp?.timeIntervalSince1970 else { return false }
        return abs(crashed - recorded) <= Self.window
    }

    /// SIGABRT, as a signal or as the EXC_CRASH mach exception it arrives as on a device.
    private static func isAbort(_ event: Event) -> Bool {
        event.exceptions?.contains { exception in
            exception.type == "SIGABRT" || exception.type == "EXC_CRASH"
                || exception.mechanism?.meta?.signal?["name"] as? String == "SIGABRT"
        } ?? false
    }
}
