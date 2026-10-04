#if DEBUG
import OSLog
import SwiftUI

/// The App Store screenshot walk's remote control (`ios/support/store-screenshots/`). Compiled into Debug builds
/// only: a Release build has neither this file's code nor the `.screenshotHooks` call in `ContentView`.
///
/// `capture.sh` writes one `s2-debug://<action>?<query>` URL to `Documents/screenshot_hook.url`; this polls the
/// file every 0.5 s, runs the action and deletes the file. A file instead of `simctl openurl`, because iOS puts an
/// "Open in Shuttle Music?" alert in front of a URL that nothing scripted can dismiss. Actions:
///
///     tab?name=home|library|search           select a root tab (pops it to its root)
///     library?category=albums|songs|...      show a library category (selected in the iPad's sidebar, or in the
///                                            iPhone's Library tab as its chip would, so there's no back button)
///     route?to=sources|equalizer             push onto the current stack (Settings' stack while it is up)
///     settings?open=1|0                      the Settings sheet
///     player?open=1|0[&fullScreen=1]         Now Playing; fullScreen=1 covers the screen on iPad too, rather than
///                                            a form sheet over a dimmed library
///     paywall?price=$9.99                    show the paywall's loaded state with this price (omit price to undo)
///     miniplayer?hidden=1|0                  hide the mini player (set before opening the screen it would cover)
///     reset                                  close every sheet and pop the selected root
///
/// What a hook can't reach (the queue sheet, the first album) the capture flows tap through Maestro.
/// What a hook switches that the screens read as they are built.
@MainActor
enum ScreenshotState {
    static var hidesMiniPlayer = false
}

@MainActor
struct ScreenshotHooksModifier: ViewModifier {
    let navigator: Navigator
    @Binding var showNowPlaying: Bool

    private static let log = Logger(subsystem: "com.simplecityapps.shuttle.dev", category: "screenshot-hooks")

    func body(content: Content) -> some View {
        content.task {
            let file = URL.documentsDirectory.appending(path: "screenshot_hook.url")
            while !Task.isCancelled {
                if let text = try? String(contentsOf: file, encoding: .utf8) {
                    try? FileManager.default.removeItem(at: file)
                    if let url = URL(string: text.trimmingCharacters(in: .whitespacesAndNewlines)) {
                        run(url)
                    }
                }
                try? await Task.sleep(for: .milliseconds(500))
            }
        }
    }

    private func run(_ url: URL) {
        let query = Dictionary(
            uniqueKeysWithValues: (URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? [])
                .compactMap { item in item.value.map { (item.name, $0) } }
        )
        Self.log.info("hook \(url.absoluteString, privacy: .public)")
        switch url.host() {
        case "tab":
            guard let tab = AppTab.allCases.first(where: { $0.title.lowercased() == query["name"] }) else { return }
            navigator.selection = .tab(tab)
        case "library":
            guard let category = LibraryCategory(rawValue: query["category"] ?? "") else { return }
            if UIDevice.current.userInterfaceIdiom == .pad {
                navigator.selectLibraryCategory(category)
            } else {
                // The compact tab bar has no category tags: choose it on the Library tab's rail (`LibraryView`'s
                // stored category), then show the tab, popped to that root.
                UserDefaults.standard.set(category.rawValue, forKey: "library.category")
                navigator.selection = .tab(.library)
            }
        case "route":
            switch query["to"] {
            case "sources": navigator.open(.sources)
            case "equalizer": navigator.open(.equalizer)
            case "scrobbling": navigator.open(.scrobbling)
            default: Self.log.error("unknown route \(query["to"] ?? "", privacy: .public)")
            }
        case "settings":
            navigator.showsSettings = query["open"] == "1"
        case "player":
            NowPlayingPresentationStyle.forcesFullScreenCover = query["fullScreen"] == "1"
            showNowPlaying = query["open"] == "1"
        case "paywall":
            AppGraph.dependencies.storeKit.showScreenshotPrice(query["price"])
        case "miniplayer":
            ScreenshotState.hidesMiniPlayer = query["hidden"] == "1"
        case "reset":
            showNowPlaying = false
            NowPlayingPresentationStyle.forcesFullScreenCover = false
            navigator.showsSettings = false
            navigator.selection = navigator.selection
            AppGraph.dependencies.storeKit.showScreenshotPrice(nil)
            ScreenshotState.hidesMiniPlayer = false
        default:
            Self.log.error("unknown hook \(url.absoluteString, privacy: .public)")
        }
    }
}

extension View {
    func screenshotHooks(navigator: Navigator, showNowPlaying: Binding<Bool>) -> some View {
        modifier(ScreenshotHooksModifier(navigator: navigator, showNowPlaying: showNowPlaying))
    }
}
#endif
