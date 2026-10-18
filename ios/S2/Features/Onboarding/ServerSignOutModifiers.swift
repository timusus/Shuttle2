import Shared
import SwiftUI

/// The shell's ViewModel, cached under an always-live key (`Navigator.shellCacheKey`) so it keeps listening to the
/// servers' sessions for as long as the app runs.
@MainActor
enum ShellModels {
    static func cached(_ services: AppServices) -> ShellViewModel {
        services.viewModels.viewModel(Navigator.shellCacheKey) { services.graph.shellViewModel }
    }
}

/// Queues a "Signed out of <server>" prompt (`Navigator.signOutPrompts`) when a server signs the user out. Listens from
/// the app's root for as long as it runs; `ServerSignOutAlertModifier` shows the prompts.
private struct ServerSignOutModifier: ViewModifier {
    let navigator: Navigator
    @Environment(\.appServices) private var appServices

    func body(content: Content) -> some View {
        let shell = ShellModels.cached(appServices.required)
        Observing(shell.uiState) { state in
            content
                .consumeEvents(state.events, handled: { shell.onEventHandled(id: $0) }) { event in
                    switch onEnum(of: event) {
                    case .serverSignedOut(let signedOut):
                        let prompt = ServerSignOutPrompt(type: signedOut.type)
                        if !navigator.signOutPrompts.contains(prompt) { navigator.signOutPrompts.append(prompt) }
                    }
                }
        }
    }
}

/// Shows the queued sign-out prompts from one presentation level (the root, the Settings sheet, Now Playing) while it's
/// the topmost, as an alert presents only from the view controller on top. Its Sign In opens the source setup at that
/// server's sign-in, over the same level, starting from the saved address and username. While any setup is open the
/// prompts wait: the user is signing in already, and the setup's own sheet is on top.
private struct ServerSignOutAlertModifier: ViewModifier {
    let navigator: Navigator
    let isTopmost: Bool

    @State private var signIn: SourceSetupStart?

    func body(content: Content) -> some View {
        let prompts = navigator.signOutPrompts
        content
            .alert(
                prompts.first?.title ?? "",
                isPresented: Binding(
                    get: { isTopmost && !navigator.sourceSetupLive && signIn == nil && !navigator.signOutPrompts.isEmpty },
                    set: { if !$0, !navigator.signOutPrompts.isEmpty { navigator.signOutPrompts.removeFirst() } }
                ),
                presenting: prompts.first
            ) { prompt in
                Button("Sign In") { signIn = .signIn(prompt.type) }
                Button("Not Now", role: .cancel) {}
            } message: { prompt in
                Text(prompt.message)
            }
            .sheet(item: $signIn) { start in
                SourceSetupFlow(start: start, navigator: navigator, onClose: { signIn = nil })
            }
    }
}

extension View {
    /// Listens for servers signing the user out and shows the prompts from the root while nothing is presented over it.
    func serverSignOutPrompt(navigator: Navigator, isTopmost: Bool) -> some View {
        modifier(ServerSignOutAlertModifier(navigator: navigator, isTopmost: isTopmost))
            .modifier(ServerSignOutModifier(navigator: navigator))
    }

    /// The sign-out prompts from a presented level (the Settings sheet, Now Playing), while it's the topmost.
    func serverSignOutAlert(navigator: Navigator, isTopmost: Bool = true) -> some View {
        modifier(ServerSignOutAlertModifier(navigator: navigator, isTopmost: isTopmost))
    }
}
