import SwiftUI

/// The root: measures the window, resolves the `LayoutTier` and hands it to `AppShell`. Owns the Now
/// Playing presentation, which has to hang off the root: a cover attached inside a `safeAreaInset` (where
/// the mini player lives) does not present. Restores every path from `@SceneStorage` once at launch and
/// keeps it in sync as the paths change, so a relaunch lands where the user left off (Android's back
/// stacks survive process death the same way).
struct ContentView: View {
    @State private var navigator = Navigator()
    @State private var showNowPlaying = false
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    /// Measured here, never read from `UIScreen`: Split View and Stage Manager resize the window
    /// without touching the screen.
    @State private var containerWidth: CGFloat = 0

    @SceneStorage("nav.home") private var homeStorage = Navigator.StoredPath()
    @SceneStorage("nav.library") private var libraryStorage = Navigator.StoredPath()
    @SceneStorage("nav.search") private var searchStorage = Navigator.StoredPath()
    @SceneStorage("nav.libraryCategories") private var categoryStorage = Navigator.StoredCategoryPaths()

    var body: some View {
        let tier = LayoutTier.resolve(horizontalSizeClass: horizontalSizeClass, containerWidth: containerWidth)
        AppShell(tier: tier, navigator: navigator, showNowPlaying: $showNowPlaying)
            .environment(\.layoutTier, tier)
            // For screens that push without a `NavigationLink`, such as Sources after its type picker closes.
            .environment(navigator)
            .nowPlayingPresentation(isPresented: $showNowPlaying, tier: tier) {
                // Go to Album/Artist closes Now Playing and pushes onto the selected root.
                NowPlayingView(onOpen: { route in navigator.open(route) })
            }
            .sheet(isPresented: $navigator.showsSettings) {
                SettingsSheet(navigator: navigator, showNowPlaying: $showNowPlaying)
            }
            .onGeometryChange(for: CGFloat.self) { proxy in
                proxy.size.width
            } action: { width in
                containerWidth = width
            }
            .onChange(of: tier) { _, newTier in
                navigator.normalizeSelection(for: newTier)
            }
            .onAppear {
                navigator.restore(home: homeStorage, library: libraryStorage, search: searchStorage, categories: categoryStorage)
            }
            .onChange(of: navigator.homePath) { _, new in homeStorage = Navigator.StoredPath(new) }
            .onChange(of: navigator.libraryPath) { _, new in libraryStorage = Navigator.StoredPath(new) }
            .onChange(of: navigator.searchPath) { _, new in searchStorage = Navigator.StoredPath(new) }
            .onChange(of: navigator.categoryPathsSnapshot) { _, new in categoryStorage = new }
    }
}

