import Shared
import SwiftUI

/// The song list's section header: "Albums" while the album sections
/// stand in for the album shelf (#678), else "Songs"; then trailing, the sort menu, labelled with the current order
/// (a checkmark on it in the menu), and for the album orders an Expand All / Collapse All icon button.
struct SongsHeader: View {
    let sortOrder: ArtistSongSortOrder
    let allExpanded: Bool
    /// Whether the song list has at least one collapsible album section (#636): an album order with none, only
    /// "Other Songs", hides Expand All / Collapse All rather than showing a toggle with nothing to do.
    var hasAlbumSections: Bool = true
    var onSortOrderSelected: (ArtistSongSortOrder) -> Void = { _ in }
    var onExpandAll: () -> Void = {}
    var onCollapseAll: () -> Void = {}

    private var showsAlbums: Bool { sortOrder.groupsByAlbum && hasAlbumSections }

    var body: some View {
        HStack(alignment: .center, spacing: Spacing.smallMedium) {
            Text(showsAlbums ? "Albums" : "Songs")
                .textRole(.sectionHeader)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: Spacing.small)
            Menu {
                Picker("Sort By", selection: Binding(get: { sortOrder }, set: onSortOrderSelected)) {
                    ForEach(ArtistSongSortOrder.menuOrder, id: \.self) { order in
                        Text(order.menuTitle).tag(order)
                    }
                }
            } label: {
                HStack(spacing: Spacing.xsmall) {
                    Text(sortOrder.shortTitle)
                    Image(systemName: "chevron.down")
                        .font(.caption.weight(.bold))
                        .imageScale(.small)
                }
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.tint)
                .contentShape(Rectangle())
            }
            .accessibilityLabel("Sort Songs")
            .accessibilityValue(sortOrder.menuTitle)
            .accessibilityIdentifier("artistDetail.sortMenu")
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
                .accessibilityIdentifier("artistDetail.expandAll")
            }
        }
    }
}
