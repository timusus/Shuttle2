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
            modifier(LetterIndexModifier(sections: sections.filter(\.isIndexed), scrollTo: scrollTo))
        } else {
            self
        }
    }
}

/// `letterIndex`: the list gives up the strip's width with a clear inset, and the strip is an overlay hanging from
/// the top that ignores the bottom safe area and keeps its own clearance above the bottom edge instead
/// (`LetterIndexClearance`), so it doesn't move when scrolling minimises the iOS 26 tab bar and the bottom accessory
/// (the mini player) moves in beside it (#674).
private struct LetterIndexModifier: ViewModifier {
    let sections: [LetterIndexSection]
    let scrollTo: (LetterIndexSection) -> Void

    @Environment(\.isMiniPlayerVisible) private var isMiniPlayerVisible
    @State private var clearance = LetterIndexClearance()
    @State private var size: CGSize = .zero

    /// Long enough for the mini player to slide in or out, or the accessory to appear, before the clearance holds.
    private static let settleDelay: Duration = .milliseconds(600)

    func body(content: Content) -> some View {
        content
            .safeAreaInset(edge: .trailing, spacing: 0) {
                Color.clear.frame(width: LetterIndexStrip.baseWidth).accessibilityHidden(true)
            }
            .onGeometryChange(for: Measure.self) { Measure(size: $0.size, bottomInset: $0.safeAreaInsets.bottom) } action: {
                size = $0.size
                clearance.measure($0.bottomInset)
            }
            // What sits below changes for real when the mini player comes or goes, or the screen rotates or resizes:
            // follow it until it settles, then hold the tallest inset again.
            .task(id: SettleKey(size: size, isMiniPlayerVisible: isMiniPlayerVisible)) {
                clearance.follow()
                try? await Task.sleep(for: Self.settleDelay)
                clearance.hold()
            }
            .overlay(alignment: .topTrailing) {
                LetterIndexStrip(sections: sections, onSelect: scrollTo)
                    .padding(.bottom, clearance.value)
                    .ignoresSafeArea(.container, edges: .bottom)
            }
    }

    private struct Measure: Equatable {
        let size: CGSize
        let bottomInset: CGFloat
    }

    private struct SettleKey: Equatable {
        let size: CGSize
        let isMiniPlayerVisible: Bool
    }
}

/// The room the letter strip keeps below itself: the list's bottom safe-area inset (the tab bar, and the mini player
/// above it or in its bottom accessory), measured rather than guessed. While holding it's the tallest inset seen, so
/// the strip keeps its frame as the iOS 26 tab bar minimises and expands under it and still clears both expanded;
/// while following (the mini player coming or going, a rotation) it's the inset as it is, so it reserves only what's
/// really there.
struct LetterIndexClearance: Equatable {
    private(set) var value: CGFloat = 0
    private(set) var isFollowing = true
    private var current: CGFloat = 0

    mutating func measure(_ bottomInset: CGFloat) {
        current = bottomInset
        value = isFollowing ? bottomInset : max(value, bottomInset)
    }

    /// Track the inset as it changes, starting from the one there now.
    mutating func follow() {
        isFollowing = true
        value = current
    }

    /// Keep the tallest inset from here on.
    mutating func hold() {
        isFollowing = false
    }
}

/// One of the strip's rows: a letter standing for its own section, or, where there isn't room for every letter, a
/// dot standing for the ones skipped between two letters, as UIKit's section index does.
struct LetterIndexEntry: Equatable {
    static let skipped = "•"

    let label: String
    /// The positions, in the strip's sections, this row stands for.
    let sections: Range<Int>

    /// The rows for `letters` in at most `slots` rows (never fewer than three, the first letter, a dot and the last):
    /// all of them when they fit; otherwise evenly spaced letters from the first to the last, alternating with dots.
    static func entries(_ letters: [String], slots: Int) -> [LetterIndexEntry] {
        let count = letters.count
        guard count > max(slots, 2) else {
            return letters.indices.map { LetterIndexEntry(label: letters[$0], sections: $0 ..< $0 + 1) }
        }
        let rows = max(3, slots.isMultiple(of: 2) ? slots - 1 : slots)
        let shown = (rows + 1) / 2
        let picks = (0 ..< shown).map { Int((Double($0) * Double(count - 1) / Double(shown - 1)).rounded()) }
        var entries: [LetterIndexEntry] = []
        for (position, pick) in picks.enumerated() {
            if position > 0, picks[position - 1] + 1 < pick {
                entries.append(LetterIndexEntry(label: skipped, sections: picks[position - 1] + 1 ..< pick))
            }
            entries.append(LetterIndexEntry(label: letters[pick], sections: pick ..< pick + 1))
        }
        return entries
    }

