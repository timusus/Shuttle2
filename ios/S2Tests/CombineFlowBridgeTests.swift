import Combine
import Foundation
import Shared
import Testing
@testable import S2

/// The Combine -> Kotlin flow bridge must not forward an assignment that did not change the value, and
/// the subscription must not keep its target alive. `RecordingFlow` stands in for :shared's
/// `WritableFlow`, whose values Swift can't read back: it holds the subscription and records what
/// `emit` would push.
struct CombineFlowBridgeTests {
    private final class RecordingFlow {
        var subscription: AnyCancellable?
        private(set) var values: [String] = []
        func emit(_ value: String) { values.append(value) }
    }

    private struct NotEquatable { let value: String }

    @Test func equatableOutputDropsRepeatedValues() {
        let subject = CurrentValueSubject<String, Never>("a")
        let flow = RecordingFlow()
        flow.subscription = subject.feed(flow) { $0.emit($1) }

        subject.send("a")
        subject.send("a")
        subject.send("b")
        subject.send("b")
        subject.send("a")

        #expect(flow.values == ["a", "b", "a"])
    }

    @Test func currentValueIsForwardedImmediately() {
        let subject = CurrentValueSubject<String, Never>("initial")
        let flow = RecordingFlow()
        flow.subscription = subject.feed(flow) { $0.emit($1) }
        #expect(flow.values == ["initial"])
    }

    @Test func nonEquatableOutputForwardsEveryValue() {
        let subject = PassthroughSubject<NotEquatable, Never>()
        let flow = RecordingFlow()
        flow.subscription = subject.feed(flow) { $0.emit($1.value) }

        subject.send(NotEquatable(value: "same"))
        subject.send(NotEquatable(value: "same"))

        #expect(flow.values == ["same", "same"])
    }

    @Test func subscriptionDoesNotRetainItsTarget() {
        let subject = PassthroughSubject<String, Never>()
        weak var weakFlow: RecordingFlow?
        do {
            let flow = RecordingFlow()
            flow.subscription = subject.feed(flow) { $0.emit($1) }
            weakFlow = flow
            subject.send("alive")
            #expect(flow.values == ["alive"])
        }
        #expect(weakFlow == nil)
        subject.send("after release") // must not crash or resurrect anything
    }

    /// Release of the Kotlin flow is up to the Kotlin GC, so only the ownership is checked here; the
    /// weak capture that makes release possible is the `feed` test above.
    @Test func toKotlinFlowKeepsItsSubscriptionOnTheFlow() {
        let subject = CurrentValueSubject<String, Never>("a")
        let flow = subject.toKotlinFlow { $0 as NSString }
        #expect(flow.subscription is AnyCancellable)
        subject.send("b") // reaches WritableFlow.emit without crashing
    }
}
