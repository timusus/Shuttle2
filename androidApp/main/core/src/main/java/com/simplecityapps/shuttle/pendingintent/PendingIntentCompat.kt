package com.simplecityapps.shuttle.pendingintent

import android.app.PendingIntent
import android.os.Build

object PendingIntentCompat {

    val FLAG_IMMUTABLE = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_IMMUTABLE
    } else {
        0
    }
}
