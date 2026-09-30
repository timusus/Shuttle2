import SwiftUI

/// Where a list row's separator goes (docs/design/ios-design-language.md §Dividers): none under an artwork row, whose
/// covers already separate it from the next; inset to the title under a text-only row (a tracklist, Settings), so
/// the line starts where the text does, as iOS's own lists draw it.
enum RowSeparator {
    /// An artwork row (Library, Search, the queue).
    case none
    /// A text-only row: the separator starts at the view this is applied to, the row's title.
    case insetToTitle
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
        }
    }
}
