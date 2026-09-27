package com.simplecityapps.mediaprovider.server

import android.content.Context
import android.content.pm.ApplicationInfo

/**
 * Whether the app is a debuggable (debug) build. Multiplatform library modules have no `BuildConfig`, so the providers
 * read the flag the app was built with instead.
 */
fun Context.isDebuggable(): Boolean = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