/// The adaptive shell (Android: `ui/shell`): Home, Library and Search as a tab bar on compact, and as a
/// sidebar on regular/wide with the library categories promoted into it in place of a "Library" tab
/// (`docs/architecture/ios-port/phase-5-ios-app.md` section 2). Each root has its own `NavigationStack`,
/// the mini player inset at the bottom of every screen, and a wide-only inspector slot for phase 6.
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
        .modifier(PlayerInspectorModifier(tier: tier, navigator: navigator))
    }

    // MARK: - Compact (tab bar)

    private var tabBarLayout: some View {
        @Bindable var navigator = navigator
        return TabView(selection: $navigator.selection) {
            ForEach(AppTab.allCases, id: \.self) { tab in
                stack(for: tab)
                    .tag(RootSelection.tab(tab))
                    .tabItem { Label(tab.title, systemImage: tab.systemImage) }
            }
        }
    }

    // MARK: - Regular and wide (sidebar)

    @available(iOS 18, *)
    private var sidebarTabLayout: some View {
        @Bindable var navigator = navigator
        return TabView(selection: $navigator.selection) {
            Tab(AppTab.home.title, systemImage: AppTab.home.systemImage, value: RootSelection.tab(.home)) {
                stack(for: .home)
            }
            TabSection(AppTab.library.title) {
                ForEach(LibraryCategory.allCases, id: \.self) { category in
                    Tab(category.title, systemImage: category.systemImage, value: RootSelection.libraryCategory(category)) {
                        categoryStack(for: category)
                    }
                }
            }
            Tab(AppTab.search.title, systemImage: AppTab.search.systemImage, value: RootSelection.tab(.search)) {
                stack(for: .search)
            }
        }
        .tabViewStyle(.sidebarAdaptable)
    }

    /// iOS 17 fallback: a sidebar list with the library categories inline, driving the same `selection`.
    private var splitViewLayout: some View {
        NavigationSplitView {
            List(selection: sidebarListSelection) {
                Label(AppTab.home.title, systemImage: AppTab.home.systemImage)
                    .tag(RootSelection.tab(.home))
                Section(AppTab.library.title) {
                    ForEach(LibraryCategory.allCases, id: \.self) { category in
                        Label(category.title, systemImage: category.systemImage)
                            .tag(RootSelection.libraryCategory(category))
                    }
                }
                Label(AppTab.search.title, systemImage: AppTab.search.systemImage)
                    .tag(RootSelection.tab(.search))
            }
            .navigationTitle("S2")
        } detail: {
            detailStack
        }
    }

    /// `List(selection:)` deselects to `nil`; the shell always has a selection, so a `nil` is ignored.
    private var sidebarListSelection: Binding<RootSelection?> {
        Binding(
            get: { navigator.selection },
            set: { newValue in if let newValue { navigator.selection = newValue } }
        )
    }

    @ViewBuilder
    private var detailStack: some View {
        switch navigator.selection {
        case .tab(.home): stack(for: .home)
        case .tab(.search): stack(for: .search)
        case .tab(.library): stack(for: .library)
        case .libraryCategory(let category): categoryStack(for: category)
        }
    }

    // MARK: - Stacks

    /// One `NavigationStack` per tab, bound to that tab's path on `Navigator`. Shared by every layout, so
    /// a tier change rebinds the same paths to the same roots.
    @ViewBuilder
    private func stack(for tab: AppTab) -> some View {
        @Bindable var navigator = navigator
        switch tab {
        case .home:
            NavigationStack(path: $navigator.homePath) {
                HomeView(navigator: navigator)
                    .settingsButton(navigator)
                    .miniPlayerInset(showNowPlaying: $showNowPlaying)
                    .routeDestinations(showNowPlaying: $showNowPlaying)
            }
        case .library:
            NavigationStack(path: $navigator.libraryPath) {
                LibraryView(navigator: navigator)
                    .settingsButton(navigator)
                    .miniPlayerInset(showNowPlaying: $showNowPlaying)
                    .routeDestinations(showNowPlaying: $showNowPlaying)
            }
        case .search:
            NavigationStack(path: $navigator.searchPath) {
                SearchView()
                    .miniPlayerInset(showNowPlaying: $showNowPlaying)
                    .routeDestinations(showNowPlaying: $showNowPlaying)
            }
        }
    }

    /// A library category's own stack (regular/wide): its root IS the category's content, promoted
    /// straight into the sidebar, with no extra hub screen in front of it.
    private func categoryStack(for category: LibraryCategory) -> some View {
        NavigationStack(path: navigator.binding(for: category)) {
            RouteDestinationView(route: .libraryCategory(category))
                .settingsButton(navigator)
                .miniPlayerInset(showNowPlaying: $showNowPlaying)
                .routeDestinations(showNowPlaying: $showNowPlaying)
        }
    }
}

/// Settings' sheet: its own `NavigationStack` on `Navigator.settingsPath`, so Sources and a server sign-in push
/// inside it, closed with Done (`docs/architecture/ios-port/phase-5-ios-app.md`, "Settings entry").
struct SettingsSheet: View {
    let navigator: Navigator
    @Binding var showNowPlaying: Bool

    var body: some View {
        @Bindable var navigator = navigator
        NavigationStack(path: $navigator.settingsPath) {
            SettingsView()
                .routeDestinations(showNowPlaying: $showNowPlaying)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") { navigator.showsSettings = false }
                            .accessibilityIdentifier("settings.done")
                    }
                }
        }
        .environment(navigator)
    }
}

extension View {
    /// The gear that opens Settings, on the Home and Library roots (every library category's root on regular and
    /// wide), so Settings is reachable at every width (#612).
    func settingsButton(_ navigator: Navigator) -> some View {
        toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    navigator.showsSettings = true
                } label: {
                    Label("Settings", systemImage: "gearshape")
                }
                .accessibilityIdentifier("settings.open")
            }
        }
    }
}

/// The wide-only inspector slot for Now Playing and the queue (empty until phase 6, #593), shown by a
/// toolbar button: Android's supporting pane from Expanded width.
private struct PlayerInspectorModifier: ViewModifier {
    let tier: LayoutTier
    let navigator: Navigator

    func body(content: Content) -> some View {
        if tier == .wide {
            content
                .inspector(isPresented: inspectorBinding) {
                    Text("Now Playing")
                        .navigationTitle("Now Playing")
                }
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            navigator.showsPlayerInspector.toggle()
                        } label: {
                            Label("Now Playing", systemImage: "sidebar.trailing")
                        }
                        .accessibilityIdentifier("inspectorToggle")
                    }
                }
        } else {
            content
        }
    }

    private var inspectorBinding: Binding<Bool> {
        Binding(get: { navigator.showsPlayerInspector }, set: { navigator.showsPlayerInspector = $0 })
    }
}
