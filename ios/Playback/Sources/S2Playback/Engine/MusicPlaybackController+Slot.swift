import Foundation

extension MusicPlaybackController {
    final class Slot {
        private static var lastId = 0
        /// Unique for the controller's life (engine queue only), unlike an object address.
        let id: Int
        /// Replaced by a load that hands the same stream back, taking the slot over.
        var track: PlaybackTrack
        let source: TrackPCMSource
        var opened = false
        /// Opened and neither read nor sought since: the source is at its first frame (or, primed,
        /// just past `primed`).
        var atStart = true
        /// It could not be opened, or a read failed.
        var failed = false
        var durationFrames: Int64?
        /// Non-nil while the source is being opened on the prepare queue; left when it's done,
        /// with `prepared` set. Nothing else touches the source until then.
        var preparing: DispatchGroup?
        /// What the open found (the duration), the first frames it decoded and how long that took,
        /// or its error. Written on the prepare queue before `preparing` is left; applied on the
        /// engine queue.
        var prepared: Result<(duration: Int64?, primed: [Float], primeError: Error?, seconds: Double), Error>?
        /// Decoded ahead by the open, read before the source: interleaved, at the output format.
        var primed: [Float] = []
        /// The open's read failed: the first read fails with it, as it would have.
        var primeError: Error?
        /// Its source turned unseekable and reads nothing more, and the owner was told where to re-open it.
        var awaitingReopen = false

        init(track: PlaybackTrack) {
            Self.lastId += 1
            id = Self.lastId
            self.track = track
            self.source = track.makeSource()
        }
    }

    /// One ``activateOutput`` call in flight on `activationQueue`.
    final class OutputActivation {
        private let done = DispatchGroup()
        /// Written before `done` is left.
        private var activated = true
        /// When `activate` returned (``StartupTiming/now()``). Written before `done` is left.
        private(set) var activatedAt: TimeInterval?

        init(on queue: DispatchQueue, _ activate: @escaping () -> Bool) {
            done.enter()
            queue.async { [self] in
                activated = activate()
                activatedAt = StartupTiming.now()
                done.leave()
            }
        }

        /// Whether the output was readied, once it's done.
        func wait() -> Bool {
            done.wait()
            return activated
        }
    }
}
