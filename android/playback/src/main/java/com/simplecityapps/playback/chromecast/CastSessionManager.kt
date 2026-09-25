package com.simplecityapps.playback.chromecast

import android.content.Context
import androidx.media3.cast.Cast
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import javax.inject.Inject
import timber.log.Timber

/**
 * Whether Cast is available, and keeps the local [HttpServer] a Cast receiver streams from running while a Cast
 * session is up, with a new key for its URLs each session (a resumed one keeps its key). Moving playback to and from the receiver is the Cast player's (see [CastQueue]).
 *
 * Follows sessions once [start]ed, which the Cast player's setup does (see [com.simplecityapps.playback.AppPlayer]).
 * Media3's [Cast] loads Cast's context off the main thread, and holds the listener until it's loaded.
 */
class CastSessionManager
@Inject
constructor(
    private val applicationContext: Context,
    private val httpServer: HttpServer,
    private val streams: CastStreams
) : SessionManagerListener<CastSession> {
    /**
     * Whether Cast can run here: it needs Google Play services, and Cast's context mustn't have failed to load. Read
     * before Cast is set up, so it asks Play services only whether it's there.
     */
    val isAvailable: Boolean
        get() = hasPlayServices && Cast.getSingletonInstance(applicationContext).castContextLoadFailure == null

    private val hasPlayServices: Boolean by lazy {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(applicationContext) == ConnectionResult.SUCCESS
    }

    private var started = false

    /** Follows Cast sessions from now on; false, doing nothing, when Cast isn't available. */
    fun start(): Boolean {
        if (!isAvailable) {
            Timber.w("Cast is unavailable on this device")
            return false
        }
        if (!started) {
            started = true
            Cast.getSingletonInstance(applicationContext).addSessionManagerListener(this)
        }
        return true
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
            Cast.getSingletonInstance(context).currentCastSession?.remoteMediaClient?.mediaStatus?.idleReason ==
                MediaStatus.IDLE_REASON_FINISHED
        } catch (e: Exception) {
            false
        }
    }
}
