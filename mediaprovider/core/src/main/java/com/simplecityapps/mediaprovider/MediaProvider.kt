package com.simplecityapps.mediaprovider

import androidx.annotation.DrawableRes
import com.simplecityapps.mediaprovider.model.Song

interface MediaProvider {

    enum class Type(val isRemote: Boolean) {
        Shuttle(false), MediaStore(false), Emby(true), Jellyfin(true);

        companion object {
            fun init(ordinal: Int): Type {
                return when (ordinal) {
                    Shuttle.ordinal -> Shuttle
                    MediaStore.ordinal -> MediaStore
                    Emby.ordinal -> Emby
                    Jellyfin.ordinal -> Jellyfin
                    else -> Shuttle
                }
            }
        }

        fun title(): String {
            return when (this) {
                Shuttle -> "Shuttle"
                MediaStore -> "Android Media Store"
                Jellyfin -> "Jellyfin"
                Emby -> "Emby"
            }
        }

        fun description(): String {
            return when (this) {
                Shuttle -> "Scans selected folders & files"
                MediaStore -> "Android-managed database"
                Jellyfin -> "Open source personal media server"
                Emby -> "Personal media server"
            }
        }

        @DrawableRes
        fun iconResId(): Int {
            return when (this) {
                Shuttle -> R.drawable.ic_launcher_foreground_blue
                MediaStore -> R.drawable.ic_baseline_android_24
                Jellyfin -> R.drawable.ic_jellyfin
                Emby -> R.drawable.ic_emby
            }
        }
    }

    val type: Type

    suspend fun findSongs(callback: ((song: Song, progress: Int, total: Int) -> (Unit))? = null): List<Song>?
}