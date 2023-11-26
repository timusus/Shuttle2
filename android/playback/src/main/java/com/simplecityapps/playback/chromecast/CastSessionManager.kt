package com.simplecityapps.playback.chromecast

import android.content.Context
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.exoplayer.ExoPlayerPlayback
import javax.inject.Inject
import timber.log.Timber

class CastSessionManager
@Inject
constructor(
    private val playbackManager: PlaybackManager,
    private val applicationContext: Context,
    private val httpServer: HttpServer,
    private val exoPlayerPlayback: ExoPlayerPlayback,
    private val mediaInfoProvider: MediaInfoProvider
) : SessionManagerListener<CastSession> {
    init {
        val sessionManager = CastContext.getSharedInstance(applicationContext).sessionManager
        sessionManager.addSessionManagerListener(this, CastSession::class.java)
    }

    override fun onSessionStarting(castSession: CastSession) {
        Timber.d("onSessionStarting")
        httpServer.start()
    }

    override fun onSessionStarted(
        castSession: CastSession,
        s: String
    ) {
        Timber.d("onSessionStarted")

        val playback = CastPlayback(applicationContext, castSession, mediaInfoProvider)
        playbackManager.switchToPlayback(playback)
    }

    override fun onSessionStartFailed(
        castSession: CastSession,
        i: Int
    ) {
        Timber.e("onSessionStartFailed")
    }

    override fun onSessionResuming(
        castSession: CastSession,
        s: String
    ) {
        Timber.d("onSessionResuming")
        if (!httpServer.wasStarted()) {
            httpServer.start()
        }
    }

    override fun onSessionResumed(
        castSession: CastSession,
        b: Boolean
    ) {
        Timber.d("onSessionResumed")

        // If we're not already playing via CastPlayback, switch
        if (playbackManager.getPlayback() !is CastPlayback) {
            val playback = CastPlayback(applicationContext, castSession, mediaInfoProvider)
            playbackManager.switchToPlayback(playback)
        }
    }

    override fun onSessionResumeFailed(
        castSession: CastSession,
        i: Int
    ) {
        Timber.e("onSessionResumeFailed ($i)")
    }

    override fun onSessionSuspended(
        castSession: CastSession,
        i: Int
    ) {
        Timber.d("onSessionSuspended ($i)")
        httpServer.stop()
    }

    override fun onSessionEnding(castSession: CastSession) {
        Timber.d("onSessionEnding() playbackState: ${playbackManager.playbackState()}")

        if (playbackManager.getPlayback() is CastPlayback) {
            // This is our final chance to update the underlying stream position In onSessionEnded(), the underlying CastPlayback#mRemoteMediaClient
            // is disconnected and hence we update our local value of stream position to the latest position.
            playbackManager.getPlayback().updateLastKnownStreamPosition()

            playbackManager.switchToPlayback(exoPlayerPlayback)
        }
    }

    override fun onSessionEnded(
        castSession: CastSession,
        i: Int
    ) {
        Timber.d("onSessionEnded")
        httpServer.stop()
    }
}
