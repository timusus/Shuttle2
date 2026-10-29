import Shared
import SwiftUI

/// One copy a song's Play From menu offers.
struct PlayFromChoice: Identifiable {
    let copy: Song
    let title: String
    /// "FLAC · 1411 kb/s"; nil when the copy's tags give neither.
    let detail: String?
    /// The copy that plays now.
    let isPlaying: Bool
    var id: Int64 { copy.id }

    /// The copies that can play now, best first; empty unless merging is on and two or more can play.
    static func choices(for song: Song, context: CopyContext?) -> [PlayFromChoice] {
        guard let context, context.mergeCopies, let rows = song.doCopyRows(context: context)?.rows else { return [] }
        let playable = rows.filter(\.playable)
        guard playable.count >= 2 else { return [] }
        return playable.map { row in
            PlayFromChoice(
                copy: row.song,
                title: row.provider.title,
                detail: [row.format, row.bitRate].compactMap { $0 }.joined(separator: " · ").nilIfEmpty,
                isPlaying: row.state == .playing
            )
        }
    }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}

/// A nested "Play From" menu in a song's menu, with a checkmark on the copy that plays; absent for a song without
/// two copies that can play.
struct PlayFromMenu: View {
    let song: Song
    @Environment(\.copyContext) private var context
    @Environment(\.playFromCopy) private var playFrom

    var body: some View {
        let choices = PlayFromChoice.choices(for: song, context: context)
        if !choices.isEmpty {
            Menu {
                ForEach(choices) { choice in
                    Button { playFrom(song, choice.copy) } label: {
                        Text(choice.title)
                        if let detail = choice.detail { Text(detail) }
                        if choice.isPlaying { Image(systemName: "checkmark") }
                    }
                }
            } label: {
                Label("Play From", systemImage: "play.circle")
            }
        }
    }
}
