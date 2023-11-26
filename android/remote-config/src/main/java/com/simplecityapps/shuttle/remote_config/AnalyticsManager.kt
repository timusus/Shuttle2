package com.simplecityapps.shuttle.remote_config

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AnalyticsManager @Inject constructor(@ApplicationContext val context: Context) {

    fun enableAnalytics(enabled: Boolean) {
        FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(enabled)
    }
}
