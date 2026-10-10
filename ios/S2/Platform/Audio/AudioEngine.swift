import Foundation

/// A track as `AudioEngine` takes it: the Kotlin `IosAudioTrack`, its URL parsed.
struct EngineTrack: Equatable {
    let id: String
    let url: URL
    let headers: [String: String]
    /// ReplayGain in dB, resolved by Kotlin; 0 is unity.
    let gainDb: Float
    /// The library's duration of the stream (ms), nil if unknown: when to open the track after it.
    let expectedDurationMs: Int64?
    /// The library's bitrate and file size, nil if unknown: how far a stream reads ahead on mobile data.
    let bitrateKbps: Int?
    let sizeBytes: Int64?
}

/// `MusicPlaybackController.State`.
enum EngineState: Equatable {
    case idle, loading, playing, paused, ended
}

/// What the engine reports, on the main queue, in the order it happened.
enum EngineEvent: Equatable {
    /// The state changed while `trackId` was the current track (nil: nothing is loaded), after the engine had taken
    /// `commands` commands (loads, plays, pauses and stops, counted from its first).
    case state(EngineState, trackId: String?, commands: Int)
    /// The next track became current: its first frame is being heard.
    case transition(trackId: String)
    /// The track couldn't be opened or decoded; the engine carries on as if it had ended.
    case failed(trackId: String, message: String)
    /// A position tick while playing, and once on pausing.
    case position(trackId: String, ms: Int64)
    /// The track's stream can't be sought to `ms` (a progressive transcode): it plays on, and Kotlin
    /// re-opens the stream at the position.
    case seekUnsupported(trackId: String, ms: Int64)
    /// With pause at the end on, the track's last frame was heard and the engine paused there, its next still next.
    case pausedAtEnd(trackId: String)
}

/// The S2Playback engine as `EngineAudioPlayer` drives it, in app types, so the adapter's tests can
/// stand in a fake without linking S2Playback. `MusicPlaybackController` conforms
/// (`MusicPlaybackController+AudioEngine.swift`). Callable from any thread.
protocol AudioEngine: AnyObject {
    /// Where the engine reports, on the main queue; nil stops reporting.
    func setEventHandler(_ handler: ((EngineEvent) -> Void)?)
    /// Readies the output for a play (the audio session), called off the main thread as a play or a load that plays
    /// is made, while the track opens; false refuses the play, which stays paused and is reported paused.
    var activateOutput: (() -> Bool)? { get set }
    /// A play was asked for (`trigger` names who asked, one token), before the queue is built: the engine times its
    /// next start from here as well as from its own load or play.
    func notePlayRequest(_ trigger: String)
    func load(current: EngineTrack, next: EngineTrack?, startMs: Int64, playWhenReady: Bool)
    func setNext(_ track: EngineTrack?)
    func play()
    func pause()
    func seek(toMs ms: Int64)
    func stop()
    func setSpeed(_ speed: Float)
    /// Pause on the current track's last frame rather than carry on into the next (the sleep timer's "end of song").
    func setPauseAtEnd(_ enabled: Bool)
    /// The uid and position (ms) of what is being heard.
    var position: (uid: String, ms: Int64)? { get }
    /// The duration (ms) of the track being heard, nil until known.
    var durationMs: Int64? { get }
    /// The rate (Hz) the engine renders and filters at, fixed for its life: every track is resampled to it.
    var outputSampleRate: Double { get }
    /// The equalizer, from the next chunk rendered: a preamp (dB) and five biquad coefficients per band
    /// (b0, b1, b2, a1, a2), designed at `outputSampleRate`.
    func setEqualizer(enabled: Bool, preampDb: Float, coefficients: [Double])
}
