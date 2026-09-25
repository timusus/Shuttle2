package com.simplecityapps.playback.chromecast

import android.content.Context
import androidx.tracing.trace
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import javax.inject.Inject
import timber.log.Timber

/**
 * Whether Cast is available, and keeps the local [HttpServer] a Cast receiver streams from running while a Cast
 * session is up, with a new key for its URLs each session (a resumed one keeps its key). Moving playback to and from the receiver is the Cast player's (see [CastQueue]).
 */
class CastSessionManager
@Inject
constructor(
    applicationContext: Context,
    private val httpServer: HttpServer,
    private val streams: CastStreams
) : SessionManagerListener<CastSession> {
    var isAvailable: Boolean = false
        private set

    init {
        trace("S2 Cast init") {
            try {
                val sessionManager = CastContext.getSharedInstance(applicationContext).sessionManager
                sessionManager.addSessionManagerListener(this, CastSession::class.java)
                isAvailable = true
            } catch (e: Exception) {
                // Cast framework unavailable on this device (e.g., no Google Play Services)
                Timber.w(e, "Failed to initialize Cast framework - Chromecast will be unavailable")
            }
        }
    }

    override fun onSessionStarting(castSession: CastSession) {
        Timber.d("onSessionStarting")
        streams.newSession()
        startHttpServer()
    }

    override fun onSessionStarted(
        castSession: CastSession,
        s: String
    ) {
        Timber.d("onSessionStarted")
    }

    override fun onSessionStartFailed(
        castSession: CastSession,
        i: Int
    ) {
        Timber.e("onSessionStartFailed")
        httpServer.stop()
    }

    override fun onSessionResuming(
        castSession: CastSession,
        s: String
    ) {
        Timber.d("onSessionResuming")
        startHttpServer()
    }

    override fun onSessionResumed(
        castSession: CastSession,
        b: Boolean
    ) {
        Timber.d("onSessionResumed")
    }

    override fun onSessionResumeFailed(
        castSession: CastSession,
        i: Int
    ) {
        Timber.e("onSessionResumeFailed ($i)")
        httpServer.stop()
    }

    override fun onSessionSuspended(
        castSession: CastSession,
        i: Int
    ) {
        Timber.d("onSessionSuspended ($i)")
        httpServer.stop()
    }

    override fun onSessionEnding(castSession: CastSession) {
        Timber.d("onSessionEnding")
    }

    override fun onSessionEnded(
        castSession: CastSession,
        i: Int
    ) {
        Timber.d("onSessionEnded")
        httpServer.stop()
    }

    private fun startHttpServer() {
        if (!httpServer.isAlive) {
            httpServer.start()
        }
    }

    companion object {
        /**
         * Whether the Cast receiver has gone idle because its item played to the end, rather than being stopped,
         * interrupted or failing: Media3's Cast player reports all of those as idle.
         */
        fun receiverPlayedOut(context: Context): Boolean = try {
            CastContext.getSharedInstance(context).sessionManager.currentCastSession?.remoteMediaClient?.mediaStatus?.idleReason ==
                MediaStatus.IDLE_REASON_FINISHED
        } catch (e: Exception) {
            false
        }
    }
}
