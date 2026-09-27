import Foundation

/// A Kotlin ViewModel as the cache sees it: something that can be told its scope has ended.
///
/// Adapted from Shuttle Podcasts' `ViewModelCache`. There, Metro hands out a new view model on every
/// graph access, and a SwiftUI view struct is re-initialised whenever its parent's body runs, so a view
/// that stored `AppGraph.shared.xViewModel` replaced its view model (and every flow `Observing` was keyed
/// on) on each re-init. Screen view models therefore come from this cache, keyed by screen and argument.
///
/// TODO(#587): no shared ViewModel is exported yet. When the first one is, give :shared an iosMain
/// helper that clears it (a `ViewModelStore` per entry, as Android's `ViewModelStoreOwner` does) and
/// conform that type here, so eviction runs `onCleared` and cancels its `viewModelScope`.
protocol ClearableViewModel: AnyObject {
    func clear()
}

/// Keyed, lifecycle-cleared view models, standing in for Android's `ViewModelStore`.
///
/// An entry lives until its screen leaves the navigation state (`retainOnly(_:)`, or `remove(_:)`), or
/// until it is the least recently used of more than `maxSize` entries. Either way it is handed to
/// `onEvict`, which by default clears a `ClearableViewModel`, as a popped back stack entry clears
/// its store on Android.
@MainActor
final class ViewModelCache {
    /// The app's cache for screen view models.
    static let shared = ViewModelCache()

    private let maxSize: Int
    private let onEvict: (AnyObject) -> Void
    private var entries: [String: AnyObject] = [:]
    /// Least recently used first.
    private var order: [String] = []

    init(maxSize: Int = 20, onEvict: @escaping (AnyObject) -> Void = { ($0 as? ClearableViewModel)?.clear() }) {
        precondition(maxSize > 0, "maxSize must be positive")
        self.maxSize = maxSize
        self.onEvict = onEvict
    }

    /// The view model cached under `key`, or a new one from `create`. A key maps to one type: asking
    /// for a different type under a key that is already in use is a programming error.
    func viewModel<ViewModel: AnyObject>(_ key: String, create: () -> ViewModel) -> ViewModel {
        if let existing = entries[key] {
            touch(key)
            guard let viewModel = existing as? ViewModel else {
                preconditionFailure("\(key) holds a \(type(of: existing)), not a \(ViewModel.self)")
            }
            return viewModel
        }
        let viewModel = create()
        entries[key] = viewModel
        order.append(key)
        while order.count > maxSize {
            evict(order[0])
        }
        return viewModel
    }

    /// Clears the view model under `key`, if any: its screen was popped or dismissed.
    func remove(_ key: String) {
        if entries[key] != nil { evict(key) }
    }

    /// Clears every view model whose key is not in `liveKeys`: the screens still on a navigation path.
    func retainOnly(_ liveKeys: Set<String>) {
        for key in order where !liveKeys.contains(key) {
            evict(key)
        }
    }

    /// Clears everything, as the app's store is cleared when its process state is discarded.
    func removeAll() {
        for key in order { evict(key) }
    }

    var keys: [String] { order }

    private func touch(_ key: String) {
        order.removeAll { $0 == key }
        order.append(key)
    }

    private func evict(_ key: String) {
        order.removeAll { $0 == key }
        if let viewModel = entries.removeValue(forKey: key) {
            onEvict(viewModel)
        }
    }
}
