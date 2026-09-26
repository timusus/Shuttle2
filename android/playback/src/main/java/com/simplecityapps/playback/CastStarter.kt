package com.simplecityapps.playback

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches Cast to [appPlayer] once the app first comes to the foreground ([startInForeground]), or once the playback
 * service starts in a process with no activity, for Android Auto, a media button or a widget ([startForSession]):
 * whichever comes first, on a main-thread message of its own, so it's never part of the application's, the
 * activity's or the service's start. A process started only in the background for anything else (a library scan, a
 * widget update) never sets Cast up. A Cast session left running resumes once it's attached.
 */
@Singleton
class CastStarter
@Inject
constructor(
    private val appPlayer: AppPlayer
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Attaches Cast once the first activity starts. */
    fun startInForeground(application: Application) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    application.unregisterActivityLifecycleCallbacks(this)
                    attachSoon()
                }

                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?
                ) {}

                override fun onActivityResumed(activity: Activity) {}

                override fun onActivityPaused(activity: Activity) {}

                override fun onActivityStopped(activity: Activity) {}

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle
                ) {}

                override fun onActivityDestroyed(activity: Activity) {}
            }
        )
    }

    /** Attaches Cast for the playback service's session, which may have started with no activity. */
    fun startForSession() {
        attachSoon()
    }

    /** Attaches Cast on a main-thread message of its own. Attaching more than once changes nothing. */
    private fun attachSoon() {
        mainHandler.post(appPlayer::attachCast)
    }
}
