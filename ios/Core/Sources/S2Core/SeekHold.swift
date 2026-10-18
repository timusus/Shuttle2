import Foundation

/// Holds a seek's target as the displayed position until the player reports a position near it, so the
/// scrubber doesn't jump back to the stale position between releasing it and the next progress tick. Gives
/// up after `timeout`, in case the seek never lands (a failed or clamped seek).
public struct SeekHold: Equatable {
    /// How close a reported position must come to the target to count as the seek having landed.
    public static let toleranceMs = 1500
    public static let timeout: TimeInterval = 2

    public private(set) var targetMs: Int?
    private var deadline: Date?

    public init() {}

    public mutating func begin(_ targetMs: Int, now: Date = Date()) {
        self.targetMs = targetMs
        deadline = now.addingTimeInterval(Self.timeout)
    }

    /// The position to show for a `reportedMs` from the player; releases the hold once the report has
    /// caught up with the target or the timeout has passed.
    public mutating func displayed(reportedMs: Int, now: Date = Date()) -> Int {
        guard let targetMs, let deadline else { return reportedMs }
        if abs(reportedMs - targetMs) <= Self.toleranceMs || now >= deadline {
            self.targetMs = nil
            self.deadline = nil
            return reportedMs
        }
        return targetMs
    }
}
