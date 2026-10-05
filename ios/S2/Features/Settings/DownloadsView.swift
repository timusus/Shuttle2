import Shared
import SwiftUI

/// Settings > Downloads (#852): how much space the downloaded songs take, Remove All, and the downloads still running
/// or failed, with Retry. Reads `OfflineDownloads` (docs/architecture/downloads.md); the files are the record, so the
/// size is the files' own.
struct DownloadsView: View {
    var body: some View {
        let downloads = AppGraph.shared.offlineDownloads
        let models = ViewModelCache.shared.viewModel(Route.downloads.cacheKey) {
            DownloadsModels(graph: AppGraph.shared)
        }
        Observing(downloads.downloads, models.actions.uiState) { current, actions in
            DownloadsStorage(
                downloads: current,
                title: { downloads.requestedTitle(path: $0) },
                fileSize: { path in
                    downloads.fileUrl(path: path).flatMap { URL(string: $0) }.flatMap { try? $0.resourceValues(forKeys: [.fileSizeKey]).fileSize }.map(Int64.init) ?? 0
                },
                onRetry: { path in
                    // One that failed in an earlier launch has its song loaded from the library again first
                    Task {
                        guard let song = try? await downloads.loadRequestedSong(path: path) else { return }
                        models.actions.send(MediaActionDownload(selection: MediaSelectionSongs(songs: [song])))
                    }
                },
                onDismiss: { downloads.dismissFailed(path: $0) },
                onRemoveAll: { downloads.removeEverything() },
                canRetry: { downloads.canRetry(path: $0) }
            )
            .mediaActionResults(actions.events, handled: { models.actions.onEventHandled(id: $0) }, send: { models.actions.send($0) })
        }
    }
}

/// The Downloads screen's ViewModels, cached together under its route's key.
final class DownloadsModels: ViewModelGroup {
    let actions: MediaActionsViewModel

    init(graph: IosAppGraph) {
        actions = graph.mediaActionsViewModel
    }

    var members: [Lifecycle_viewmodelViewModel] { [actions] }
}

/// The downloads as plain values: the completed ones counted, the running and failed ones listed.
struct DownloadsState: Equatable {
    struct Item: Equatable, Identifiable {
        let path: String
        let title: String
        /// 0...1 while running; nil for a failed one.
        let progress: Double?
        let canRetry: Bool

        var id: String { path }
    }

    var completedPaths: [String] = []
    var running: [Item] = []
    var failed: [Item] = []

    var isEmpty: Bool { completedPaths.isEmpty && running.isEmpty && failed.isEmpty }

    /// `title` is a song's name, nil when the download is from an earlier launch and its song isn't known.
    init(_ downloads: [String: OfflineDownload], title: (String) -> String?, canRetry: (String) -> Bool = { _ in false }) {
        for (path, download) in downloads.sorted(by: { $0.key < $1.key }) {
            let name = title(path) ?? String(localized: "Unknown song")
            switch download.state {
            case .completed:
                completedPaths.append(path)
            case .downloading:
                running.append(Item(path: path, title: name, progress: Double(download.progress), canRetry: false))
            case .failed:
                failed.append(Item(path: path, title: name, progress: nil, canRetry: canRetry(path)))
            default:
                break
            }
        }
    }

    init() {}
}

/// Adds the size of the completed downloads' files, read only when the set of completed
/// downloads changes (not on each progress tick).
struct DownloadsStorage: View {
    let downloads: [String: OfflineDownload]
    var title: (String) -> String?
    var fileSize: (String) -> Int64
    var onRetry: (String) -> Void
    var onDismiss: (String) -> Void
    var onRemoveAll: () -> Void
    var canRetry: (String) -> Bool

    @State private var bytes: Int64?

    var body: some View {
        let state = DownloadsState(downloads, title: title, canRetry: canRetry)
        DownloadsContent(state: state, totalBytes: bytes, onRetry: onRetry, onDismiss: onDismiss, onRemoveAll: onRemoveAll)
            .task(id: state.completedPaths) {
                bytes = state.completedPaths.reduce(Int64(0)) { $0 + fileSize($1) }
            }
    }
}

/// The screen from plain values.
struct DownloadsContent: View {
    let state: DownloadsState
    /// The completed downloads' size on disk; nil until it's been read.
    var totalBytes: Int64?
    var onRetry: (String) -> Void = { _ in }
    var onDismiss: (String) -> Void = { _ in }
    var onRemoveAll: () -> Void = {}

    @State private var confirmingRemoveAll = false

    var body: some View {
        Form {
            Section {
                LabeledContent("Downloaded Songs", value: "\(state.completedPaths.count)")
                    .accessibilityIdentifier("downloads.count")
                LabeledContent("Space Used", value: totalBytes.map(Self.size) ?? "–")
                    .accessibilityIdentifier("downloads.size")
                Button("Remove All Downloads", role: .destructive) { confirmingRemoveAll = true }
                    .disabled(state.isEmpty)
                    .accessibilityIdentifier("downloads.removeAll")
            } footer: {
                if state.isEmpty {
                    Text("Songs you download from a server are kept on this device so they play without a connection.")
                }
            }
            if !state.running.isEmpty {
                Section("In Progress") {
                    ForEach(state.running) { item in
                        VStack(alignment: .leading, spacing: Spacing.xsmall) {
                            Text(item.title)
                            ProgressView(value: item.progress)
                        }
                        .accessibilityElement(children: .combine)
                        .accessibilityValue(Text(item.progress.map { $0.formatted(.percent.precision(.fractionLength(0))) } ?? ""))
                        .accessibilityIdentifier("downloads.running")
                    }
                }
            }
            if !state.failed.isEmpty {
                Section("Failed") {
                    ForEach(state.failed) { item in
                        VStack(alignment: .leading, spacing: Spacing.xsmall) {
                            Text(item.title)
                            HStack {
                                if item.canRetry {
                                    Button("Retry", systemImage: "arrow.clockwise") { onRetry(item.path) }
                                        .accessibilityLabel(Text("Retry \(item.title)"))
                                        .accessibilityIdentifier("downloads.retry")
                                }
                                Button("Dismiss", systemImage: "xmark") { onDismiss(item.path) }
                                    .accessibilityLabel(Text("Dismiss \(item.title)"))
                                    .accessibilityIdentifier("downloads.dismiss")
                            }
                            .buttonStyle(.borderless)
                            .font(.callout)
                        }
                        .accessibilityIdentifier("downloads.failed")
                    }
                }
            }
        }
        .formStyle(.grouped)
        .navigationTitle("Downloads")
        .confirmationDialog("Remove all downloads?", isPresented: $confirmingRemoveAll, titleVisibility: .visible) {
            Button("Remove All", role: .destructive, action: onRemoveAll)
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This deletes the downloaded songs from this device and stops any that are running. You can download them again.")
        }
    }

    static func size(_ bytes: Int64) -> String {
        ByteCountFormatter.string(fromByteCount: bytes, countStyle: .file)
    }
}
