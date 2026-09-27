import Combine
import Shared

// Swift -> Kotlin flows, adapted from Shuttle Podcasts' `CombineFlowBridge`.
//
// Some shared code reads state only Swift has (preferences in `UserDefaults`, the scene phase, the
// audio route). Swift can't implement a Kotlin `Flow` under SKIE, so :shared's iosMain has a
// `WritableFlow<T>`: a `Flow` backed by a replay-1 `MutableSharedFlow`, with an `emit(value:)` Swift
// calls and a `subscription` slot that keeps the Combine side alive exactly as long as the flow.

extension Publisher where Failure == Never {
    /// A Kotlin flow of this publisher's values, mapped by `transform`. The flow holds the subscription.
    func toKotlinFlow<T: AnyObject>(transform: @escaping (Output) -> T?) -> WritableFlow<T> {
        let flow = WritableFlow<T>()
        flow.subscription = feed(flow) { $0.emit(value: transform($1)) }
        return flow
    }
}

extension Publisher where Failure == Never, Output: Equatable {
    /// As above, dropping values equal to the last one forwarded (see the deduplicating `feed`).
    func toKotlinFlow<T: AnyObject>(transform: @escaping (Output) -> T?) -> WritableFlow<T> {
        let flow = WritableFlow<T>()
        flow.subscription = feed(flow) { $0.emit(value: transform($1)) }
        return flow
    }
}

extension Publisher where Failure == Never {
    /// Forwards every value to `target` through `emit`, holding `target` weakly: the returned
    /// subscription is meant to be stored on `target` itself, so a strong capture would be a cycle.
    func feed<Target: AnyObject>(_ target: Target, _ emit: @escaping (Target, Output) -> Void) -> AnyCancellable {
        sink { [weak target] value in
            guard let target else { return }
            emit(target, value)
        }
    }
}

extension Publisher where Failure == Never, Output: Equatable {
    /// The Equatable overload drops values equal to the last one forwarded. `@Published` republishes
    /// on every assignment, and the shared ViewModels feed these flows into `flatMapLatest`, so a
    /// redundant emission would cancel and restart whatever is downstream. Android's DataStore flows
    /// are distinct already; this keeps the Swift-fed ones the same.
    func feed<Target: AnyObject>(_ target: Target, _ emit: @escaping (Target, Output) -> Void) -> AnyCancellable {
        removeDuplicates().sink { [weak target] value in
            guard let target else { return }
            emit(target, value)
        }
    }
}
