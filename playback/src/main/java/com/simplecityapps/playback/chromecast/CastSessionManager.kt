package com.simplecityapps.playback.chromecast

import android.content.Context
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.exoplayer.ExoPlayerPlayback
import timber.log.Timber
import javax.inject.Inject

class CastSessionManager @Inject constructor(
    private val playbackManager: PlaybackManager,
    private val applicationContext: Context,
    private val httpServer: HttpServer,
    private val exoPlayerPlayback: ExoPlayerPlayback
) : SessionManagerListener<CastSession> {

    init {
        val sessionManager = CastContext.getSharedInstance(applicationContext).sessionManager
        sessionManager.addSessionManagerListener(this, CastSession::class.java)
    }

    override fun onSessionStarting(castSession: CastSession) {
        Timber.d("onSessionStarting")
    }

    override fun onSessionStarted(castSession: CastSession, s: String) {
        Timber.d("onSessionStarted")

        val playback = CastPlayback(applicationContext, castSession, httpServer)
        playbackManager.switchToPlayback(playback)
    }

    override fun onSessionStartFailed(castSession: CastSession, i: Int) {
        Timber.e("onSessionStartFailed")
    }

    override fun onSessionEnding(castSession: CastSession) {
        Timber.d("onSessionEnding() isPlaying: ${playbackManager.isPlaying()}")

        if (playbackManager.getPlayback() is CastPlayback) {

            // This is our final chance to update the underlying stream position In onSessionEnded(), the underlying CastPlayback#mRemoteMediaClient
            // is disconnected and hence we update our local value of stream position to the latest position.
            playbackManager.getPlayback().updateLastKnownStreamPosition()

            playbackManager.switchToPlayback(exoPlayerPlayback)
        }
    }

    override fun onSessionEnded(castSession: CastSession, i: Int) {
        Timber.d("onSessionEnded")
    }

    override fun onSessionResuming(castSession: CastSession, s: String) {
        Timber.d("onSessionResuming")
    }

    override fun onSessionResumed(castSession: CastSession, b: Boolean) {
        Timber.d("onSessionResumed")

        // If we're not already playing via CastPlayback, switch
        if (!(playbackManager.getPlayback() is CastPlayback)) {
            val playback = CastPlayback(applicationContext, castSession, httpServer)
            playbackManager.switchToPlayback(playback)
        }
    }

    override fun onSessionResumeFailed(castSession: CastSession, i: Int) {
        Timber.e("onSessionResumeFailed ($i)")
    }

    override fun onSessionSuspended(castSession: CastSession, i: Int) {
        Timber.d("onSessionSuspended ($i)")
    }
}