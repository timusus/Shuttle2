import Combine

// Swift -> Kotlin flows, adapted from Shuttle Podcasts' `CombineFlowBridge`.
//
// Some shared code reads state only Swift has (preferences in `UserDefaults`, the scene phase, the
// audio route). Swift can't implement a Kotlin `Flow` under SKIE, so Podcasts gives :shared's iosMain a
// `WritableFlow<T>`: a `Flow` backed by a replay-1 `MutableSharedFlow`, with an `emit(value:)` Swift
// calls and a `subscription` slot that keeps the Combine side alive exactly as long as the flow.
//
// TODO(#587): S2's :shared has no `WritableFlow` yet. When the first Swift-fed flow is needed, copy
// Podcasts' `shared/src/iosMain/.../di/WritableFlow.kt` into :shared and add, here:
//
//     extension Publisher where Failure == Never {
//         func toKotlinFlow<T: AnyObject>(transform: @escaping (Output) -> T?) -> WritableFlow<T> {
//             let flow = WritableFlow<T>()
//             flow.subscription = feed(flow) { $0.emit(value: transform($1)) }
//             return flow
//         }
//     }
//
// (and the same for `Output: Equatable`, so the deduplicating `feed` below is the one picked).

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
