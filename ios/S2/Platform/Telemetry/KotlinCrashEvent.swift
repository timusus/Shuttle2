import Foundation
import Sentry

/// Builds the Sentry event for a Kotlin/Native uncaught exception (`CrashHooks.kt`): the Kotlin class as the error
/// type, the message as the value, and the Kotlin stack both as frames on the exception and as raw text in `extra`.
/// Ported from Shuttle Podcasts' `KotlinCrashEvent`.
enum KotlinCrashEvent {
    /// One Kotlin/Native backtrace line, as `Throwable.getStackTrace()` prints it on iOS:
    /// `<index> <module> <hex address> <symbol> + <offset>`, with a `(<file>:<line>:<col>)` location trailing only in
    /// binaries that kept debug info. `<symbol>` is `kfun:<package>#<member>` for Kotlin frames, or a bare C symbol or
    /// `0x0` for frames outside the Kotlin runtime.
    static func frame(fromLine line: Substring) -> Sentry.Frame? {
        let text = line.trimmingCharacters(in: .whitespaces)
        guard !text.isEmpty, !text.hasPrefix("Caused by:") else { return nil }

        let fields = text.split(separator: " ")
        guard fields.count >= 4, fields[2].hasPrefix("0x") else { return nil }
        let module = String(fields[1])
        let address = String(fields[2])
        var rest = fields[3...].joined(separator: " ")

        var location: Substring?
        if rest.hasSuffix(")"), let openParen = rest.lastIndex(of: "(") {
            location = rest[rest.index(after: openParen)..<rest.index(before: rest.endIndex)]
            rest = String(rest[rest.startIndex..<openParen]).trimmingCharacters(in: .whitespaces)
        }

        let symbol: String
        if let plusRange = rest.range(of: " + ", options: .backwards) {
            symbol = String(rest[rest.startIndex..<plusRange.lowerBound])
        } else {
            symbol = rest
        }
        guard !symbol.isEmpty else { return nil }

        let frame = Sentry.Frame()
        frame.module = module
        frame.instructionAddress = address
        if symbol.hasPrefix("kfun:") {
            let body = String(symbol.dropFirst("kfun:".count))
            frame.function = body
            if let hashIndex = body.lastIndex(of: "#") {
                frame.package = String(body[body.startIndex..<hashIndex])
            } else if let dotIndex = body.lastIndex(of: ".") {
                frame.package = String(body[body.startIndex..<dotIndex])
            }
        } else {
            frame.function = symbol
        }
        if let location {
            let parts = location.components(separatedBy: ":")
            if parts.count >= 3, let sourceLine = Int(parts[parts.count - 2]), let column = Int(parts[parts.count - 1]) {
                frame.fileName = parts[0..<(parts.count - 2)].joined(separator: ":")
                frame.lineNumber = sourceLine as NSNumber
                frame.columnNumber = column as NSNumber
            } else {
                frame.fileName = String(location)
            }
        }
        frame.inApp = true
        return frame
    }

    /// `getStackTrace()` lists the crash site first; Sentry wants the crashing frame last.
    static func frames(fromStackTrace stackTrace: String) -> [Sentry.Frame] {
        stackTrace.split(separator: "\n", omittingEmptySubsequences: false)
            .compactMap(frame(fromLine:))
            .reversed()
    }

    static func make(message: String, stackTrace: String, exceptionClass: String) -> Sentry.Event {
        let value = message.isEmpty ? exceptionClass : "\(exceptionClass): \(message)"
        let exception = Sentry.Exception(value: value, type: exceptionClass)
        let parsed = frames(fromStackTrace: stackTrace)
        if !parsed.isEmpty {
            exception.stacktrace = SentryStacktrace(frames: parsed, registers: [:])
        }

        let event = Sentry.Event(level: .fatal)
        event.exceptions = [exception]
        event.tags = ["kotlin_native": "true"]
        event.extra = [
            "kotlin_exception_class": exceptionClass,
            "kotlin_message": message,
            "kotlin_stack_trace": stackTrace,
        ]
        return event
    }
}
