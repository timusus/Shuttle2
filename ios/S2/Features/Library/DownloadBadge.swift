import Shared
import SwiftUI

/// Whether a song is on the device, for the badge on its row (#853): downloaded, or on its way. A failed download
/// shows nothing, as it isn't held (`OfflineDownloads.observeHeldPaths`).
enum DownloadBadge: Equatable {
    case downloaded
    case downloading

    var systemImage: String {
        switch self {
        case .downloaded: "arrow.down.circle.fill"
        case .downloading: "arrow.down.circle.dotted"
        }
    }

    var label: String {
        switch self {
        case .downloaded: String(localized: "Downloaded")
        case .downloading: String(localized: "Downloading")
        }
    }

    /// The badge for each song path that has one. Progress is left out, so the map only changes (and rows only redraw)
    /// when a download starts, finishes, fails or is removed, not on every progress tick.
    static func badges(_ downloads: [String: OfflineDownload]) -> [String: DownloadBadge] {
        downloads.compactMapValues { download in
            switch download.state {
            case .completed: .downloaded
            case .downloading: .downloading
            default: nil
            }
        }
    }
}

private struct DownloadBadgesKey: EnvironmentKey {
    static let defaultValue: [String: DownloadBadge] = [:]
}

extension EnvironmentValues {
    /// The badge of each song, by `Song.path`; set once at the root by `observingDownloadBadges`.
    var downloadBadges: [String: DownloadBadge] {
        get { self[DownloadBadgesKey.self] }
        set { self[DownloadBadgesKey.self] = newValue }
    }
}

extension View {
    /// Puts `downloads`' badges in the environment, from one observation for the whole app rather than one per row.
    func observingDownloadBadges(_ downloads: OfflineDownloads) -> some View {
        modifier(DownloadBadgesModifier(downloads: downloads))
    }
}

private struct DownloadBadgesModifier: ViewModifier {
    let downloads: OfflineDownloads
    @State private var badges: [String: DownloadBadge]

    init(downloads: OfflineDownloads) {
        self.downloads = downloads
        _badges = State(initialValue: DownloadBadge.badges(downloads.downloads.value))
    }

    func body(content: Content) -> some View {
        content
            .environment(\.downloadBadges, badges)
            .task {
                for await current in downloads.downloads {
                    let next = DownloadBadge.badges(current)
                    if next != badges { badges = next }
                }
            }
    }
}

/// A song row's download badge: a small glyph, spoken as its state.
struct DownloadBadgeView: View {
    let badge: DownloadBadge

    var body: some View {
        Image(systemName: badge.systemImage)
            .accessibilityLabel(badge.label)
            .accessibilityIdentifier(badge == .downloaded ? "songRow.downloaded" : "songRow.downloading")
    }
}
