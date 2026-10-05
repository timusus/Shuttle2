import Foundation
import Shared

/// One labelled value in Song Info.
struct SongInfoRow: Equatable, Identifiable {
    let label: String
    let value: String
    var id: String { label }
}

/// A titled group of rows in Song Info; a section with no rows isn't made.
struct SongInfoSection: Equatable, Identifiable {
    let title: String
    let rows: [SongInfoRow]
    var id: String { title }
}

/// What Song Info shows for a song: the sections, labels and values of Android's, from the shared `infoSections()`,
/// with the labels and the source's value localised, and the rows the song has no value for left out.
enum SongInfoSections {
    static func make(for song: Song, lyrics: String? = nil) -> [SongInfoSection] {
        make(from: song.infoSections(lyrics: lyrics))
    }

    static func make(from shared: [Shared.SongInfoSection]) -> [SongInfoSection] {
        shared.compactMap { section in
            let rows = section.rows.compactMap { row -> SongInfoRow? in
                let resolved = row.valueKey?.localized() ?? row.value
                guard let value = resolved, !value.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }
                return SongInfoRow(label: row.label.localized(), value: value)
            }
            return rows.isEmpty ? nil : SongInfoSection(title: section.title.localized(), rows: rows)
        }
    }
}
