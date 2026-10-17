import Shared
import SwiftUI

/// What views read from the app's graph and platform objects, set once on the root view (`S2App`) and read with
/// `@Environment(\.appServices)`.
@MainActor
struct AppServices {
    let graph: IosAppGraph
    let viewModels: ViewModelCache
    let playerBinding: PlayerBinding
    let playIntent: PlayIntent
    let storeKit: StoreKitManager
    let localLibrary: LocalLibrary
    let importState: SkieSwiftStateFlow<SongImportState>
    let searchWarmUp: SearchWarmUp
}

extension EnvironmentValues {
    /// Optional because `.environment(\.appServices, _)` reads the current value first, so an unset default cannot trap.
    @Entry var appServices: AppServices?
}

extension AppServices? {
    /// Reading it unset is a programmer error: S2App or a test must set it on the root view.
    var required: AppServices {
        guard let services = self else { preconditionFailure("\\.appServices is unset") }
        return services
    }
}
