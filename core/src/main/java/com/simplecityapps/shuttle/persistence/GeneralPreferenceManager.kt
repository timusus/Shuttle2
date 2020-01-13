package com.simplecityapps.shuttle.persistence

import android.content.SharedPreferences
import com.simplecityapps.core.BuildConfig

class GeneralPreferenceManager(private val sharedPreferences: SharedPreferences) {

    var previousVersionCode: Int
        set(value) {
            sharedPreferences.put("previous_version_code", value)
        }
        get() {
            return sharedPreferences.get("previous_version_code", BuildConfig.VERSION_CODE)
        }

    var hasSeenChangelog: Boolean
        set(value) {
            sharedPreferences.put("changelog_viewed", value)
        }
        get() {
            return sharedPreferences.get("changelog_viewed", false)
        }

    var showChangelogOnLaunch: Boolean
        set(value) {
            sharedPreferences.put("changelog_show_on_launch", value)
        }
        get() {
            return sharedPreferences.get("changelog_show_on_launch", true)
        }

    var nightMode: String
        set(value) {
            sharedPreferences.put("pref_night_mode", value)
        }
        get() {
            return sharedPreferences.get("pref_night_mode", "0")
        }
}