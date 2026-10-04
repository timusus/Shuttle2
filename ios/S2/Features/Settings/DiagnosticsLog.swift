import CoreTransferable
import OSLog
import UIKit
import UniformTypeIdentifiers

/// The app's log for a bug report: this run's `Logger` entries from the app's subsystems, under a header naming the
/// build and device. iOS keeps no log file of its own, so the text is read from the unified log when it's shared
/// (`ShareLink` exports it lazily, to a temporary file, so the share sheet gets a file rather than a large string).
struct DiagnosticsLog: Transferable {
    static let subsystemPrefix = "com.simplecityapps"

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(exportedContentType: .plainText) { log in
            SentTransferredFile(try log.writeFile())
        }
    }

    var fileName = "shuttle-diagnostics.txt"

    func writeFile() throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
        try Self.text().write(to: url, atomically: true, encoding: .utf8)
        return url
    }

    static func text(now: Date = .now) -> String {
        var lines = [header(now: now)]
        if let store = try? OSLogStore(scope: .currentProcessIdentifier),
           let entries = try? store.getEntries(at: store.position(timeIntervalSinceLatestBoot: 0)) {
            let format = Date.ISO8601FormatStyle(includingFractionalSeconds: true)
            for case let entry as OSLogEntryLog in entries where entry.subsystem.hasPrefix(subsystemPrefix) {
                lines.append("\(entry.date.formatted(format)) [\(entry.category)] \(entry.composedMessage)")
            }
        }
        return lines.joined(separator: "\n")
    }

    private static func header(now: Date) -> String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "?"
        let build = info?["CFBundleVersion"] as? String ?? "?"
        let device = UIDevice.current
        return "Shuttle Music \(version) (\(build)), \(device.systemName) \(device.systemVersion), \(device.model), \(now.formatted(Date.ISO8601FormatStyle()))\n"
    }
}
