import Shared
import SwiftUI

/// The Library lists' A–Z section index (#627), Music and Contacts' right-edge letters rather than Android's draggable
/// thumb. The letters come from the shared UiStates' `letterIndex` (`letterSections` in `:android:domain`): the same
/// key the shared sort compares, so a name sort's letters run in order, with '#' for digits, symbols and scripts
/// without a short alphabet. A sort that isn't by name has no `letterIndex`, and so no index.
///
/// Lists and grids alike use `LetterIndexStrip`, which scrolls to a section's first item, so the letters sit at the
/// same x in list and grid mode (#643): iOS 26's native list index (`sectionIndexLabel`) sits further out than any
/// strip a grid can draw.

/// A run of a list's rows under one letter.
struct LetterIndexSection: Identifiable, Equatable {
    let letter: String
    /// The rows' positions in the list.
    let rows: Range<Int>
    /// The first row's id: what the section is identified by, and what the index scrolls to.
    let anchor: AnyHashable
    /// Whether this is the first run under its letter, which the index entry jumps to. A collation that splits a
    /// letter (the '#' of ideographs sorts after Z, while digits sort before A) gives later runs no entry of their own.
    let isIndexed: Bool

    var id: AnyHashable { anchor }
}

enum LetterIndex {
    /// The shared `sections` as runs of `items`, identified by their first item's `id`; nil when there's no index.
    static func sections<Item, ID: Hashable>(_ sections: [LetterSection]?, items: [Item], id: KeyPath<Item, ID>) -> [LetterIndexSection]? {
        guard let sections, !sections.isEmpty, !items.isEmpty else { return nil }
        var seen = Set<String>()
        return sections.enumerated().compactMap { position, section in
            let start = Int(section.firstIndex)
            let end = position + 1 < sections.count ? Int(sections[position + 1].firstIndex) : items.count
            guard start < end, end <= items.count else { return nil }
            return LetterIndexSection(
                letter: section.letter,
                rows: start ..< end,
                anchor: AnyHashable(items[start][keyPath: id]),
                isIndexed: seen.insert(section.letter).inserted
            )
        }
    }
}

/// A plain list of `items` in their letter `sections`, with the index down its trailing edge; without sections, the
/// same rows unsectioned. `header` rows (Songs' Shuffle) come first, outside any section. `row` gets each item's
/// position in `items`.
struct LetterIndexedList<Item, ID: Hashable, Header: View, Row: View>: View {
    let items: [Item]
    let id: KeyPath<Item, ID>
    let sections: [LetterIndexSection]?
    @ViewBuilder let header: () -> Header
    @ViewBuilder let row: (Int, Item) -> Row

    init(
        items: [Item],
        id: KeyPath<Item, ID>,
        sections: [LetterIndexSection]?,
        @ViewBuilder header: @escaping () -> Header,
        @ViewBuilder row: @escaping (Int, Item) -> Row
    ) {
        self.items = items
        self.id = id
        self.sections = sections
        self.header = header
        self.row = row
    }

    var body: some View {
        if let sections {
            ScrollViewReader { proxy in
                List {
                    header()
                    ForEach(sections) { section in
                        Section {
                            ForEach(section.rows.map { IndexedItem(index: $0, item: items[$0], id: items[$0][keyPath: id]) }, id: \.id) {
                                row($0.index, $0.item)
                            }
                        }
                    }
                }
                .listStyle(.plain)
                .letterIndex(sections) { proxy.scrollTo($0.anchor, anchor: .top) }
            }
        } else {
            List {
                header()
                ForEach(items.indices.map { IndexedItem(index: $0, item: items[$0], id: items[$0][keyPath: id]) }, id: \.id) {
                    row($0.index, $0.item)
                }
            }
            .listStyle(.plain)
        }
    }
}

/// A Library list row that pushes `route`, without the disclosure chevron, as Music and Contacts do, so nothing
/// competes with the letter index down the same edge. The whole row taps through and VoiceOver reads it as a button.
/// iOS 26 hides the chevron itself; before it an invisible link fills the row behind the label, so a tap anywhere
/// on the row reaches it, and the row is one element with the one button trait (the hidden link adds none).
/// Settings-style lists keep plain `NavigationLink`s and their chevrons.
struct LibraryRowLink<Label: View>: View {
    let route: Route
    @ViewBuilder let label: () -> Label

    var body: some View {
        if #available(iOS 26, *) {
            NavigationLink(value: route, label: label)
                .navigationLinkIndicatorVisibility(.hidden)
        } else {
            label()
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
                .background {
                    NavigationLink(value: route) { Color.clear }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .contentShape(Rectangle())
                        .opacity(0)
                        .accessibilityHidden(true)
                }
                .accessibilityElement(children: .combine)
                .accessibilityAddTraits(.isButton)
        }
    }
}

