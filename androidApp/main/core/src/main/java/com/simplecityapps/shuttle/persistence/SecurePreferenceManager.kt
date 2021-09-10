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

    var embyAddress: String?
        set(value) {
            sharedPreferences.put("emby_address", value)
        }
        get() {
            return sharedPreferences.getString("emby_address", null)
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

    var jellyfinAddress: String?
        set(value) {
            sharedPreferences.put("jellyfin_address", value)
        }
        get() {
            return sharedPreferences.getString("jellyfin_address", null)
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

    var plexAddress: String?
        set(value) {
            sharedPreferences.put("plex_host", value)
        }
        get() {
            return sharedPreferences.getString("plex_host", null)
        }
}