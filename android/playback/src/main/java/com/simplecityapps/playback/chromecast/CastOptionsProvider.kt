package com.simplecityapps.playback.chromecast

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.simplecityapps.playback.R

@Suppress("unused")
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions = CastOptions.Builder()
        .setReceiverApplicationId(context.getString(R.string.cast_app_id))
        // S2's own media session and notification follow the Cast player; the Cast SDK's own would duplicate them.
        .setCastMediaOptions(CastMediaOptions.Builder().setMediaSessionEnabled(false).setNotificationOptions(null).build())
        .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
