import SwiftUI

/// An in-content section header: a rounded bold title (`.s2SectionTitle`), optionally a one-line subtitle under it
/// saying what the section is, and, when the section has more than it shows, a trailing "See All" with a chevron. For Home's shelves, a detail screen's "Albums", the queue's
/// "Up Next". Outside a `List`, give it the screen's horizontal inset; inside one it takes the row's.
///
/// ```swift
/// SectionHeader("Albums", seeAll: .libraryCategory(.albums))  // pushes a route
/// SectionHeader("Up Next")                                  // title only
/// SectionHeader("Rediscover", subtitle: "Albums you haven't played in a while")
/// SectionHeader("Most Played") { showAll() }                // an action
/// ```
struct SectionHeader: View {
    let title: String
    let subtitle: String?
    private let seeAll: SeeAll?

    private enum SeeAll {
        case route(Route)
        case action(() -> Void)
    }

    init(_ title: String, subtitle: String? = nil) {
        self.title = title
        self.subtitle = subtitle
        seeAll = nil
    }

    /// See All pushes `route` onto the enclosing `NavigationStack`.
    init(_ title: String, subtitle: String? = nil, seeAll route: Route) {
        self.title = title
        self.subtitle = subtitle
        seeAll = .route(route)
    }

    /// See All runs `action`.
    init(_ title: String, subtitle: String? = nil, seeAllAction action: @escaping () -> Void) {
        self.title = title
        self.subtitle = subtitle
        seeAll = .action(action)
    }

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        // At the accessibility sizes See All goes under the title, as in Podcasts: side by side, a long title
        // hyphenates into fragments beside it.
        let stacked = dynamicTypeSize.isAccessibilitySize && seeAll != nil
        let layout = stacked
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: Spacing.xsmall))
            : AnyLayout(HStackLayout(alignment: .firstTextBaseline, spacing: Spacing.small))
        layout {
            VStack(alignment: .leading, spacing: Spacing.tiny) {
                Text(title)
                    .font(.s2SectionTitle)
                    .foregroundStyle(.primary)
                    .accessibilityAddTraits(.isHeader)
                if let subtitle {
                    Text(subtitle)
                        .font(.subheadline)
                        .foregroundStyle(.s2TextSecondary)
                        .lineLimit(1)
                }
            }
            if !stacked {
                Spacer(minLength: Spacing.small)
            }
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
