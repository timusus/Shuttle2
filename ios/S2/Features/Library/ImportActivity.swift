import Shared
import SwiftUI

/// The library import's state, one flow for every root's toolbar (`RootToolbar`): read once, so `Observing` keeps
/// one collection rather than restarting on each render.
@MainActor
enum ImportActivity {
    static let state: SkieSwiftStateFlow<SongImportState> = AppGraph.shared.songImportStateProvider.songImportState
}

/// The import's activity outside the setup (#624): a small ring filling as the library imports, or a warning once an
/// import failed, beside Settings' gear on the Home and Library roots. Tapping it shows what's importing and a way to
/// Sources; nothing blocks while it runs. Hidden while idle.
struct ImportActivityButton: View {
    let status: ImportStatus
    let onOpenSources: () -> Void

    @State private var showsDetail = false

    var body: some View {
        Button { showsDetail = true } label: {
            ImportActivityGlyph(status: status)
        }
        .accessibilityLabel(status.isImporting ? "Importing Your Library" : "Import Failed")
        .accessibilityIdentifier("importActivity")
        .popover(isPresented: $showsDetail) {
            VStack(alignment: .leading, spacing: Spacing.medium) {
                ImportStatusRow(status: status)
                Button("Open Sources", systemImage: "server.rack") {
                    showsDetail = false
                    onOpenSources()
                }
                .accessibilityIdentifier("importActivity.openSources")
            }
            .padding(Spacing.medium)
            .frame(minWidth: ArtworkSize.hero, alignment: .leading)
            .presentationCompactAdaptation(.popover)
        }
    }
}

/// The toolbar glyph: the import's progress as a ring (a spinner when it has none), or a warning.
private struct ImportActivityGlyph: View {
    let status: ImportStatus

    var body: some View {
        switch status {
        case .importing(_, _, let fraction?):
            ZStack {
                Circle().stroke(.tint.opacity(0.25), lineWidth: Spacing.tiny)
                Circle()
                    .trim(from: 0, to: fraction)
                    .stroke(.tint, style: StrokeStyle(lineWidth: Spacing.tiny, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                Image(systemName: "arrow.down").font(.caption2.weight(.bold))
            }
            .frame(width: Spacing.large - Spacing.xsmall, height: Spacing.large - Spacing.xsmall)
        case .importing:
            ProgressView()
        case .failed:
            Image(systemName: "exclamationmark.arrow.triangle.2.circlepath").foregroundStyle(.red)
        case .idle:
            EmptyView()
        }
    }
}
