import SwiftUI

/// The root: measures the window, resolves the `LayoutTier` and hands it to `AppShell`. Owns the Now
/// Playing presentation, which has to hang off the root: a cover attached inside a `safeAreaInset` (where
/// the mini player lives) does not present.
struct ContentView: View {
    @State private var navigator = Navigator()
    @State private var showNowPlaying = false
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    /// Measured here, never read from `UIScreen`: Split View and Stage Manager resize the window
    /// without touching the screen.
    @State private var containerWidth: CGFloat = 0

    var body: some View {
        let tier = LayoutTier.resolve(horizontalSizeClass: horizontalSizeClass, containerWidth: containerWidth)
        AppShell(tier: tier, navigator: navigator, showNowPlaying: $showNowPlaying)
            .environment(\.layoutTier, tier)
            .nowPlayingPresentation(isPresented: $showNowPlaying, tier: tier) {
                NowPlayingView()
            }
            .onGeometryChange(for: CGFloat.self) { proxy in
                proxy.size.width
            } action: { width in
                containerWidth = width
            }
    }
}

/// The adaptive shell (Android: `ui/shell`): the three tabs as a tab bar on compact and a sidebar above
/// it, each tab its own `NavigationStack`, the mini player inset at the bottom of every screen.
struct AppShell: View {
    let tier: LayoutTier
    let container: ShellContainer
    let navigator: Navigator
    @Binding var showNowPlaying: Bool

    init(
        tier: LayoutTier,
        container: ShellContainer? = nil,
        navigator: Navigator,
        showNowPlaying: Binding<Bool>
    ) {
        self.tier = tier
        self.container = container ?? .resolve(for: tier)
        self.navigator = navigator
        self._showNowPlaying = showNowPlaying
    }

    var body: some View {
        Group {
            switch container {
            case .tabBar:
                tabBarLayout
            case .sidebarTabs:
                if #available(iOS 18, *) {
                    sidebarTabLayout
                } else {
                    splitViewLayout
                }
            case .splitView:
                splitViewLayout
            }
        }
        .accessibilityIdentifier(container.accessibilityIdentifier)
    }

    // MARK: - Compact (tab bar)

    private var tabBarLayout: some View {
        @Bindable var navigator = navigator
        return TabView(selection: $navigator.selectedTab) {
            ForEach(AppTab.allCases, id: \.self) { tab in
                stack(for: tab)
                    .tag(tab)
                    .tabItem { Label(tab.title, systemImage: tab.systemImage) }
            }
        }
    }

    // MARK: - Regular and wide (sidebar)

    @available(iOS 18, *)
    private var sidebarTabLayout: some View {
        @Bindable var navigator = navigator
        return TabView(selection: $navigator.selectedTab) {
            ForEach(AppTab.allCases, id: \.self) { tab in
                Tab(tab.title, systemImage: tab.systemImage, value: tab) {
                    stack(for: tab)
                }
            }
        }
        .tabViewStyle(.sidebarAdaptable)
    }

    /// iOS 17 fallback: a sidebar list driving the same `selectedTab`.
    private var splitViewLayout: some View {
        NavigationSplitView {
            List(selection: sidebarSelection) {
                ForEach(AppTab.allCases, id: \.self) { tab in
                    Label(tab.title, systemImage: tab.systemImage)
                        .tag(tab)
                }
            }
            .navigationTitle("S2")
        } detail: {
            stack(for: navigator.selectedTab)
        }
    }

    /// `List(selection:)` deselects to `nil`; the shell always has a tab, so a `nil` is ignored.
    private var sidebarSelection: Binding<AppTab?> {
        Binding(
            get: { navigator.selectedTab },
            set: { if let tab = $0 { navigator.selectedTab = tab } }
        )
    }

    // MARK: - Tab stacks

    /// One `NavigationStack` per tab, bound to that tab's path on `Navigator`. Shared by every layout, so
    /// a tier change rebinds the same paths to the same roots.
    @ViewBuilder
    private func stack(for tab: AppTab) -> some View {
        @Bindable var navigator = navigator
        switch tab {
        case .home:
            NavigationStack(path: $navigator.homePath) {
                withMiniPlayer(HomeView())
            }
        case .library:
            NavigationStack(path: $navigator.libraryPath) {
                withMiniPlayer(LibraryView())
            }
        case .search:
            NavigationStack(path: $navigator.searchPath) {
                withMiniPlayer(SearchView())
            }
        }
    }

    /// Attaches the mini player to a screen INSIDE its `NavigationStack`. Attached to the stack itself
    /// (Shuttle Podcasts found) the bar draws but reserves no safe area and receives no touches.
    private func withMiniPlayer(_ content: some View) -> some View {
        content.safeAreaInset(edge: .bottom, spacing: 0) {
            MiniPlayerView(showNowPlaying: $showNowPlaying)
        }
    }
}
