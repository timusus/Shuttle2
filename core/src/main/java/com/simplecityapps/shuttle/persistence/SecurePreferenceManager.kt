package com.simplecityapps.shuttle.persistence

import android.content.SharedPreferences

class SecurePreferenceManager(private val sharedPreferences: SharedPreferences) {

    var embyUserName: String?
        set(value) {
            sharedPreferences.put("emby_username", value)
        }
        get() {
            return sharedPreferences.getString("emby_username", null)
        }

    var embyPass: String?
        set(value) {
            sharedPreferences.put("emby_pass", value)
        }
        get() {
            return sharedPreferences.getString("emby_pass", null)
        }

    var embyAccessToken: String?
        set(value) {
            sharedPreferences.put("emby_access_token", value)
        }
        get() {
            return sharedPreferences.getString("emby_access_token", null)
        }

    var embyUserId: String?
        set(value) {
            sharedPreferences.put("emby_user_id", value)
        }
        get() {
            return sharedPreferences.getString("emby_user_id", null)
        }

    var embyHost: String?
        set(value) {
            sharedPreferences.put("emby_host", value)
        }
        get() {
            return sharedPreferences.getString("emby_host", null)
        }

    var embyPort: Int?
        set(value) {
            sharedPreferences.put("emby_port", value)
        }
        get() {
            val port = sharedPreferences.getInt("emby_port", -1)
            return if (port != -1) port else null
        }
}