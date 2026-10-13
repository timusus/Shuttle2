import Foundation
import os
import PlaybackRender

/// **The first stage: each item at its ReplayGain.** Ported from Android's `ReplayGainAudioProcessor`, which reads an
/// item's gain at its flush. Here the gain arrives already in dB, mode and preamp applied (Kotlin's `replayGainDb`),
/// when the item's stream is resolved, which is before its source opens and so before its flush. A setting change
/// therefore applies from the next item resolved. An item with no gain recorded plays at unity.
public final class ReplayGainProcessor: AudioProcessor, @unchecked Sendable {
    /// Most recent last. Bounded: a gain is only needed until its item is flushed, and the ones decoded ahead are few.
    private let gains = OSAllocatedUnfairLock(initialState: [(item: AnyHashable, db: Float)]())
    private static let kept = 16

    // Transport queue only.
    private var gain: Float = 1

    public init() {}

    /// Called when `item`'s stream resolves, on any thread.
    public func setGain(_ db: Float, for item: some Hashable & Sendable) {
        let item = AnyHashable(item)
        gains.withLock { gains in
            gains.removeAll { $0.item == item }
            gains.append((item, db))
            if gains.count > Self.kept { gains.removeFirst(gains.count - Self.kept) }
        }
    }

    public func configure(_ input: PCMFormat) throws -> PCMFormat { input }

    public var isActive: Bool { true }

    public func process(_ samples: UnsafeMutableBufferPointer<Float>) {
        guard gain != 1 else { return }
        for index in samples.indices { samples[index] *= gain }
    }

    public func flush(_ metadata: StreamMetadata) {
        let item = AnyHashable(metadata.item)
        let db = gains.withLock { gains in gains.last { $0.item == item }?.db } ?? 0
        gain = db == 0 ? 1 : powf(10, db / 20)
    }

    public func reset() {
        gain = 1
    }
}
