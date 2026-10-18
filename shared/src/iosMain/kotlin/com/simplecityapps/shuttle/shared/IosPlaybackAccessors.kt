package com.simplecityapps.shuttle.shared

import com.simplecityapps.shuttle.playback.RecordPlays
import com.simplecityapps.shuttle.playback.RecordResumePoints
import com.simplecityapps.shuttle.playbackreporting.PlaybackReporting
import com.simplecityapps.shuttle.scrobbling.PlaybackScrobbling
import com.simplecityapps.shuttle.scrobbling.flush.InProcessScrobbleFlushScheduler
import com.simplecityapps.shuttle.shared.playback.IosPlayerController

/** The player and what follows its events; part of [IosAppGraph]. */
interface IosPlaybackAccessors {
    /** Playback: `PlaybackOperations`, and the queue through its `queueOperations`. One for the graph, on main. */
    val playerController: IosPlayerController

    /** Records each song's plays and pause positions from [playerController]'s events; Swift starts it once, at launch. */
    val recordPlays: RecordPlays

    /** Keeps where each play context was left (#670) from [playerController]'s events; Swift starts it once, at launch. */
    val recordResumePoints: RecordResumePoints

    /** Scrobbles to Last.fm from [playerController]'s events (#503); Swift starts it once, at launch. */
    val playbackScrobbling: PlaybackScrobbling

    /** Sends the queued scrobbles: after each one is queued, and from Swift on foreground and in a `BGAppRefreshTask`. */
    val scrobbleFlushScheduler: InProcessScrobbleFlushScheduler

    /** Reports plays to Jellyfin, Emby and Plex (and replays missed ones); Swift starts it at launch. */
    val playbackReporting: PlaybackReporting
}
