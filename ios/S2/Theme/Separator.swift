import SwiftUI

/// Where a list row's separator goes (docs/design/ios-design-language.md §Dividers): none under an artwork row, whose
/// covers already separate it from the next; inset to the title under a text-only row (a tracklist, Settings), so
/// the line starts where the text does, as iOS's own lists draw it.
enum RowSeparator {
    /// An artwork row (Library, Search, the queue).
    case none
    /// A text-only row: the separator starts at the view this is applied to, the row's title.
    case insetToTitle
    /// The row sets `insetToTitle` on its own title (`TrackRow`), so the list leaves its separator alone.
    case system
}

extension View {
    /// Applies the separator rule. For `.none` apply it to the row (the `List`'s direct child); for
    /// `.insetToTitle` apply it to the title, so the separator's leading edge follows the title's.
    @ViewBuilder
    func rowSeparator(_ separator: RowSeparator) -> some View {
        switch separator {
        case .none:
            listRowSeparator(.hidden)
        case .insetToTitle:
            alignmentGuide(.listRowSeparatorLeading) { $0[.leading] }
        case .system:
            self
        }
    }
}

extension View {
    /// A pinned plain-list section header's own background: the screen's, so rows scrolling under it don't show
    /// through (iOS 26's plain headers have none, and the glass bar above doesn't hide them). Apply it to every
    /// header in a `.plain` `List`. A list on an elevated surface (the queue sheet) sets that surface on itself and
    /// passes it here, since `.background` doesn't pick up a sheet's elevation.
    func pinnedHeader(_ surface: Color = Color(uiColor: .systemBackground)) -> some View {
        frame(maxWidth: .infinity, alignment: .leading)
            .background(surface)
    }
}
