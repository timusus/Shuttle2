import Foundation
import os
import Shared

/// Where Kotlin's `Logger` lands: the app's subsystem, the Kotlin tag as the category. The message is interpolated
/// into a literal format string here, in the binary's `__oslogstring` section, so persisted entries render in
/// `log show` (a format string handed over from Kotlin data reads `<compose failure>`). `privacy: .public` keeps the
/// message from being redacted outside the debugger.
final class KotlinLogSink: NativeLogSink, @unchecked Sendable {
    private static let subsystem = "com.simplecityapps.shuttle2"

    private let lock = NSLock()
    private var loggers: [String: Logger] = [:]

    func log(level: NativeLogLevel, category: String, message: String) {
        let logger = logger(for: category)
        switch level {
        case .debug: logger.debug("\(message, privacy: .public)")
        case .info: logger.info("\(message, privacy: .public)")
        case .warn: logger.notice("\(message, privacy: .public)")
        case .error: logger.error("\(message, privacy: .public)")
        }
    }

    private func logger(for category: String) -> Logger {
        lock.lock()
        defer { lock.unlock() }
        if let existing = loggers[category] { return existing }
        let created = Logger(subsystem: Self.subsystem, category: category)
        loggers[category] = created
        return created
    }
}
