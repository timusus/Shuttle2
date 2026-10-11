import Observation
import os
import UIKit

/// The quick actions on the app icon's long-press menu (#761). Dynamic shortcut items, so their titles are looked up
/// in Localizable.strings at launch and fall back to the English literal until the generated tables carry them.
enum HomeScreenQuickAction: String, CaseIterable {
    case shuffleAll = "com.simplecityapps.shuttle.quickaction.shuffle-all"
    case search = "com.simplecityapps.shuttle.quickaction.search"
    case history = "com.simplecityapps.shuttle.quickaction.history"

    init?(_ item: UIApplicationShortcutItem) {
        self.init(rawValue: item.type)
    }

    var item: UIApplicationShortcutItem {
        UIApplicationShortcutItem(
            type: rawValue,
            localizedTitle: title,
            localizedSubtitle: nil,
            icon: UIApplicationShortcutIcon(systemImageName: systemImage)
        )
    }

    private static let log = Logger(subsystem: "com.simplecityapps.shuttle2", category: "quick-actions")

    private var title: String {
        switch self {
        case .shuffleAll: String(localized: "Shuffle All")
        case .search: String(localized: "Search")
        case .history: String(localized: "Recently Played")
        }
    }

    private var systemImage: String {
        switch self {
        case .shuffleAll: "shuffle"
        case .search: "magnifyingglass"
        case .history: "clock.arrow.circlepath"
        }
    }

    /// Where a navigating action goes; nil for the one that plays.
    var destination: (tab: AppTab, route: Route?)? {
        switch self {
        case .shuffleAll: nil
        case .search: (.search, nil)
        case .history: (.home, .smartPlaylist(id: "history"))
        }
    }

    /// Carries the action out through the app's own entry points: navigation through `navigator`, the shuffle through
    /// `shuffleLibrary` (by default the same performer as Siri's and Shortcuts' Shuffle All).
    @MainActor
    func perform(
        on navigator: Navigator,
        shuffleLibrary: () async throws -> Void = { try await IntentPerformers.requireLibrary().shuffleLibrary() }
    ) async {
        guard let destination else {
            do {
                try await shuffleLibrary()
            } catch {
                Self.log.error("Shuffle All quick action failed: \(error.localizedDescription, privacy: .public)")
            }
            return
        }
        // `open` pushes onto Settings' own path while its sheet is up, so close it (and the inspector) first.
        navigator.showsSettings = false
        navigator.showsPlayerInspector = false
        navigator.selectTab(destination.tab)
        if let route = destination.route {
            navigator.open(route)
        }
    }
}

/// Hands an action iOS delivered (at launch or while running) to the shell, which performs it once it is on screen.
@MainActor
@Observable
final class QuickActionRouter {
    static let shared = QuickActionRouter()

    var pending: HomeScreenQuickAction?

    func install() {
        UIApplication.shared.shortcutItems = HomeScreenQuickAction.allCases.map(\.item)
    }
}

/// Receives the action for a running app; a cold launch's arrives in the connection options
/// (`AppDelegate.application(_:configurationForConnecting:options:)`).
final class QuickActionSceneDelegate: NSObject, UIWindowSceneDelegate {
    func windowScene(
        _ windowScene: UIWindowScene,
        performActionFor shortcutItem: UIApplicationShortcutItem,
        completionHandler: @escaping (Bool) -> Void
    ) {
        guard let action = HomeScreenQuickAction(shortcutItem) else {
            completionHandler(false)
            return
        }
        Task { @MainActor in QuickActionRouter.shared.pending = action }
        completionHandler(true)
    }
}
