package com.simplecityapps.shuttle.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * The file androidx.preference's `PreferenceManager.getDefaultSharedPreferences` opens, named the same way, so
 * settings the legacy preference screens wrote carry over without a migration.
 */
fun Context.defaultSharedPreferences(): SharedPreferences = getSharedPreferences("${packageName}_preferences", Context.MODE_PRIVATE)
