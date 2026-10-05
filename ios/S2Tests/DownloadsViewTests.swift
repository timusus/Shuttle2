import Shared
import SwiftUI
import Testing
import ViewInspector

@testable import S2

/// Settings > Downloads (#852): the downloads as the screen lists them, and its Retry, Dismiss and Remove All.
@MainActor
struct DownloadsViewTests {
    private let downloads: [String: OfflineDownload] = [
        "jellyfin://item/1": OfflineDownload(state: .completed, progress: 1),
        "jellyfin://item/2": OfflineDownload(state: .completed, progress: 1),
        "jellyfin://item/3": OfflineDownload(state: .downloading, progress: 0.5),
        "jellyfin://item/4": OfflineDownload(state: .failed, progress: 0.2),
        "jellyfin://item/5": OfflineDownload(state: .failed, progress: 0)
    ]

    private func state(known: Set<String> = ["jellyfin://item/4"]) -> DownloadsState {
        DownloadsState(downloads, title: { known.contains($0) ? "Teardrop" : nil }, canRetry: { known.contains($0) })
    }

    @Test func sortsDownloadsIntoCompletedRunningAndFailed() {
        let sut = state()
        #expect(sut.completedPaths == ["jellyfin://item/1", "jellyfin://item/2"])
        #expect(sut.running.map(\.path) == ["jellyfin://item/3"])
        #expect(sut.running.first?.progress == 0.5)
        #expect(sut.failed.map(\.path) == ["jellyfin://item/4", "jellyfin://item/5"])
        #expect(!sut.isEmpty)
        #expect(DownloadsState([:], title: { _ in nil }).isEmpty)
    }

    @Test func onlyAFailedDownloadWithAKnownSongCanBeRetried() {
        let failed = state().failed
        #expect(failed[0].title == "Teardrop")
        #expect(failed[0].canRetry)
        #expect(!failed[1].canRetry)
    }

    @Test func showsTheCountAndTheSpaceUsed() throws {
        let sut = DownloadsContent(state: state(), totalBytes: 5_000_000)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "downloads.count").find(text: "2")) != nil)
        #expect((try? sut.inspect().find(text: DownloadsContent.size(5_000_000))) != nil)
        let unread = DownloadsContent(state: state(), totalBytes: nil)
        #expect((try? unread.inspect().find(text: "–")) != nil)
    }

    @Test func retryAndDismissReportTheirPath() throws {
        var retried: String?
        var dismissed: String?
        let sut = DownloadsContent(state: state(), onRetry: { retried = $0 }, onDismiss: { dismissed = $0 })
        try sut.inspect().find(viewWithAccessibilityIdentifier: "downloads.retry").find(ViewType.Button.self).tap()
        #expect(retried == "jellyfin://item/4")
        try sut.inspect().find(button: "Dismiss").tap()
        #expect(dismissed == "jellyfin://item/4")
    }

    @Test func removeAllIsOffOnlyWhenThereAreNoDownloads() throws {
        let empty = DownloadsContent(state: DownloadsState([:], title: { _ in nil }))
        #expect(try empty.inspect().find(viewWithAccessibilityIdentifier: "downloads.removeAll").find(ViewType.Button.self).isDisabled())
        let some = DownloadsContent(state: state())
        #expect(try !some.inspect().find(viewWithAccessibilityIdentifier: "downloads.removeAll").find(ViewType.Button.self).isDisabled())
    }
}
