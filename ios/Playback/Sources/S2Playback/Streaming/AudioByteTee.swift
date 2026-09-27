// Copied from Shuttle Podcasts (podcasts@9ee6e0954) mobile/ios/Playback/Sources/Playback/Streaming/AudioByteTee.swift — see ios/Playback/README.md.
import Foundation

/// Raw enclosure bytes as fetched, in offset order, for the ad-skip spine.
///
/// The precision argument for iOS ad-skip is that the bytes the scanner looked at are the bytes the
/// listener heard, and the only way to hold that is for one fetch to feed both. ``HTTPRangeByteSource``
/// is that fetch: the decoder pulls through ``StreamByteReader``, and the same bytes leave here with
/// the absolute offset they start at. Nothing on this side may read back — a tee is a write path, so
/// no implementation of it can ever become a second byte source for playback.
///
/// Ordering is the contract: within a transaction the offsets are contiguous and increasing. A gap
/// or a jump only ever appears as a new transaction, announced first.
public protocol AudioByteTee: AnyObject {

    /// The fetch this tee is attached to exists. Handed over once per media load, before the first
    /// transaction opens, so the tee may hold it (weakly: the fetch holds the tee) for appetite.
    func byteSourceDidStart(readAhead: any ReadAheadControl)

    /// The player is about to move the decoder to `ms`. Announced BEFORE the decoder is told, so
    /// the transaction the seek opens can carry the target it was opened to play from — the only
    /// moment the target is known with certainty. `generation` is the same seek counter the
    /// transaction will carry in `seekGeneration`, so a tee can pair the two and refuse to anchor
    /// a body a later seek has already overtaken. A re-anchoring the player does for its own
    /// reasons (an engine restart, a stall recovery) announces the position it is already at.
    func playerWillSeek(toMs ms: Int64, generation: Int)

    /// A response body has been accepted and its bytes are about to start arriving.
    ///
    /// `isContinuation` is true when the stream is being picked up where it stopped — a dropped
    /// connection, a bounded `Range` that ran out, a retried request — and false when the offset is
    /// somewhere the listener has moved to (the first open, or a seek). Only the false case says
    /// anything about where the listener is, so only it may be paired with a position.
    ///
    /// `seekGeneration` is the player's seek counter as it stood when this body was requested. Two
    /// seeks in quick succession each announce a target before the transaction they will open
    /// exists, so it is the only way a listener on this protocol can tell which target this body
    /// was opened for — and refuse to anchor it to one a later seek has already replaced.
    func byteSourceDidOpenTransaction(
        startByte: Int64,
        totalBytes: Int64?,
        isContinuation: Bool,
        seekGeneration: Int
    )

    /// One chunk exactly as it arrived, at its absolute offset in the resource.
    func byteSource(didReceive bytes: Data, at offset: Int64)

    /// The body ended — completed, cancelled or failed. `endedAtByte` is one past the last byte.
    func byteSourceDidCloseTransaction(endedAtByte: Int64)

    /// The resource's total size, learned from the first response that carried it.
    func byteSource(didLearnTotalBytes: Int64)
}
