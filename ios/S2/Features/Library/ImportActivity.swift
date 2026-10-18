import Shared
import SwiftUI

/// Nothing blocks while the import runs, so this stays a small glyph that opens the detail on tap.
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
            Image(systemName: "exclamationmark.arrow.triangle.2.circlepath").foregroundStyle(.s2Error)
        case .idle:
            EmptyView()
        }
    }
}
