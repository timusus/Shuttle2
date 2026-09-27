import SwiftUI

@main
struct S2App: App {
    init() {
        AppGraph.initialize()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .task { LibraryImport.atLaunch() }
        }
    }
}
