import Shared

/// A screen's shared ViewModels, cached and cleared together under the screen's one `ViewModelCache` key: the
/// `Navigator` retains a key per route, so a screen with a second ViewModel (its `MediaActionsViewModel`, say) keeps
/// both in one of these, as both would share the screen's nav entry on Android.
protocol ViewModelGroup: ClearableViewModel {
    var members: [Lifecycle_viewmodelViewModel] { get }
}

extension ViewModelGroup {
    func clear() {
        for member in members { member.clear() }
    }
}
