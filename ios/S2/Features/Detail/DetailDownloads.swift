import Shared
import SwiftUI

extension View {
    /// The long-press menu on a song row: Play Next, Add to Queue and, when the screen presents it, Song Info for that
    /// one song.
    func songContextMenu(
        _ song: Song,
        onPlayNext: @escaping ([Song]) -> Void,
        onAddToQueue: @escaping ([Song]) -> Void,
        onSongInfo: ((Song) -> Void)? = nil,
        downloads: DetailDownloads = DetailDownloads()
    ) -> some View {
        contextMenu {
            Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") { onPlayNext([song]) }
            Button("Add to Queue", systemImage: "text.append") { onAddToQueue([song]) }
            if let onSongInfo {
                Button("Song Info", systemImage: "info.circle") { onSongInfo(song) }
            }
            DownloadMenuItems(songs: [song], downloads: downloads)
        }
    }
}

/// A detail screen's offline downloads (#759, docs/architecture/downloads.md): how far some songs' downloads have got
/// (`OfflineDownloads.summary`, the shared actions' rule) and the shared Download and Remove Download actions. The
/// default has no `summary`, which hides the items, as for a screen that doesn't offer them.
struct DetailDownloads {
    var summary: (([Song]) -> DownloadSummary)? = nil
    var onDownload: ([Song]) -> Void = { _ in }
    var onRemove: ([Song]) -> Void = { _ in }
}

extension DetailDownloads {
    /// Reads the graph's downloads and sends the actions through `actions`. Its screen observes
    /// `OfflineDownloads.downloads`, so it's redrawn, and the summary read again, as a download moves on.
    @MainActor
    init(actions: MediaActionsViewModel, downloads: OfflineDownloads = AppGraph.shared.offlineDownloads) {
        self.init(downloads: downloads, send: { actions.send($0) })
    }

    /// The same, sending the actions through `send`, for a screen that has no `MediaActionsViewModel` of its own (Search).
    @MainActor
    init(downloads: OfflineDownloads = AppGraph.shared.offlineDownloads, send: @escaping (any MediaAction) -> Void) {
        summary = { downloads.summary(songs: $0) }
        onDownload = { send(MediaActionDownload(selection: MediaSelectionSongs(songs: $0))) }
        onRemove = { send(MediaActionRemoveDownload(selection: MediaSelectionSongs(songs: $0))) }
    }
}

/// Download, for songs not yet all on the device, and Remove Download, for any that are or are on their way.
struct DownloadMenuItems: View {
    let songs: [Song]
    let downloads: DetailDownloads

    var body: some View {
        if let summary = downloads.summary?(songs) {
            if summary.canDownload {
                Button("Download", systemImage: "arrow.down.circle") { downloads.onDownload(songs) }
            }
            if summary.canRemove {
                Button("Remove Download", systemImage: "xmark.circle", role: .destructive) { downloads.onRemove(songs) }
            }
        }
    }
}

/// "Downloaded" once every song of the screen is on the device, "Downloading" while any is on its way; nothing otherwise.
struct DownloadStatusLabel: View {
    let status: DownloadSummary.Status

    var body: some View {
        switch status {
        case .downloaded:
            Label("Downloaded", systemImage: "arrow.down.circle.fill")
                .labelStyle(DownloadStatusLabelStyle())
                .accessibilityIdentifier("detailHero.downloaded")
        case .downloading:
            Label("Downloading", systemImage: "arrow.down.circle.dotted")
                .labelStyle(DownloadStatusLabelStyle())
                .accessibilityIdentifier("detailHero.downloading")
        default:
            EmptyView()
        }
    }
}

private struct DownloadStatusLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: Spacing.xsmall) {
            configuration.icon
            configuration.title
        }
        .font(.s2Eyebrow)
        .foregroundStyle(.s2TextSecondary)
        .accessibilityElement(children: .combine)
    }
}
