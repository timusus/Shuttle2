import SwiftUI

/// An in-content section header: a rounded bold title (`.s2SectionTitle`) and, when the section has more than
/// it shows, a trailing "See All" with a chevron. For Home's shelves, a detail screen's "Albums", the queue's
/// "Up Next". Outside a `List`, give it the screen's horizontal inset; inside one it takes the row's.
///
/// ```swift
/// SectionHeader("Albums", seeAll: .libraryCategory(.albums))  // pushes a route
/// SectionHeader("Up Next")                                  // title only
/// SectionHeader("Most Played") { showAll() }                // an action
/// ```
struct SectionHeader: View {
    let title: String
    private let seeAll: SeeAll?

    private enum SeeAll {
        case route(Route)
        case action(() -> Void)
    }

    init(_ title: String) {
        self.title = title
        seeAll = nil
    }

    /// See All pushes `route` onto the enclosing `NavigationStack`.
    init(_ title: String, seeAll route: Route) {
        self.title = title
        seeAll = .route(route)
    }

    /// See All runs `action`.
    init(_ title: String, seeAllAction action: @escaping () -> Void) {
        self.title = title
        seeAll = .action(action)
    }

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: Spacing.small) {
            Text(title)
                .font(.s2SectionTitle)
                .foregroundStyle(.primary)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: Spacing.small)
            switch seeAll {
            case .route(let route):
                NavigationLink(value: route) { seeAllLabel }
                    .buttonStyle(.plain)
            case .action(let action):
                Button(action: action) { seeAllLabel }
                    .buttonStyle(.plain)
            case nil:
                EmptyView()
            }
        }
    }

    private var seeAllLabel: some View {
        HStack(spacing: Spacing.xsmall) {
            Text("See All")
            Image(systemName: "chevron.right")
                .font(.footnote.weight(.semibold))
                .imageScale(.small)
        }
        .font(.subheadline.weight(.medium))
        .foregroundStyle(.tint)
        .contentShape(Rectangle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("See All \(title)")
    }
}
