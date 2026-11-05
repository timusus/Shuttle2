import Shared
import SwiftUI

/// One entry of a detail screen's sort menu: the menu row's title and the shorter label the closed menu shows.
struct SongSortChoice<Sort: Hashable>: Hashable {
    let sort: Sort
    let title: String
    let shortTitle: String
}

/// Titled "Albums" where album sections stand in for the list, else "Songs"; a grouped sort adds an Expand All / Collapse All
/// button. Shared by the artist and playlist screens, which differ only in their sort choices and identifiers.
struct SongListHeader<Sort: Hashable>: View {
    let sort: Sort
    let choices: [SongSortChoice<Sort>]
    /// Whether the list shows collapsible album sections (a grouped sort with at least one album).
    let showsAlbums: Bool
    let allExpanded: Bool
    var idPrefix = "artistDetail"
    var onSortSelected: (Sort) -> Void = { _ in }
    var onExpandAll: () -> Void = {}
    var onCollapseAll: () -> Void = {}

    private var current: SongSortChoice<Sort>? { choices.first { $0.sort == sort } }

    var body: some View {
        HStack(alignment: .center, spacing: Spacing.smallMedium) {
            Text(showsAlbums ? "Albums" : "Songs")
                .textRole(.sectionHeader)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: Spacing.small)
            Menu {
                Picker("Sort By", selection: Binding(get: { sort }, set: onSortSelected)) {
                    ForEach(choices, id: \.sort) { choice in
                        Text(choice.title).tag(choice.sort)
                    }
                }
            } label: {
                HStack(spacing: Spacing.xsmall) {
                    Text(current?.shortTitle ?? "")
                    Image(systemName: "chevron.down")
                        .font(.caption.weight(.bold))
                        .imageScale(.small)
                }
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.tint)
                .contentShape(Rectangle())
            }
            .accessibilityLabel("Sort Songs")
            .accessibilityValue(current?.title ?? "")
            .accessibilityIdentifier("\(idPrefix).sortMenu")
            if showsAlbums {
                Button(action: allExpanded ? onCollapseAll : onExpandAll) {
                    Label(
                        allExpanded ? "Collapse All" : "Expand All",
                        systemImage: allExpanded ? "rectangle.compress.vertical" : "rectangle.expand.vertical"
                    )
                    .labelStyle(.iconOnly)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.tint)
                    .frame(minWidth: TouchTarget.minimum, minHeight: TouchTarget.minimum)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(allExpanded ? "Collapse All" : "Expand All")
                .accessibilityIdentifier("\(idPrefix).expandAll")
            }
        }
    }
}

/// The artist screen's Songs header: its sort orders over the shared header.
struct SongsHeader: View {
    let sortOrder: ArtistSongSortOrder
    let allExpanded: Bool
    /// Whether the song list has at least one collapsible album section: an album order with none, only
    /// "Other Songs", hides Expand All / Collapse All rather than showing a toggle with nothing to do.
    var hasAlbumSections: Bool = true
    var onSortOrderSelected: (ArtistSongSortOrder) -> Void = { _ in }
    var onExpandAll: () -> Void = {}
    var onCollapseAll: () -> Void = {}

    var body: some View {
        SongListHeader(
            sort: sortOrder,
            choices: ArtistSongSortOrder.menuOrder.map { SongSortChoice(sort: $0, title: $0.menuTitle, shortTitle: $0.shortTitle) },
            showsAlbums: sortOrder.groupsByAlbum && hasAlbumSections,
            allExpanded: allExpanded,
            onSortSelected: onSortOrderSelected,
            onExpandAll: onExpandAll,
            onCollapseAll: onCollapseAll
        )
    }
}
