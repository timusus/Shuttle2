import Foundation
import os
import PlaybackRender

/// **The first stage: each item at its ReplayGain.** Ported from Android's `ReplayGainAudioProcessor`: each item's tags
/// are taken at its flush, and the mode and preamp are read per buffer, so a change applies to the item playing. The
/// tags arrive when the item's stream resolves, which is before its source opens and so before its flush. A change of
/// gain mid-item ramps over 5 ms, where Android's steps, so it doesn't click. At unity the audio passes bit for bit.
public final class ReplayGainProcessor: AudioProcessor, @unchecked Sendable {
    public enum Mode: Sendable { case off, track, album }

    private struct Tags { var track: Float?; var album: Float? }

    private struct Setting { var mode = Mode.off; var preampDb: Float = 0 }

    /// Most recent last. Bounded: tags are only needed until their item is flushed, and the ones decoded ahead are few.
    private let tags = OSAllocatedUnfairLock(initialState: [(item: AnyHashable, tags: Tags)]())
    private static let kept = 16

    private let setting = OSAllocatedUnfairLock(initialState: Setting())

    // Transport queue only.
    private var itemTags = Tags()
    private var channelCount = 1
    private var rampFrames = 1
    private var gain: Float = 1
    private var target: Float = 1
    private var step: Float = 0

    public init() {}

    /// Called when `item`'s stream resolves, on any thread; nil where the song has no such tag.
    public func setTags(track: Float?, album: Float?, for item: some Hashable & Sendable) {
        let item = AnyHashable(item)
        tags.withLock { tags in
            tags.removeAll { $0.item == item }
            tags.append((item, Tags(track: track, album: album)))
            if tags.count > Self.kept { tags.removeFirst(tags.count - Self.kept) }
        }
    }

    /// From the next buffer, on any thread.
    public func setMode(_ mode: Mode, preampDb: Float) {
        setting.withLock { $0 = Setting(mode: mode, preampDb: preampDb) }
    }

    public func configure(_ input: PCMFormat) throws -> PCMFormat {
        channelCount = max(1, Int(input.channelCount))
        rampFrames = max(1, Int(input.sampleRate * 0.005))
        return input
    }

    public var isActive: Bool { true }

    public func process(_ samples: UnsafeMutableBufferPointer<Float>) {
        let wanted = currentFactor()
        if wanted != target {
            target = wanted
            step = (target - gain) / Float(rampFrames)
        }
        if gain == target {
            guard gain != 1 else { return }
            for index in samples.indices { samples[index] *= gain }
            return
        }
        var frameStart = 0
        while frameStart < samples.count {
            if gain != target {
                gain += step
                if (step > 0 && gain > target) || (step < 0 && gain < target) { gain = target }
            }
            for index in frameStart..<min(frameStart + channelCount, samples.count) { samples[index] *= gain }
            frameStart += channelCount
        }
    }

    public func flush(_ metadata: StreamMetadata) {
        let item = AnyHashable(metadata.item)
        itemTags = tags.withLock { tags in tags.last { $0.item == item }?.tags } ?? Tags()
        target = currentFactor()
        gain = target
    }

    public func reset() {
        itemTags = Tags()
        gain = 1
        target = 1
    }

    /// Android's `replayGainDb`: the preamp applies even with the mode off, and a missing tag falls back to the other.
    private func currentFactor() -> Float {
        let setting = setting.withLock { $0 }
        let tagDb: Float = switch setting.mode {
        case .off: 0
        case .track: itemTags.track ?? itemTags.album ?? 0
        case .album: itemTags.album ?? itemTags.track ?? 0
        }
        let db = setting.preampDb + tagDb
        return db == 0 ? 1 : powf(10, db / 20)
    }
}
