package com.simplecityapps.shuttle.settings

import android.content.Context
import android.content.SharedPreferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** Binds [Setting]s to the app's default SharedPreferences, the file the settings screens have always written. */
@SingleIn(AppScope::class)
class SettingsStore @Inject constructor(
    private val sharedPreferences: SharedPreferences
) {
    fun <T> preference(setting: Setting<T>): Preference<T> = sharedPreferences.preference(setting)
}

/**
 * The file androidx.preference's `PreferenceManager.getDefaultSharedPreferences` opens, named the same way, so
 * settings the legacy preference screens wrote carry over without a migration.
 */
fun Context.defaultSharedPreferences(): SharedPreferences = getSharedPreferences("${packageName}_preferences", Context.MODE_PRIVATE)
