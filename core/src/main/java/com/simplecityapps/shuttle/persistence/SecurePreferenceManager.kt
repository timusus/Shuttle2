package com.simplecityapps.shuttle.persistence

import android.content.SharedPreferences

class SecurePreferenceManager(private val sharedPreferences: SharedPreferences) {

    // Emby
    
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

    
    // Jellyfin

    var jellyfinUserName: String?
        set(value) {
            sharedPreferences.put("jellyfin_username", value)
        }
        get() {
            return sharedPreferences.getString("jellyfin_username", null)
        }

    var jellyfinPass: String?
        set(value) {
            sharedPreferences.put("jellyfin_pass", value)
        }
        get() {
            return sharedPreferences.getString("jellyfin_pass", null)
        }

    var jellyfinAccessToken: String?
        set(value) {
            sharedPreferences.put("jellyfin_access_token", value)
        }
        get() {
            return sharedPreferences.getString("jellyfin_access_token", null)
        }

    var jellyfinUserId: String?
        set(value) {
            sharedPreferences.put("jellyfin_user_id", value)
        }
        get() {
            return sharedPreferences.getString("jellyfin_user_id", null)
        }

    var jellyfinHost: String?
        set(value) {
            sharedPreferences.put("jellyfin_host", value)
        }
        get() {
            return sharedPreferences.getString("jellyfin_host", null)
        }

    var jellyfinPort: Int?
        set(value) {
            sharedPreferences.put("jellyfin_port", value)
        }
        get() {
            val port = sharedPreferences.getInt("jellyfin_port", -1)
            return if (port != -1) port else null
        }


    // Plex

    var plexUserName: String?
        set(value) {
            sharedPreferences.put("plex_username", value)
        }
        get() {
            return sharedPreferences.getString("plex_username", null)
        }

    var plexPass: String?
        set(value) {
            sharedPreferences.put("plex_pass", value)
        }
        get() {
            return sharedPreferences.getString("plex_pass", null)
        }

    var plexAccessToken: String?
        set(value) {
            sharedPreferences.put("plex_access_token", value)
        }
        get() {
            return sharedPreferences.getString("plex_access_token", null)
        }

    var plexUserId: String?
        set(value) {
            sharedPreferences.put("plex_user_id", value)
        }
        get() {
            return sharedPreferences.getString("plex_user_id", null)
        }

    var plexHost: String?
        set(value) {
            sharedPreferences.put("plex_host", value)
        }
        get() {
            return sharedPreferences.getString("plex_host", null)
        }

    var plexPort: Int?
        set(value) {
            sharedPreferences.put("plex_port", value)
        }
        get() {
            val port = sharedPreferences.getInt("plex_port", -1)
            return if (port != -1) port else null
        }
}