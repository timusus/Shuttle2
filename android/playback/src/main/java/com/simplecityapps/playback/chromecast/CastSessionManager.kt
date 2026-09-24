package com.simplecityapps.playback.chromecast

import android.content.Context
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import javax.inject.Inject
import timber.log.Timber

/**
 * Keeps the local [HttpServer] a Cast receiver streams from running while a Cast session is up.
 *
 * Playback stays on the local player during a session for now: handing the queue to a Cast player comes back with
 * the Media3 Cast player (#345).
 */
class CastSessionManager
@Inject
constructor(
    applicationContext: Context,
    private val httpServer: HttpServer
) : SessionManagerListener<CastSession> {
    var isAvailable: Boolean = false
        private set

    init {
        try {
            val sessionManager = CastContext.getSharedInstance(applicationContext).sessionManager
            sessionManager.addSessionManagerListener(this, CastSession::class.java)
            isAvailable = true
        } catch (e: Exception) {
            // Cast framework unavailable on this device (e.g., no Google Play Services)
            Timber.w(e, "Failed to initialize Cast framework - Chromecast will be unavailable")
        }
    }

    override fun onSessionStarting(castSession: CastSession) {
        Timber.d("onSessionStarting")
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
}
