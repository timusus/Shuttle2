import Shared
import SwiftUI

/// The Library lists' A–Z section index (#627), Music and Contacts' right-edge letters rather than Android's draggable
/// thumb. The letters come from the shared UiStates' `letterIndex` (`letterSections` in `:android:domain`): the same
/// key the shared sort compares, so a name sort's letters run in order, with '#' for digits, symbols and scripts
/// without a short alphabet. A sort that isn't by name has no `letterIndex`, and so no index.
///
/// iOS 26 lists use the native index (`sectionIndexLabel`); iOS 17 and 18 lists, and grids on every version, use
/// `LetterIndexStrip`, which scrolls to a section's first item.

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
/// same rows unsectioned. `row` gets each item's position in `items`.
struct LetterIndexedList<Item, ID: Hashable, Row: View>: View {
    let items: [Item]
    let id: KeyPath<Item, ID>
    let sections: [LetterIndexSection]?
    @ViewBuilder let row: (Int, Item) -> Row

    var body: some View {
        if let sections {
            ScrollViewReader { proxy in
                List {
                    ForEach(sections) { section in
                        Section {
                            ForEach(section.rows.map { IndexedItem(index: $0, item: items[$0], id: items[$0][keyPath: id]) }, id: \.id) {
                                row($0.index, $0.item)
                            }
                        }
                        .letterIndexLabel(section)
                    }
                }
                .listStyle(.plain)
                .letterIndex(sections, native: true) { proxy.scrollTo($0.anchor, anchor: .top) }
            }
        } else {
            List {
                ForEach(items.indices.map { IndexedItem(index: $0, item: items[$0], id: items[$0][keyPath: id]) }, id: \.id) {
                    row($0.index, $0.item)
                }
            }
            .listStyle(.plain)
        }
    }
}

private struct IndexedItem<Item, ID: Hashable> {
    let index: Int
    let item: Item
    let id: ID
}

extension View {
    /// The section's entry in the native list index, from iOS 26.
    @ViewBuilder
    fileprivate func letterIndexLabel(_ section: LetterIndexSection) -> some View {
        if #available(iOS 26, *) {
            sectionIndexLabel(section.isIndexed ? section.letter : nil)
        } else {
            self
        }
    }

    /// The index for `sections` down the trailing edge: the native one on an iOS 26 list (`native`), else
    /// `LetterIndexStrip`, which calls `scrollTo` with the section picked. No index without sections.
    @ViewBuilder
    func letterIndex(_ sections: [LetterIndexSection]?, native: Bool = false, scrollTo: @escaping (LetterIndexSection) -> Void) -> some View {
        if let sections {
            if #available(iOS 26, *), native {
                listSectionIndexVisibility(.visible)
            } else {
                safeAreaInset(edge: .trailing, spacing: 0) {
                    LetterIndexStrip(sections: sections.filter(\.isIndexed), onSelect: scrollTo)
                }
            }
        } else {
            self
        }
    }
}

/// The right-edge letters where the native index isn't available: touch or drag along them to jump to a letter,
/// with a selection tick as each one passes. VoiceOver reads it as one adjustable control: swipe up or down to step
/// through the letters.
struct LetterIndexStrip: View {
    let sections: [LetterIndexSection]
    let onSelect: (LetterIndexSection) -> Void

    /// The letter last jumped to: highlighted while a finger is on the strip, and VoiceOver's value.
    @State private var current: String?
    @State private var isDragging = false
    @State private var height: CGFloat = 0
    /// One letter's height at the current text size: caption2's line.
    @ScaledMetric(relativeTo: .caption2) private var letterHeight: CGFloat = 14
    @ScaledMetric(relativeTo: .caption2) private var width: CGFloat = 20

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
        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { height = $0 }
        .padding(.horizontal, Spacing.xsmall)
        .padding(.vertical, Spacing.small)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { value in
                    if !isDragging {
                        // A new touch jumps even to the letter the last one ended on: the list may have moved since.
                        isDragging = true
                        current = nil
                    }
                    select(at: value.location.y - Spacing.small)
                }
                .onEnded { _ in isDragging = false }
        )
        .frame(maxHeight: .infinity)
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

    /// The letter under `y`, measured down from the first letter's top.
    private func select(at y: CGFloat) {
        guard height > 0, !sections.isEmpty else { return }
        let position = Int((y / height * CGFloat(sections.count)).rounded(.down))
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
