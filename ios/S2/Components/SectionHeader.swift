import SwiftUI

/// An in-content section header: a rounded bold title (`.s2SectionTitle`) and optionally a one-line subtitle under it
/// saying what the section is. When the section has a destination the whole title block is the tap target, with a small
/// chevron after the title, as in Apple Music and Podcasts. For Home's shelves, a detail screen's "Albums", the queue's
/// "Up Next". Outside a `List`, give it the screen's horizontal inset; inside one it takes the row's.
///
/// ```swift
/// SectionHeader("Albums", opens: .libraryCategory(.albums))  // pushes a route
/// SectionHeader("Up Next")                                   // title only
/// SectionHeader("Rediscover", subtitle: "Albums you haven't played in a while")
/// SectionHeader("Most Played") { showAll() }                 // an action
/// SectionHeader("Heavy Rotation").play { playAll() }         // long-press menu: Play Heavy Rotation
/// ```
struct SectionHeader: View {
    let title: String
    let subtitle: String?
    private let destination: Destination?
    private var playAction: (() -> Void)?

    private enum Destination {
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
        destination = nil
    }

    init(_ title: String, style: Style) {
        self.title = title
        subtitle = nil
        destination = nil
        self.style = style
    }

    /// Tapping the header pushes `route` onto the enclosing `NavigationStack`.
    init(_ title: String, subtitle: String? = nil, opens route: Route) {
        self.title = title
        self.subtitle = subtitle
        destination = .route(route)
    }

    /// Tapping the header runs `action`.
    init(_ title: String, subtitle: String? = nil, action: @escaping () -> Void) {
        self.title = title
        self.subtitle = subtitle
        destination = .action(action)
    }

    /// Adds "Play <title>" to the header's long-press menu and as a VoiceOver action. There is no visible button.
    func play(_ action: @escaping () -> Void) -> SectionHeader {
        var copy = self
        copy.playAction = action
        return copy
    }

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
        let header = destinationView
        if let playAction {
            let name = "Play \(title)"
            header
                .contextMenu {
                    Button(action: playAction) { Label(name, systemImage: "play.fill") }
                }
                .accessibilityAction(named: name, playAction)
        } else {
            header
        }
    }

    @ViewBuilder private var destinationView: some View {
        switch destination {
        case .route(let route):
            NavigationLink(value: route) { titles(chevron: true) }
                .buttonStyle(.plain)
        case .action(let action):
            Button(action: action) { titles(chevron: true) }
                .buttonStyle(.plain)
        case nil:
            titles(chevron: false)
        }
    }

    private func titles(chevron: Bool) -> some View {
        VStack(alignment: .leading, spacing: Spacing.tiny) {
            HStack(spacing: Spacing.xsmall) {
                Text(title)
                    .textRole(.sectionHeader)
                    .foregroundStyle(.primary)
                if chevron {
                    Image(systemName: "chevron.right")
                        .font(.footnote.weight(.semibold))
                        .imageScale(.small)
                        .foregroundStyle(.s2TextSecondary)
                }
            }
            if let subtitle {
                Text(subtitle)
                    .textRole(.rowSubtitle)
                    .foregroundStyle(.s2TextSecondary)
                    .lineLimit(1)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
        .modifier(HeaderAccessibility(title: title, subtitle: subtitle, linked: chevron))
    }
}

/// A linked header reads as one button naming the section; an unlinked one stays a plain heading.
private struct HeaderAccessibility: ViewModifier {
    let title: String
    let subtitle: String?
    let linked: Bool

    func body(content: Content) -> some View {
        if linked {
            content
                .accessibilityElement(children: .ignore)
                .accessibilityLabel([title, subtitle].compactMap { $0 }.joined(separator: ", "))
                .accessibilityHint("See all")
                .accessibilityAddTraits([.isButton, .isHeader])
        } else {
            content.accessibilityElement(children: .contain)
                .accessibilityAddTraits(.isHeader)
        }
    }
}
