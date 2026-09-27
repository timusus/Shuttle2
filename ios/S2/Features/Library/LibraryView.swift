import SwiftUI

/// The Library tab's root on compact: a list of categories that pushes each one's list
/// (`Route.libraryCategory`), Music-style rather than Android's pager of tabs. Regular and wide show the
/// same categories directly in the sidebar instead (`AppShell`),
/// `docs/architecture/ios-port/phase-5-ios-app.md` section 2.
struct LibraryView: View {
    let navigator: Navigator

    var body: some View {
        List(LibraryCategory.allCases, id: \.self) { category in
            NavigationLink(value: Route.libraryCategory(category)) {
                Label(category.title, systemImage: category.systemImage)
            }
        }
        .navigationTitle(AppTab.library.title)
        .settingsGear(navigator)
    }
}
