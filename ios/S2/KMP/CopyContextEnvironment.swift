import Shared
import SwiftUI

extension EnvironmentValues {
    /// What decides which copy of a song plays; nil until the first value arrives, when no copy UI shows.
    @Entry var copyContext: CopyContext?
    /// Plays a copy of a song now: `(song, copy)`.
    @Entry var playFromCopy: (Song, Song) -> Void = { _, _ in }
}

extension View {
    /// Puts the copy context in the environment from one observation for the whole app, rather than one per song menu.
    func observingCopyContext(_ observe: ObserveCopyContext, playFrom: @escaping (Song, Song) -> Void) -> some View {
        modifier(CopyContextModifier(observe: observe, playFrom: playFrom))
    }
}

private struct CopyContextModifier: ViewModifier {
    let observe: ObserveCopyContext
    let playFrom: (Song, Song) -> Void
    @State private var context: CopyContext?

    func body(content: Content) -> some View {
        content
            .environment(\.copyContext, context)
            .environment(\.playFromCopy, playFrom)
            .task {
                for await current in observe.invoke() { context = current }
            }
    }
}