    /// The section at `fraction` (0 at the top, 1 at the bottom) down `entries`: a dot's share of the strip is split
    /// between the letters it skips, so a drag still passes through every section.
    static func section(at fraction: CGFloat, in entries: [LetterIndexEntry]) -> Int? {
        guard !entries.isEmpty else { return nil }
        let scaled = min(max(fraction, 0), 1) * CGFloat(entries.count)
        let row = min(Int(scaled), entries.count - 1)
        let range = entries[row].sections
        let within = min(max(scaled - CGFloat(row), 0), 1)
        return min(range.lowerBound + Int(within * CGFloat(range.count)), range.upperBound - 1)
    }
}

/// The right-edge letters: touch or drag along them to jump to a letter,
/// with a selection tick as each one passes. VoiceOver reads it as one adjustable control: swipe up or down to step
/// through the letters. Where the height it's given can't fit every letter at `minimumLetterHeight`, it shows every
/// other one with dots between (`LetterIndexEntry`).
struct LetterIndexStrip: View {
    let sections: [LetterIndexSection]
    let onSelect: (LetterIndexSection) -> Void

    /// The section last jumped to: highlighted while a finger is on the strip, and VoiceOver's value.
    @State private var current: Int?
    @State private var isDragging = false
    /// Where the letters sit in the strip's own space, the one the drag reports in.
    @State private var lettersFrame: CGRect = .zero
    /// The height the strip is given, down to the clearance below it; zero until measured.
    @State private var availableHeight: CGFloat = 0
    /// One letter's height at the current text size: caption2's line.
    @ScaledMetric(relativeTo: .caption2) private var letterHeight: CGFloat = 14
    @ScaledMetric(relativeTo: .caption2) private var width: CGFloat = LetterIndexStrip.letterWidth

    /// A letter's column at the default text size.
    static let letterWidth: CGFloat = 20
    /// The whole strip's width at the default text size, the letters and their padding: the width it takes from
    /// the content beside it (`LibraryGrid.columnCount`).
    static let baseWidth = letterWidth + Spacing.xsmall * 2
    /// The shortest a letter's row gets, to stay legible and hittable, before the strip drops letters instead.
    static let minimumLetterHeight: CGFloat = 11

    /// How many rows fit in `height`, the strip's padding included; unlimited until the height is known.
    static func slots(height: CGFloat) -> Int {
        guard height > 0 else { return .max }
        return max(0, Int(((height - Spacing.small * 2) / minimumLetterHeight).rounded(.down)))
    }

    var body: some View {
        let entries = LetterIndexEntry.entries(sections.map(\.letter), slots: Self.slots(height: availableHeight))
        VStack(spacing: 0) {
            ForEach(entries, id: \.sections.lowerBound) { entry in
                Text(entry.label)
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(isDragging && current.map(entry.sections.contains) == true ? AnyShapeStyle(.primary) : AnyShapeStyle(.tint))
                    .minimumScaleFactor(0.5)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .frame(width: width)
        .frame(maxHeight: letterHeight * CGFloat(entries.count))
        .onGeometryChange(for: CGRect.self) { $0.frame(in: .named(Self.space)) } action: { lettersFrame = $0 }
        .padding(.horizontal, Spacing.xsmall)
        .padding(.vertical, Spacing.small)
        // The padded strip takes the drag: just above or below the letters picks the first or last.
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
                    select(at: value.location.y, in: entries)
                }
                .onEnded { _ in isDragging = false }
        )
        .sensoryFeedback(.selection, trigger: current) { _, new in new != nil }
        .dynamicTypeSize(...DynamicTypeSize.xxLarge)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Section index")
        .accessibilityValue(current.flatMap { sections.indices.contains($0) ? sections[$0].letter : nil } ?? "")
        .accessibilityAdjustableAction { direction in
            let position = current ?? -1
            switch direction {
            case .increment: pick(min(position + 1, sections.count - 1))
            case .decrement: pick(max(position - 1, 0))
            @unknown default: break
            }
        }
        .accessibilityIdentifier("library.sectionIndex")
        // Hangs from the top of the height it's given, which it measures to know how many letters fit.
        .frame(maxHeight: .infinity, alignment: .top)
        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { availableHeight = $0 }
    }

    private static let space = "letterIndexStrip"

    /// The section under `y` in the strip's space.
    private func select(at y: CGFloat, in entries: [LetterIndexEntry]) {
        guard lettersFrame.height > 0 else { return }
        if let position = LetterIndexEntry.section(at: (y - lettersFrame.minY) / lettersFrame.height, in: entries) {
            pick(position)
        }
    }

    private func pick(_ position: Int) {
        guard sections.indices.contains(position), position != current else { return }
        current = position
        onSelect(sections[position])
    }
}
