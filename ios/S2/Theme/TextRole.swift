import SwiftUI

/// What a piece of text is for, so every row of one kind sets it the same way. Each role is a Dynamic Type style
/// from `Typography.swift`; screens ask for a role instead of picking a size and weight.
enum TextRole {
    /// A list row's or tile's title. Regular weight; a selected or now-playing row adds `.fontWeight(.semibold)` itself.
    case rowTitle
    /// A row's second line: artist, album, counts.
    case rowSubtitle
    /// A row's trailing time or count.
    case rowMeta
    /// Running text.
    case body
    /// Hints, footers and small labels under a control.
    case caption
    /// An in-content section's title.
    case sectionHeader
    /// A small uppercase label over a run of rows (a disc).
    case groupHeader

    var font: Font {
        switch self {
        case .rowTitle: .s2RowTitle
        case .rowSubtitle: .s2RowSubtitle
        case .rowMeta: .s2RowMeta
        case .body: .body
        case .caption: .s2Caption
        case .sectionHeader: .s2SectionTitle
        case .groupHeader: .s2GroupHeader
        }
    }
}

extension View {
    func textRole(_ role: TextRole) -> some View {
        font(role.font)
    }
}
