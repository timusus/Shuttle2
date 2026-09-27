import Shared
import SwiftUI

// One-shot events from shared ViewModels, the counterpart of the Compose `ConsumeEvents` over `PendingEvents`
// (UDF doc principle 4). A ViewModel holds each event in its UiState as a `PendingEvent` until the UI hands its id
// back, so an event posted while no screen is watching still reaches the next one.

extension View {
    /// Calls `consume` once for each of `events`, then `handled` with its id so the ViewModel drops it.
    func consumeEvents<Value: AnyObject>(
        _ events: [PendingEvent<Value>],
        handled: @escaping (Int64) -> Void,
        consume: @escaping (Value) -> Void
    ) -> some View {
        modifier(ConsumeEventsModifier(events: events, handled: handled, consume: consume))
    }
}

private struct ConsumeEventsModifier<Value: AnyObject>: ViewModifier {
    let events: [PendingEvent<Value>]
    let handled: (Int64) -> Void
    let consume: (Value) -> Void

    /// Ids already passed to `consume`: the ViewModel's list only shrinks once `handled` reaches it, so a body
    /// run in between mustn't consume the same event twice.
    @State private var consumed: Set<Int64> = []

    func body(content: Content) -> some View {
        content.onChange(of: events.map(\.id), initial: true) { _, ids in
            for event in events where !consumed.contains(event.id) {
                consumed.insert(event.id)
                if let value = event.value { consume(value) }
                handled(event.id)
            }
            consumed.formIntersection(ids)
        }
    }
}
