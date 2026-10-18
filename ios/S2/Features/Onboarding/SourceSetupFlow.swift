import Shared
import SwiftUI

/// Where the source setup opens: the welcome (first run), the server cards (Sources' Add a Server), or straight at a
/// server's sign-in (Sources' Sign In Again).
enum SourceSetupStart: Hashable, Identifiable {
    case welcome
    case chooseSource
    case signIn(MediaProviderType)

    var id: Self { self }
}

/// A step pushed onto the setup's own stack.
enum SourceSetupStep: Hashable {
    case chooseSource
    case signIn(MediaProviderType)
    case importing
}

/// Adding a media server (#624), on the shared `SourceSetupViewModel`: welcome, the server cards, the sign-in
/// (`ServerSignInView`) and the import's progress, in the setup's own `NavigationStack`. `ContentView` opens it at
/// the welcome on a first run (no server, never finished or skipped: `SourceSetupUiState.firstRun`); Sources opens it
/// at the cards or a sign-in in a sheet, so the first run and Add a Server are one implementation. Closing it, from
/// Not Now, Continue or Cancel, finishes the setup (`onFinish`), so the first run never opens by itself again.
///
/// Android has no first run (#379); this is iOS's, since an iPhone's music isn't readable until it's copied into the
/// app or a folder is picked in Files. The first run's cards also offer this device's music (#590): it turns the device
/// on as a source and follows its import, as a sign-in does.
struct SourceSetupFlow: View {
    let start: SourceSetupStart
    /// Keeps the sign-in's view model live while the setup is up (`Navigator.sourceSetupLive`); nil in previews.
    let navigator: Navigator?
    let onClose: () -> Void

    @State private var path: [SourceSetupStep] = []
    @Environment(\.appServices) private var appServices
    private var services: AppServices { appServices.required }

    var body: some View {
        let models = SourceSetupModels.cached(services)
        NavigationStack(path: $path) {
            root(models)
                .navigationDestination(for: SourceSetupStep.self) { step in
                    destination(step, models)
                }
        }
        .onAppear { navigator?.sourceSetupLive = true }
        .onDisappear { navigator?.sourceSetupLive = false }
    }

    @ViewBuilder
    private func root(_ models: SourceSetupModels) -> some View {
        switch start {
        case .welcome:
            SourceSetupWelcome(onStart: { path.append(.chooseSource) }, onSkip: { close(models) })
        case .chooseSource:
            cards(models).toolbar { cancel(models) }
        case .signIn(let type):
            signIn(type, models).toolbar { cancel(models) }
        }
    }

    @ViewBuilder
    private func destination(_ step: SourceSetupStep, _ models: SourceSetupModels) -> some View {
        switch step {
        case .chooseSource:
            cards(models)
        case .signIn(let type):
            signIn(type, models)
        case .importing:
            Observing(models.setup.uiState) { state in
                SourceSetupImportPage(
                    state: SourceSetupImportState(state.serverImport),
                    onRetry: { LibraryImport.refresh(graph: services.graph) },
                    onContinue: { close(models) }
                )
            }
            // The server is connected: going back to its sign-in would only sign in again.
            .navigationBarBackButtonHidden()
        }
    }

    private func cards(_ models: SourceSetupModels) -> some View {
        SourceTypeCards(
            types: MediaProviderType.signInTypes,
            onSelect: { type in
                // The entitlement gate (`TryAddServer`, open on iOS until billing lands) before the sign-in.
                if models.setup.onChooseType() { path.append(.signIn(type)) }
            },
            // Sources has its own switch for this device; only the first run offers it here.
            onUseThisDevice: start == .welcome ? {
                models.setup.onUseThisDevice()
                path.append(.importing)
            } : nil
        )
    }

    private func signIn(_ type: MediaProviderType, _ models: SourceSetupModels) -> some View {
        ServerSignInView(
            type: type,
            cacheKey: Navigator.sourceSetupSignInCacheKey(type),
            onConnected: { models.setup.onServerConnected(type: type) },
            onFinished: { path.append(.importing) }
        )
    }

    private func cancel(_ models: SourceSetupModels) -> some ToolbarContent {
        ToolbarItem(placement: .cancellationAction) {
            Button("Cancel") { close(models) }
                .accessibilityIdentifier("onboarding.cancel")
        }
    }

    private func close(_ models: SourceSetupModels) {
        models.setup.onFinish()
        onClose()
    }
}

/// The setup's ViewModel, cached under a key that is always live (`Navigator.sourceSetupCacheKey`): `ContentView`
/// reads it at launch for the first run, and the setup follows the import after its sign-in has gone.
final class SourceSetupModels: ViewModelGroup {
    let setup: SourceSetupViewModel

    init(graph: IosAppGraph) {
        setup = graph.sourceSetupViewModel
    }

    var members: [Lifecycle_viewmodelViewModel] { [setup] }

    @MainActor
    static func cached(_ services: AppServices) -> SourceSetupModels {
        services.viewModels.viewModel(Navigator.sourceSetupCacheKey) { SourceSetupModels(graph: services.graph) }
    }
}

// MARK: - First-run presentation

extension View {
    /// The first run's setup, over everything: a full-screen cover on compact, a form-sized sheet on regular and
    /// wide. It can't be swiped away; Not Now or Continue closes it.
    func sourceSetupPresentation(isPresented: Binding<Bool>, fullScreen: Bool, navigator: Navigator) -> some View {
        let cover = Binding(get: { isPresented.wrappedValue && fullScreen }, set: { isPresented.wrappedValue = $0 })
        let sheet = Binding(get: { isPresented.wrappedValue && !fullScreen }, set: { isPresented.wrappedValue = $0 })
        let flow = SourceSetupFlow(start: .welcome, navigator: navigator, onClose: { isPresented.wrappedValue = false })
            .interactiveDismissDisabled()
        return fullScreenCover(isPresented: cover) { flow }
            .sheet(isPresented: sheet) {
                if #available(iOS 18, *) {
                    flow.presentationSizing(.form)
                } else {
                    flow
                }
            }
    }
}
