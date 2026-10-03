import Sentry
import Testing
@testable import S2

/// Parsing the Kotlin/Native stack text into Sentry frames, the pure part of the Kotlin crash reporter (ported from
/// Shuttle Podcasts). The hook itself aborts the process, so it isn't tested. The fixtures are the shape
/// `Throwable.getStackTrace()` prints on Kotlin/Native iOS: `<index> <module> <address> <symbol> + <offset>`, with a
/// `(<file>:<line>:<col>)` location only when the binary kept debug info.
struct KotlinCrashEventTests {
    private static let locatedLine: Substring =
        "1   test.kexe                           0x102fc0163        kfun:com.simplecityapps.shuttle.shared.telemetry.CrashHooksTest.Boom.<init>#internal + 123 (/src/shared/CrashHooksTest.kt:11:5)"

    private static let unlocatedLine: Substring =
        "0   test.kexe                           0x1031fcf73        kfun:kotlin.Exception#<init>(kotlin.String?;kotlin.Throwable?){} + 123 "

    private static let systemLine: Substring =
        "18  dyld                                0x104cb30e3        0x0 + 4375392483 "

    @Test func parsesALocatedKfunFrame() throws {
        let frame = try #require(KotlinCrashEvent.frame(fromLine: Self.locatedLine))

        #expect(frame.function == "com.simplecityapps.shuttle.shared.telemetry.CrashHooksTest.Boom.<init>#internal")
        #expect(frame.package == "com.simplecityapps.shuttle.shared.telemetry.CrashHooksTest.Boom.<init>")
        #expect(frame.module == "test.kexe")
        #expect(frame.instructionAddress == "0x102fc0163")
        #expect(frame.fileName == "/src/shared/CrashHooksTest.kt")
        #expect(frame.lineNumber == 11)
        #expect(frame.columnNumber == 5)
        #expect(frame.inApp == true)
    }

    @Test func parsesAKfunFrameWithoutASourceLocation() throws {
        let frame = try #require(KotlinCrashEvent.frame(fromLine: Self.unlocatedLine))

        #expect(frame.function == "kotlin.Exception#<init>(kotlin.String?;kotlin.Throwable?){}")
        #expect(frame.package == "kotlin.Exception")
        #expect(frame.instructionAddress == "0x1031fcf73")
        #expect(frame.fileName == nil)
    }

    @Test func parsesASystemFrameWithNoKfunSymbol() throws {
        let frame = try #require(KotlinCrashEvent.frame(fromLine: Self.systemLine))

        #expect(frame.module == "dyld")
        #expect(frame.function == "0x0")
        #expect(frame.package == nil)
        #expect(frame.instructionAddress == "0x104cb30e3")
    }

    @Test func skipsCauseSummariesAndUnrecognisedLines() {
        #expect(KotlinCrashEvent.frame(fromLine: "Caused by: kotlin.IllegalStateException: boom") == nil)
        #expect(KotlinCrashEvent.frame(fromLine: "not a stack frame at all") == nil)
        #expect(KotlinCrashEvent.frame(fromLine: "") == nil)
    }

    @Test func framesComeOldestFirstWithTheCrashFrameLast() {
        let stackTrace = "\(Self.locatedLine)\nCaused by: kotlin.IllegalStateException: boom\n    \(Self.unlocatedLine)"

        #expect(KotlinCrashEvent.frames(fromStackTrace: stackTrace).map(\.function) == [
            "kotlin.Exception#<init>(kotlin.String?;kotlin.Throwable?){}",
            "com.simplecityapps.shuttle.shared.telemetry.CrashHooksTest.Boom.<init>#internal",
        ])
    }

    @Test func theEventCarriesTheClassMessageStackAndTag() throws {
        let event = KotlinCrashEvent.make(message: "boom", stackTrace: String(Self.locatedLine), exceptionClass: "kotlin.IllegalStateException")

        let exception = try #require(event.exceptions?.first)
        #expect(event.level == .fatal)
        #expect(exception.type == "kotlin.IllegalStateException")
        #expect(exception.value == "kotlin.IllegalStateException: boom")
        #expect(exception.stacktrace?.frames.count == 1)
        #expect(event.tags?["kotlin_native"] == "true")
        #expect(event.extra?["kotlin_exception_class"] as? String == "kotlin.IllegalStateException")
    }

    @Test func aMessagelessExceptionReportsTheClassAlone() {
        let event = KotlinCrashEvent.make(message: "", stackTrace: String(Self.locatedLine), exceptionClass: "kotlin.IllegalStateException")
        #expect(event.exceptions?.first?.value == "kotlin.IllegalStateException")
    }
}