extension LetterIndexedList where Header == EmptyView {
    init(items: [Item], id: KeyPath<Item, ID>, sections: [LetterIndexSection]?, @ViewBuilder row: @escaping (Int, Item) -> Row) {
        self.init(items: items, id: id, sections: sections, header: { EmptyView() }, row: row)
    }
}

private struct IndexedItem<Item, ID: Hashable> {
    let index: Int
    let item: Item
    let id: ID
}

extension View {
    /// The index for `sections` down the trailing edge, a `LetterIndexStrip`, which calls `scrollTo` with the section
    /// picked. No index without sections.
    @ViewBuilder
    func letterIndex(_ sections: [LetterIndexSection]?, scrollTo: @escaping (LetterIndexSection) -> Void) -> some View {
        if let sections {
            safeAreaInset(edge: .trailing, spacing: 0) {
                LetterIndexStrip(sections: sections.filter(\.isIndexed), onSelect: scrollTo)
            }
        } else {
            self
        }
    }
}

/// The right-edge letters: touch or drag along them to jump to a letter,
/// with a selection tick as each one passes. VoiceOver reads it as one adjustable control: swipe up or down to step
/// through the letters.
struct LetterIndexStrip: View {
    let sections: [LetterIndexSection]
    let onSelect: (LetterIndexSection) -> Void

    /// The letter last jumped to: highlighted while a finger is on the strip, and VoiceOver's value.
    @State private var current: String?
    @State private var isDragging = false
    /// Where the letters sit in the strip's own space, the one the drag reports in.
    @State private var lettersFrame: CGRect = .zero
    /// One letter's height at the current text size: caption2's line.
    @ScaledMetric(relativeTo: .caption2) private var letterHeight: CGFloat = 14
    @ScaledMetric(relativeTo: .caption2) private var width: CGFloat = LetterIndexStrip.letterWidth

    /// A letter's column at the default text size.
    static let letterWidth: CGFloat = 20
    /// The whole strip's width at the default text size, the letters and their padding: the width it takes from
    /// the content beside it (`LibraryGrid.columnCount`).
    static let baseWidth = letterWidth + Spacing.xsmall * 2

    var body: some View {
        VStack(spacing: 0) {
            ForEach(sections) { section in
                Text(section.letter)
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(isDragging && section.letter == current ? AnyShapeStyle(.primary) : AnyShapeStyle(.tint))
                    .minimumScaleFactor(0.5)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .frame(width: width)
        .frame(maxHeight: letterHeight * CGFloat(sections.count))
        .onGeometryChange(for: CGRect.self) { $0.frame(in: .named(Self.space)) } action: { lettersFrame = $0 }
        .padding(.horizontal, Spacing.xsmall)
        .padding(.vertical, Spacing.small)
        .frame(maxHeight: .infinity)
        // The whole trailing edge takes the drag, as in Contacts: above or below the letters picks the first or last.
        .contentShape(Rectangle())
        .coordinateSpace(.named(Self.space))
        .gesture(
            DragGesture(minimumDistance: 0, coordinateSpace: .named(Self.space))
                .onChanged { value in
                    if !isDragging {
                        // A new touch jumps even to the letter the last one ended on: the list may have moved since.
                        isDragging = true
                        current = nil
                    }
                    select(at: value.location.y)
                }
                .onEnded { _ in isDragging = false }
        )
        .sensoryFeedback(.selection, trigger: current) { _, new in new != nil }
        .dynamicTypeSize(...DynamicTypeSize.xxLarge)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Section index")
        .accessibilityValue(current ?? "")
        .accessibilityAdjustableAction { direction in
            let position = sections.firstIndex { $0.letter == current } ?? -1
            switch direction {
            case .increment: pick(min(position + 1, sections.count - 1))
            case .decrement: pick(max(position - 1, 0))
            @unknown default: break
            }
        }
        .accessibilityIdentifier("library.sectionIndex")
    }

    private static let space = "letterIndexStrip"

    /// The letter under `y` in the strip's space.
    private func select(at y: CGFloat) {
        guard lettersFrame.height > 0, !sections.isEmpty else { return }
        let position = Int(((y - lettersFrame.minY) / lettersFrame.height * CGFloat(sections.count)).rounded(.down))
        pick(min(max(position, 0), sections.count - 1))
    }

    private func pick(_ position: Int) {
        guard sections.indices.contains(position) else { return }
        let section = sections[position]
        guard section.letter != current else { return }
        current = section.letter
        onSelect(section)
    }
}
