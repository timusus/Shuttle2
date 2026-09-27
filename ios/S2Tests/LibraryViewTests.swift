import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// The Library tab's root: a plain list of categories, each a `NavigationLink` to its `Route`.
@MainActor
struct LibraryViewTests {
    @Test func listsEveryCategory() throws {
        let sut = LibraryView(navigator: Navigator())
        for category in LibraryCategory.allCases {
            #expect((try? sut.inspect().find(text: category.title)) != nil, "missing \(category.title)")
        }
    }
}
