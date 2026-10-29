import Shared

/// The classical work the playing song sits in, as plain values: the shared `NowPlayingWork` (built only for a work of
/// two or more movements, and only while Classical display is on) with each ribbon segment and movement row paired
/// with the queue item it jumps to.
struct NowPlayingWorkState: Equatable {
    struct Segment: Equatable {
        let durationMs: Int
        /// "Play movement II" names it for VoiceOver.
        let ordinal: String
        let queueUID: Int64?
    }

    struct Movement: Equatable {
        let numeral: String
        let name: String
        let durationMs: Int
        let queueUID: Int64?
    }

    var composer: String?
    var work: String
    var detail: String?
    var movement: String
    var performers: String?
    /// The mini player's two lines.
    var miniTitle: String
    var miniSubtitle: String
    var ordinal: String
    var total: String
    var segments: [Segment]
    var currentSegment: Int
    /// Time in the current segment before the current movement starts (an act of several movements).
    var currentSegmentOffsetMs: Int
    var remainingMs: Int
    /// Many movements: the segments merge into one bar.
    var continuous: Bool
    var movements: [Movement]
    var currentMovement: Int

    /// How far through the current segment `positionMs` into the current movement is, 0...1.
    func currentFill(positionMs: Int) -> Double {
        guard segments.indices.contains(currentSegment), segments[currentSegment].durationMs > 0 else { return 0 }
        return min(1, max(0, Double(currentSegmentOffsetMs + positionMs) / Double(segments[currentSegment].durationMs)))
    }

    /// "3 of 5 · 24:10 left in work"
    func caption(positionMs: Int) -> String {
        let left = NowPlayingWorkKt.formatWorkRemaining(ms: Int64(remainingMs - positionMs / 1000 * 1000))
        return "\(ordinal) of \(total) · \(left) left in work"
    }
}

extension NowPlayingWorkState {
    /// `queueUIDs` are the queue's items in order and `currentIndex` the playing one's place in them.
    init(_ work: NowPlayingWork, queueUIDs: [Int64], currentIndex: Int) {
        let ribbon = work.ribbon
        let starts = ribbon.segmentStarts.map { Int($0.intValue) }
        let first = starts.first ?? 0
        func uid(_ index: Int) -> Int64? { queueUIDs.indices.contains(index) ? queueUIDs[index] : nil }
        self.init(
            composer: work.composer,
            work: work.work,
            detail: work.detail,
            movement: work.movement,
            performers: work.performers,
            miniTitle: work.miniTitle,
            miniSubtitle: work.miniSubtitle,
            ordinal: work.ordinal,
            total: work.total,
            segments: zip(ribbon.segments, starts).enumerated().map { offset, pair in
                let (duration, start) = pair
                let ordinals = work.ordinals
                return Segment(
                    durationMs: Int(duration.int64Value),
                    ordinal: ordinals.indices.contains(start - first) ? ordinals[start - first] : String(offset + 1),
                    queueUID: uid(start)
                )
            },
            currentSegment: Int(ribbon.currentSegment),
            currentSegmentOffsetMs: Int(ribbon.currentSegmentOffsetMs),
            remainingMs: Int(ribbon.remainingMs),
            continuous: ribbon.continuous,
            movements: work.tracks.enumerated().map { index, track in
                Movement(numeral: track.numeral, name: track.name, durationMs: Int(track.durationMs), queueUID: uid(first + index))
            },
            currentMovement: currentIndex - first
        )
    }
}
