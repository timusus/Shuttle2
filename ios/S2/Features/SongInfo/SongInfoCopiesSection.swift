import Shared
import SwiftUI

extension SongCopyState {
    /// The pill a copy's row shows; nil for a copy that plays if asked and has nothing to say.
    var label: String? {
        switch self {
        case .playing: "Playing"
        case .primary: "Primary"
        case .downloaded: "Downloaded"
        case .unreachable: "Unreachable"
        case .missing: "Missing"
        case .offline: "Offline"
        case .available: nil
        }
    }

    /// A copy that can't play now stays listed, but quiet.
    var isUnavailable: Bool { self == .unreachable || self == .missing || self == .offline }
}

extension CopyChoice {
    /// Why the playing copy is the one that plays.
    var footer: String {
        switch onEnum(of: self) {
        case .primary: "You made this copy primary."
        case .albumPrimary: "This album's primary source."
        case .automatic(let automatic):
            automatic.preferred == .bestQuality
                ? "Chosen automatically: the best quality that plays now."
                : "Chosen automatically: a file on this device, then the best quality."
        }
    }
}

/// What a copy's row can do; each closure takes the copy.
struct SongCopyActions {
    var onPlay: (Song) -> Void = { _ in }
    var onMakePrimary: (Song) -> Void = { _ in }
    var onMakeAlbumPrimary: (Song) -> Void = { _ in }
    var onUseAutomatic: () -> Void = {}
}

/// Song Info's Copies section: one row per copy, best first, with the reason the first one plays as its footer.
struct SongInfoCopiesSection: View {
    let copies: SongCopyRows
    let actions: SongCopyActions

    var body: some View {
        Section {
            ForEach(copies.rows, id: \.song.id) { row in
                SongCopyRowView(row: row)
                    .contextMenu {
                        if row.playable {
                            Button("Play This Copy", systemImage: "play") { actions.onPlay(row.song) }
                        }
                        if row.primary {
                            Button("Use Automatic Choice", systemImage: "wand.and.stars") { actions.onUseAutomatic() }
                        } else {
                            Button("Make Primary", systemImage: "star") { actions.onMakePrimary(row.song) }
                        }
                        Button("Make Primary for This Album", systemImage: "square.stack") { actions.onMakeAlbumPrimary(row.song) }
                    }
            }
        } header: {
            Text("Copies")
                .font(.s2GroupHeader)
                .accessibilityIdentifier("songInfo.section.Copies")
        } footer: {
            Text(copies.chosenBy.footer)
        }
    }
}

private struct SongCopyRowView: View {
    let row: SongCopyRow

    private var detail: String {
        [row.format, row.bitDepth, row.bitRate, row.sampleRate].compactMap { $0 }.joined(separator: " · ")
    }

    var body: some View {
        HStack(spacing: Spacing.medium) {
            Image(systemName: row.provider.symbol)
                .foregroundStyle(.s2TextSecondary)
                .frame(width: 24)
            VStack(alignment: .leading, spacing: Spacing.xsmall) {
                Text(row.provider.title).font(.s2RowTitle)
                if !detail.isEmpty {
                    Text(detail).font(.s2RowSubtitle).foregroundStyle(.s2TextSecondary)
                }
            }
            Spacer(minLength: Spacing.small)
            if let label = row.state.label {
                Text(label).font(.s2RowSubtitle).foregroundStyle(.s2TextSecondary)
            }
        }
        .opacity(row.state.isUnavailable ? 0.5 : 1)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("songInfo.copy.\(row.song.id)")
    }
}
