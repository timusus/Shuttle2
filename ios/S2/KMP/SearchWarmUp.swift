import Shared
import SwiftUI

/// Starts building the search index in the background, once: when Home or a Library list first shows its content,
/// or when Search opens, whichever is first. Started at launch it competed with that first content for the
/// library's songs; a search opened before it has run builds the index itself.
@MainActor
final class SearchWarmUp {
    private let index: LibrarySearchIndex
    private var started = false

    init(index: LibrarySearchIndex) {
        self.index = index
    }

    func start() {
        guard !started else { return }
        started = true
        StartupTrace.step("searchIndexWarmUp") { index.warmUp() }
    }
}

extension View {
    /// Starts the search index's warm-up once [loaded], this screen's first content, is up.
    func warmsUpSearch(once loaded: Bool) -> some View {
        modifier(WarmsUpSearch(loaded: loaded))
    }
}

private struct WarmsUpSearch: ViewModifier {
    let loaded: Bool

    @Environment(\.appServices) private var appServices

    func body(content: Content) -> some View {
        content.onChange(of: loaded, initial: true) { _, loaded in
            if loaded { appServices.required.searchWarmUp.start() }
        }
    }
}
