import Shared
import SwiftUI

/// Which song's Song Info to present. Identifiable so `.sheet(item:)` can take it.
struct SongInfoTarget: Identifiable, Equatable {
    let songID: Int64
    var id: Int64 { songID }
}

extension View {
    /// Presents Song Info for `target` as a sheet. Any song menu sets its own `@State` target to open it.
    func songInfoSheet(_ target: Binding<SongInfoTarget?>) -> some View {
        sheet(item: target) { target in
            SongInfoSheet(songID: target.songID)
        }
    }
}

/// Song Info on the shared `SongInfoViewModel`, which follows the library, so a tag edit shows at once.
struct SongInfoSheet: View {
    let songID: Int64
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let key = "songInfo:\(songID)"
        let viewModel = ViewModelCache.shared.viewModel(key) {
            AppGraph.shared.songInfoViewModelFactory.create(songId: songID)
        }
        NavigationStack {
            Observing(viewModel.uiState) { state in
                SongInfoContent(song: state.song, isLoading: state.loading)
            }
            .navigationTitle("Song Info")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                        .accessibilityIdentifier("songInfo.done")
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .onDisappear { ViewModelCache.shared.remove(key) }
    }
}

/// The grouped sections for a song, or a placeholder while it loads or if it's gone from the library.
struct SongInfoContent: View {
    let song: Song?
    var isLoading = false

    var body: some View {
        if let song {
            let sections = SongInfoSections.make(for: song)
            List {
                ForEach(sections) { section in
                    Section {
                        ForEach(section.rows) { row in
                            SongInfoRowView(row: row)
                        }
                    } header: {
                        Text(section.title)
                            .font(.s2GroupHeader)
                            .accessibilityIdentifier("songInfo.section.\(section.title)")
                    }
                }
            }
            .listStyle(.insetGrouped)
        } else if isLoading {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            EmptyState("Song Not Found", systemImage: "music.note", message: "It may have been removed from your library.")
        }
    }
}

/// A label over its value on compact widths' long values (paths), side by side otherwise; long-press copies the value.
private struct SongInfoRowView: View {
    let row: SongInfoRow

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .firstTextBaseline, spacing: Spacing.medium) {
                label
                Spacer(minLength: Spacing.small)
                value.multilineTextAlignment(.trailing)
            }
            VStack(alignment: .leading, spacing: Spacing.xsmall) {
                label
                value
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("songInfo.row.\(row.label)")
        .contextMenu {
            Button("Copy", systemImage: "doc.on.doc") { UIPasteboard.general.string = row.value }
        }
    }

    private var label: some View {
        Text(row.label).font(.s2RowSubtitle).foregroundStyle(.s2TextSecondary)
    }

    private var value: some View {
        Text(row.value).font(.s2RowTitle).fixedSize(horizontal: false, vertical: true)
    }
}
