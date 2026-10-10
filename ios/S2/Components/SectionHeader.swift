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
    private var playAction: (() -> Void)?

    private enum SeeAll {
        case route(Route)
        case action(() -> Void)
    }

    /// `.disc` is the small uppercase label over a run of rows (an album's "Disc 2"); it takes no subtitle or actions.
    enum Style {
        case standard
        case disc
    }

    private var style = Style.standard

    init(_ title: String, subtitle: String? = nil) {
        self.title = title
        self.subtitle = subtitle
        seeAll = nil
    }

    init(_ title: String, style: Style) {
        self.title = title
        subtitle = nil
        seeAll = nil
        self.style = style
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

    /// Adds a trailing Play button (44pt, "Play <title>") that runs `action`, after See All when there is one.
    func play(_ action: @escaping () -> Void) -> SectionHeader {
        var copy = self
        copy.playAction = action
        return copy
    }

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        switch style {
        case .standard: standardBody
        case .disc:
            Text(title)
                .textRole(.groupHeader)
                .foregroundStyle(.s2TextSecondary)
                .textCase(.uppercase)
                .accessibilityAddTraits(.isHeader)
        }
    }

    @ViewBuilder private var standardBody: some View {
        // At the accessibility sizes See All goes under the title, as in Podcasts: side by side, a long title
        // hyphenates into fragments beside it.
        let stacked = dynamicTypeSize.isAccessibilitySize && seeAll != nil
        let layout = stacked
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: Spacing.xsmall))
            : AnyLayout(HStackLayout(alignment: .firstTextBaseline, spacing: Spacing.small))
        layout {
            VStack(alignment: .leading, spacing: Spacing.tiny) {
                Text(title)
                    .textRole(.sectionHeader)
                    .foregroundStyle(.primary)
                    .accessibilityAddTraits(.isHeader)
                if let subtitle {
                    Text(subtitle)
                        .textRole(.rowSubtitle)
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
            if let playAction {
                Button(action: playAction) {
                    Image(systemName: "play.fill")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.tint)
                        .frame(minWidth: 44, minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Play \(title)")
                .accessibilityIdentifier("sectionHeader.play")
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
