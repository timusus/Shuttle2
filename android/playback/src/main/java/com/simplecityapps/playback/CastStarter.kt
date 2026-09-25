package com.simplecityapps.playback

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches Cast to [appPlayer] once the app first comes to the foreground: on a main-thread message of its own, posted
 * when the first activity starts, so it's never part of the application's or the activity's start. A process started
 * only in the background (a library scan, a widget update, Android Auto) never sets Cast up. A Cast session left
 * running resumes once it's attached.
 */
@Singleton
class CastStarter
@Inject
constructor(
    private val appPlayer: AppPlayer
) {
    fun startInForeground(application: Application) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    application.unregisterActivityLifecycleCallbacks(this)
                    Handler(Looper.getMainLooper()).post(appPlayer::attachCast)
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
}
