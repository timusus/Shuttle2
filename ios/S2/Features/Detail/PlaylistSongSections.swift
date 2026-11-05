import Shared
import SwiftUI

/// A playlist screen's song list from the shared `SongSection`s: foldable album sections under a grouped sort, else one
/// flat run. `row` draws a song at its offset in the displayed (flattened) order, numbered inside an album section.
struct PlaylistSongSections<Row: View>: View {
    let sections: [SongSection]
    let expandedAlbums: Set<AlbumGroupKey>
    var onToggleAlbum: (Album) -> Void = { _ in }
    var onPlaySection: ([Song]) -> Void = { _ in }
    var onShuffleSection: ([Song]) -> Void = { _ in }
    var onPlayNext: ([Song]) -> Void = { _ in }
    var onAddToQueue: ([Song]) -> Void = { _ in }
    var onOpenAlbum: (Album) -> Void = { _ in }
    @ViewBuilder let row: (Song, Int, Bool) -> Row

    @Environment(\.layoutTier) private var layoutTier

    private var indexed: [(id: Int, section: SongSection, start: Int)] {
        var offset = 0
        return sections.enumerated().map { index, section in
            defer { offset += section.songs.count }
            return (index, section, offset)
        }
    }

    var body: some View {
        ForEach(indexed, id: \.id) { entry in
            if let album = entry.section.album {
                albumSection(album, songs: entry.section.songs, start: entry.start)
            } else {
                Section {
                    ForEach(Array(entry.section.songs.enumerated()), id: \.offset) { offset, song in
                        row(song, entry.start + offset, false)
                    }
                } header: {
                    // Under a grouped sort the unnamed section holds songs on none of the albums
                    if sections.contains(where: { $0.album != nil }) {
                        SectionHeader("Other Songs").textCase(nil).pinnedHeader()
                    }
                }
            }
        }
    }

    private func albumSection(_ album: Album, songs: [Song], start: Int) -> some View {
        let expanded = album.groupKey.map { expandedAlbums.contains($0) } ?? false
        let inset = AdaptiveLayout.contentInset(layoutTier)
        return Section {
            if expanded {
                ForEach(Array(songs.enumerated()), id: \.offset) { offset, song in
                    row(song, start + offset, true)
                }
            }
        } header: {
            AlbumSectionHeader(
                album: album,
                songCount: songs.count,
                isExpanded: expanded,
                onToggle: { onToggleAlbum(album) },
                onPlay: { onPlaySection(songs) },
                onShuffle: { onShuffleSection(songs) },
                onPlayNext: { onPlayNext(songs) },
                onAddToQueue: { onAddToQueue(songs) },
                onOpenAlbum: { onOpenAlbum(album) }
            )
            .pinnedHeader()
            .listRowInsets(EdgeInsets(top: Spacing.xsmall, leading: inset, bottom: Spacing.xsmall, trailing: inset))
        }
        .listSectionSpacing(Spacing.small)
    }
}

extension [SongSection] {
    /// Whether any section is an album, so the list is grouped and offers Expand All.
    var hasAlbumSections: Bool { contains { $0.album != nil } }
}
