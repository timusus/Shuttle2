package com.simplecityapps.shuttle.settings

import android.content.Context
import android.content.SharedPreferences
import com.simplecityapps.shuttle.persistence.SharedPreferencesKeyValueStore

/**
 * The file androidx.preference's `PreferenceManager.getDefaultSharedPreferences` opens, named the same way, so
 * settings the legacy preference screens wrote carry over without a migration.
 */
fun Context.defaultSharedPreferences(): SharedPreferences = getSharedPreferences("${packageName}_preferences", Context.MODE_PRIVATE)

/**
 * A [SettingsStore] over [sharedPreferences]. Kept for the server providers' tests, which build one over their
 * FakeSharedPreferences; new code passes a KeyValueStore.
 */
fun SettingsStore(sharedPreferences: SharedPreferences): SettingsStore = SettingsStore(SharedPreferencesKeyValueStore(sharedPreferences))
