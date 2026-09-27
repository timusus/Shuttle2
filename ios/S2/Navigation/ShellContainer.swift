import SwiftUI

/// Which root container the shell draws, from the layout tier and what the OS offers.
enum ShellContainer: Hashable {
    /// Compact: a bottom tab bar.
    case tabBar
    /// Regular and wide on iOS 18+: `TabView` in the `.sidebarAdaptable` style, a sidebar the user can
    /// collapse to a top tab bar.
    case sidebarTabs
    /// Regular and wide on iOS 17: `NavigationSplitView` with a sidebar list.
    case splitView

    static func resolve(for tier: LayoutTier) -> ShellContainer {
        guard tier != .compact else { return .tabBar }
        if #available(iOS 18, *) {
            return .sidebarTabs
        }
        return .splitView
    }

    /// Set on the container so tests (and UI automation) can tell the layouts apart.
    var accessibilityIdentifier: String {
        switch self {
        case .tabBar: "shell.tabBar"
        case .sidebarTabs: "shell.sidebarTabs"
        case .splitView: "shell.splitView"
        }
    }
}
