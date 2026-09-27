// Copied from Shuttle Podcasts (podcasts@9ee6e0954) mobile/ios/Playback/Sources/Playback/Streaming/ReadAheadControl.swift — see ios/Playback/README.md.
import Foundation

/// **The one thing a byte tee may ask of the player: fetch a little further ahead.**
///
/// The player's fetch stops at a ceiling measured from the decoder's read position
/// (``ReadAheadPolicy``). A scanner working at its frontier sometimes needs bytes past that
/// ceiling, and "appetite" is how it says so: while open, the ceiling is the wider
/// `appetiteWindowSeconds`; closed, the ordinary one. That is the entire upward channel. It cannot
/// make the player read, seek, or reopen — a dead or wrong scanner can at most fetch more than
/// was needed, never change what is heard.
///
/// `decoderBytesConsumed` is the playhead in bytes, exactly: what the decoder has read IS where the
/// listener is, give or take the seconds of read-ahead the schedule budget allows. The tee reads it
/// to set the ring's horizon and the read-ahead base, so it is offered here rather than through a
/// closure the player would have to remember to wire.
///
/// Defined by the player layer; implemented by ``StreamingPCMReader``; handed to the tee through
/// ``AudioByteTee/byteSourceDidStart(readAhead:)`` once the fetch exists.
public protocol ReadAheadControl: AnyObject {
    func openAppetite()
    func closeAppetite()
    var decoderBytesConsumed: Int64 { get }
}
